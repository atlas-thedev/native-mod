package xyz.nativelaunch.voice;

import xyz.nativelaunch.core.Log;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/**
 * UDP link to the Native voice relay. Packets: [0x4E][type][sid][seq][AES-128-GCM(payload)+tag], nonce sid|seq|dir.
 * One socket serves every room the player is in; the sid in the header says which room (and key) a packet is for.
 */
final class VoiceNet implements Runnable {
	static final int T_AUDIO = 1, T_PING = 2, T_BYE = 3;
	private static final int HEADER = 10, TAG = 16;

	final InetSocketAddress server;
	private final DatagramSocket socket;
	private final Cipher enc, dec;
	private volatile boolean running = true;
	private final Thread thread;

	VoiceNet(String host, int port) throws Exception {
		server = new InetSocketAddress(host, port);
		if (server.isUnresolved()) {
			throw new java.net.UnknownHostException(host);
		}
		socket = new DatagramSocket();
		socket.setSoTimeout(500);
		socket.setReceiveBufferSize(1 << 18);
		enc = Cipher.getInstance("AES/GCM/NoPadding");
		dec = Cipher.getInstance("AES/GCM/NoPadding");
		thread = new Thread(this, "Native-Voice-Net");
		thread.setDaemon(true);
		thread.start();
	}

	boolean sameServer(String host, int port) {
		return server.getHostString().equals(host) && server.getPort() == port;
	}

	private static byte[] nonce(int sid, int seq, int dir) {
		return ByteBuffer.allocate(12).putInt(sid).putInt(seq).putInt(dir).array();
	}

	void send(Voice.Room r, int type, byte[] plain, int len) {
		if (!running) {
			return;
		}
		try {
			int seq = r.seq.incrementAndGet();
			byte[] out = new byte[HEADER + len + TAG];
			ByteBuffer.wrap(out).put((byte) 0x4E).put((byte) type).putInt(r.sid).putInt(seq);
			synchronized (enc) {
				enc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(r.secret, "AES"), new GCMParameterSpec(128, nonce(r.sid, seq, 0)));
				enc.updateAAD(out, 0, HEADER);
				enc.doFinal(plain, 0, len, out, HEADER);
			}
			socket.send(new DatagramPacket(out, out.length, server));
		} catch (Throwable t) {
			Log.debug("Voice send failed: {}", t.toString());
		}
	}

	@Override
	public void run() {
		byte[] buf = new byte[2048];
		DatagramPacket p = new DatagramPacket(buf, buf.length);
		while (running) {
			try {
				p.setLength(buf.length);
				socket.receive(p);
				int n = p.getLength();
				if (n < HEADER + TAG || buf[0] != 0x4E) {
					continue;
				}
				ByteBuffer h = ByteBuffer.wrap(buf, 0, HEADER);
				h.get();
				int type = h.get(), sid = h.getInt(), seq = h.getInt();
				Voice.Room r = Voice.roomBySid(sid);
				if (r == null) {
					continue;
				}
				byte[] plain;
				try {
					dec.init(Cipher.DECRYPT_MODE, new SecretKeySpec(r.secret, "AES"), new GCMParameterSpec(128, nonce(sid, seq, 1)));
					dec.updateAAD(buf, 0, HEADER);
					plain = dec.doFinal(buf, HEADER, n - HEADER);
				} catch (Exception bad) {
					continue;
				}
				if (type == T_AUDIO && plain.length >= 9) {
					ByteBuffer b = ByteBuffer.wrap(plain);
					int from = b.getInt(), flags = b.get() & 0xFF, sseq = b.getInt();
					byte[] opus = new byte[plain.length - 9];
					System.arraycopy(plain, 9, opus, 0, opus.length);
					Voice.onAudio(r, from, flags, sseq, opus);
				} else if (type == T_PING && plain.length >= 8) {
					r.rtt = (int) (System.currentTimeMillis() - ByteBuffer.wrap(plain).getLong());
					r.lastPong = System.currentTimeMillis();
				}
			} catch (SocketTimeoutException idle) {
				// loop to re-check running
			} catch (Throwable t) {
				if (running) {
					Log.debug("Voice receive failed: {}", t.toString());
				}
			}
		}
	}

	void ping(Voice.Room r) {
		send(r, T_PING, ByteBuffer.allocate(8).putLong(System.currentTimeMillis()).array(), 8);
	}

	void close() {
		running = false;
		socket.close();
	}
}
