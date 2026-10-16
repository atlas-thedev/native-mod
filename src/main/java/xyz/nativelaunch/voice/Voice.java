package xyz.nativelaunch.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.McBridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native Voice: friend / group calls through Relay, plus proximity voice with everyone on the same Minecraft server
 * who runs Native (positions come from the local world, so the Minecraft server needs no plugin).
 * Main-thread API for the UI; network and audio run on their own threads.
 */
public final class Voice {
	public static final class Member {
		public final int sid;
		public final String userId, name, ign, skin;
		public final boolean muted, deafened, connected;

		Member(JsonObject o) {
			sid = (int) o.get("sid").getAsLong();
			userId = str(o, "userId");
			name = str(o, "name");
			ign = str(o, "ign");
			skin = str(o, "skin");
			muted = bool(o, "muted");
			deafened = bool(o, "deafened");
			connected = bool(o, "connected");
		}
	}

	public static final class Room {
		/** Conversation key ("dm:<friend>", "g:<group>") or "srv:<address>". */
		public final String key, room;
		final int sid;
		final byte[] secret;
		final boolean proximity;
		final AtomicInteger seq = new AtomicInteger();
		volatile List<Member> members;
		volatile int rtt = -1;
		volatile long lastPong, lastPing;
		final long joinedAt = System.currentTimeMillis();

		Room(String convKey, JsonObject o) {
			this.key = convKey;
			this.room = str(o, "room");
			this.sid = (int) o.get("sid").getAsLong();
			this.secret = java.util.Base64.getDecoder().decode(str(o, "key"));
			this.proximity = convKey.startsWith("srv:");
			this.members = Voice.members(o.getAsJsonArray("members"));
		}

		public List<Member> members() {
			return members;
		}

		public int ping() {
			return rtt;
		}
	}

	// settings, written by the Voice Chat module every frame
	public static volatile boolean pushToTalk = true, pttHeld, proximityOn = true;
	public static volatile float thresholdDb = -42, micGain = 1, outVolume = 1, range = 48;
	public static volatile boolean muted, deafened;
	/** Chosen devices by name; "" = system default. */
	public static volatile String micDevice = "", outDevice = "";
	private static volatile long micTestUntil;
	static volatile boolean selfSpeaking;
	static volatile float selfLevel;

	private static volatile Room call, prox;
	private static volatile String joining;
	private static volatile String error;
	private static volatile long errorUntil;
	private static final Map<String, List<Member>> live = new ConcurrentHashMap<String, List<Member>>();
	private static final Map<Integer, RemoteStream> streams = new ConcurrentHashMap<Integer, RemoteStream>();
	private static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Native-Voice");
		t.setDaemon(true);
		return t;
	});
	private static VoiceNet net;
	private static volatile AudioEngine audio;
	private static final boolean SVC = svcInstalled();
	private static volatile long proxRetryAt;
	private static volatile String proxFor;
	private static volatile boolean proxBusy;
	private static final double[] earPos = new double[5];
	private static volatile boolean earValid;

	private Voice() {
	}

	// ── per-player volume (config/native-voice.json, by Native user id) ──

	private static final Map<String, Float> volumes = new ConcurrentHashMap<String, Float>();
	private static volatile boolean volumesLoaded, volumesDirty;

	private static java.nio.file.Path volumesFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("native-voice.json");
	}

	private static void loadVolumes() {
		if (volumesLoaded) {
			return;
		}
		volumesLoaded = true;
		try {
			java.nio.file.Path f = volumesFile();
			if (java.nio.file.Files.isRegularFile(f)) {
				JsonObject o = new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8))
						.getAsJsonObject().getAsJsonObject("volumes");
				for (Map.Entry<String, JsonElement> e : o.entrySet()) {
					volumes.put(e.getKey(), Math.max(0f, Math.min(2f, e.getValue().getAsFloat())));
				}
			}
		} catch (Throwable t) {
			Log.debug("Voice volumes: {}", t.toString());
		}
	}

	/** Volume for one player, 0 - 2 (1 = as sent). */
	public static float volume(String userId) {
		loadVolumes();
		Float v = userId == null ? null : volumes.get(userId);
		return v == null ? 1f : v;
	}

	public static void setVolume(String userId, float v) {
		if (userId == null) {
			return;
		}
		loadVolumes();
		v = Math.max(0f, Math.min(2f, v));
		if (Math.abs(v - 1f) < 0.001f) {
			volumes.remove(userId);
		} else {
			volumes.put(userId, v);
		}
		if (!volumesDirty) {
			volumesDirty = true;
			pool.execute(() -> {
				try {
					Thread.sleep(800);
					volumesDirty = false;
					JsonObject root = new JsonObject(), o = new JsonObject();
					for (Map.Entry<String, Float> e : volumes.entrySet()) {
						o.addProperty(e.getKey(), e.getValue());
					}
					root.add("volumes", o);
					java.nio.file.Path f = volumesFile();
					java.nio.file.Files.createDirectories(f.getParent());
					java.nio.file.Files.write(f, root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
				} catch (Throwable t) {
					Log.debug("Voice volumes not saved: {}", t.toString());
				}
			});
		}
	}

	/** A member of your call or proximity room by user id, or null. */
	public static Member member(String userId) {
		for (Room r : new Room[] {call, prox}) {
			if (r != null && userId != null) {
				for (Member m : r.members) {
					if (userId.equals(m.userId)) {
						return m;
					}
				}
			}
		}
		return null;
	}

	/** Simple Voice Chat is installed: it owns proximity voice then (Native keeps calls), so nobody is heard twice. */
	public static boolean svcInstalled() {
		try {
			return FabricLoader.getInstance().isModLoaded("voicechat");
		} catch (Throwable t) {
			return false;
		}
	}

	// ── state for the UI ──────────────────────────────────────────────────

	public static Room call() {
		return call;
	}

	public static Room proximity() {
		return prox;
	}

	public static boolean inCall(String convKey) {
		Room r = call;
		return r != null && r.key.equals(convKey);
	}

	public static boolean joining(String convKey) {
		return convKey.equals(joining);
	}

	/** Members of a call in a conversation (yours or someone else's), empty when none. */
	public static List<Member> callMembers(String convKey) {
		Room r = call;
		if (r != null && r.key.equals(convKey)) {
			return r.members;
		}
		List<Member> m = live.get(convKey);
		return m == null ? Collections.<Member>emptyList() : m;
	}

	public static String error() {
		String e = error;
		if (e != null && errorUntil != 0 && System.currentTimeMillis() > errorUntil) {
			error = null; // a "declined" note fades by itself
			e = null;
		}
		return e;
	}

	/** Shows a message for a few seconds where the call status is shown. */
	private static void note(String message) {
		error = message;
		errorUntil = System.currentTimeMillis() + 10_000;
	}

	/**
	 * Why the call has no audio: the voice server never answered our pings (a firewall, a VPN or a network that blocks
	 * UDP), or null when it is fine / still connecting.
	 */
	public static String linkProblem(String convKey) {
		Room r = call;
		if (r == null || !r.key.equals(convKey)) {
			return null;
		}
		long now = System.currentTimeMillis();
		boolean silent = r.lastPong == 0 ? now - r.joinedAt > 6000 : now - r.lastPong > 8000;
		return silent ? "Can't reach the voice server. A firewall or VPN may be blocking UDP." : null;
	}

	/** Push to talk is on: hold {@link #pttKey} to be heard. */
	public static boolean pushToTalkOn() {
		return pushToTalk;
	}

	public static volatile String pttKey = "";

	public static String micError() {
		AudioEngine a = audio;
		return a == null ? null : a.micError;
	}

	public static boolean selfSpeaking() {
		return selfSpeaking;
	}

	/** True while this member's voice is coming in. */
	public static boolean speaking(Member m) {
		String me = me();
		if (me != null && me.equals(m.userId)) {
			return selfSpeaking;
		}
		RemoteStream s = streams.get(m.sid);
		return s != null && s.speaking();
	}

	public static String me() {
		RelayClient r = RelayClient.get();
		return r == null ? null : r.meId;
	}

	static boolean active() {
		return call != null || prox != null;
	}

	static boolean wantsMic() {
		return active() && !muted && !deafened || System.currentTimeMillis() < micTestUntil;
	}

	/** The mic test is on screen: keep the microphone open (and the engine running) for a moment. */
	public static void micTest() {
		micTestUntil = System.currentTimeMillis() + 600;
		if (audio == null) {
			pool.execute(() -> {
				if (audio == null) {
					audio = new AudioEngine();
					audio.start();
				}
			});
		}
	}

	/** Microphone level in dBFS (after the mic volume), -100 when silent / closed. */
	public static double micDb() {
		float l = selfLevel;
		return l <= 1e-5f ? -100 : 20 * Math.log10(l);
	}

	static Collection<RemoteStream> streams() {
		return streams.values();
	}

	static Room roomBySid(int sid) {
		Room c = call, p = prox;
		return c != null && c.sid == sid ? c : p != null && p.sid == sid ? p : null;
	}

	// ── actions ───────────────────────────────────────────────────────────

	public static void joinCall(final String convKey) {
		if (joining != null) {
			return;
		}
		incoming.remove(convKey);
		joining = convKey;
		error = null;
		pool.execute(() -> {
			try {
				Room r = join(convKey);
				Room old = call;
				call = r;
				if (old != null && old.sid != r.sid) {
					bye(old);
				}
				live.remove(convKey);
				error = null;
			} catch (Throwable t) {
				error = message(t);
			} finally {
				joining = null;
			}
		});
	}

	public static void leaveCall() {
		final Room r = call;
		call = null;
		if (r != null) {
			pool.execute(() -> bye(r));
		}
	}

	public static void toggleMute() {
		muted = !muted;
		if (!muted && deafened) {
			deafened = false;
		}
		pushState();
	}

	public static void toggleDeafen() {
		deafened = !deafened;
		pushState();
	}

	/** Leaves everything (module switched off / game closing). */
	public static void shutdown() {
		leaveCall();
		final Room p = prox;
		prox = null;
		proxFor = null;
		if (p != null) {
			pool.execute(() -> bye(p));
		}
		pool.execute(() -> {
			if (audio != null) {
				audio.stop();
				audio = null;
			}
			if (net != null) {
				net.close();
				net = null;
			}
			streams.clear();
		});
	}

	private static Room join(String convKey) throws Exception {
		RelayClient client = RelayClient.get();
		if (client == null || client.meId == null) {
			throw new IllegalStateException("Sign in to Native to use voice.");
		}
		McBridge mc = mc();
		JsonObject body = new JsonObject();
		body.addProperty("room", convKey);
		String ign = mc == null ? null : mc.username();
		if (ign != null) {
			body.addProperty("ign", ign);
		}
		String skin = xyz.nativelaunch.ui.Avatars.self(ign);
		if (skin != null) {
			body.addProperty("skin", skin);
		}
		body.addProperty("muted", muted);
		body.addProperty("deafened", deafened);
		JsonObject res = client.apiPost("/v1/voice/join", body);
		Room r = new Room(convKey, res);
		String host = str(res, "host");
		int port = res.get("port").getAsInt();
		if (net == null || !net.sameServer(host, port)) {
			if (net != null) {
				net.close();
			}
			net = new VoiceNet(host, port);
		}
		if (audio == null) {
			audio = new AudioEngine();
			audio.start();
		}
		net.ping(r);
		r.lastPing = System.currentTimeMillis();
		return r;
	}

	private static void bye(Room r) {
		try {
			VoiceNet n = net;
			if (n != null) {
				n.send(r, VoiceNet.T_BYE, new byte[0], 0);
			}
			JsonObject body = new JsonObject();
			body.addProperty("sid", r.sid & 0xFFFFFFFFL);
			RelayClient c = RelayClient.get();
			if (c != null) {
				c.apiPost("/v1/voice/leave", body);
			}
		} catch (Throwable t) {
			Log.debug("Voice leave: {}", t.toString());
		}
		dropStreams();
	}

	private static void pushState() {
		final Room c = call, p = prox;
		final boolean m = muted, d = deafened;
		pool.execute(() -> {
			for (Room r : new Room[] {c, p}) {
				if (r == null) {
					continue;
				}
				try {
					JsonObject body = new JsonObject();
					body.addProperty("sid", r.sid & 0xFFFFFFFFL);
					body.addProperty("muted", m);
					body.addProperty("deafened", d);
					RelayClient.get().apiPost("/v1/voice/state", body);
				} catch (Throwable t) {
					Log.debug("Voice state: {}", t.toString());
				}
			}
		});
	}

	// ── game thread: proximity room, positions, keep-alive ────────────────

	public static void tick(McBridge mc, boolean enabled) {
		if (mc == null) {
			return;
		}
		String server = mc.inWorld() ? mc.server() : null;
		String want = enabled && proximityOn && !SVC && server != null && !"Singleplayer".equals(server) ? "srv:" + server : null;
		Room p = prox;
		if (want == null ? proxFor != null : !want.equals(proxFor)) {
			switchProximity(want);
		} else if (want != null && p == null && !proxBusy && System.currentTimeMillis() > proxRetryAt) {
			switchProximity(want);
		}
		boolean ear = mc.player(earPos);
		if (ear) {
			earPos[1] += 1.6; // eye height, like the speakers' positions
		}
		earValid = ear;
		if (p != null) {
			double[] pos = new double[5];
			for (Member m : p.members) {
				RemoteStream s = streams.get(m.sid);
				if (s != null) {
					boolean ok = m.ign != null && mc.playerPos(m.ign, pos);
					if (ok) {
						s.x = pos[0];
						s.y = pos[1];
						s.z = pos[2];
					}
					s.placed = ok;
				}
			}
		}
		long now = System.currentTimeMillis();
		VoiceNet n = net;
		for (Room r : new Room[] {call, prox}) {
			if (r != null && n != null && now - r.lastPing > 2000) {
				r.lastPing = now;
				n.ping(r);
			}
		}
	}

	private static void switchProximity(final String want) {
		final Room old = prox;
		prox = null;
		proxFor = want;
		proxBusy = true;
		pool.execute(() -> {
			try {
				if (old != null) {
					bye(old);
				}
				if (want == null) {
					return;
				}
				Room r = join(want);
				if (want.equals(proxFor)) {
					prox = r;
				} else {
					bye(r);
				}
			} catch (Throwable t) {
				proxRetryAt = System.currentTimeMillis() + 30_000;
				Log.debug("Proximity voice unavailable: {}", t.toString());
			} finally {
				proxBusy = false;
			}
		});
	}

	static boolean ear(double[] out) {
		if (!earValid) {
			return false;
		}
		System.arraycopy(earPos, 0, out, 0, 5);
		return true;
	}

	// ── network threads ───────────────────────────────────────────────────

	static void sendAudio(byte[] packet, int len) {
		VoiceNet n = net;
		if (n == null) {
			return;
		}
		Room c = call, p = prox;
		if (c != null) {
			n.send(c, VoiceNet.T_AUDIO, packet, len);
		}
		if (p != null) {
			n.send(p, VoiceNet.T_AUDIO, packet, len);
		}
	}

	static void onAudio(Room r, int from, int flags, int seq, byte[] opus) {
		RemoteStream s = streams.get(from);
		if (s == null) {
			try {
				s = new RemoteStream(from, r.proximity);
			} catch (Throwable t) {
				return;
			}
			for (Member m : r.members) {
				if (m.sid == from) {
					s.userId = m.userId;
				}
			}
			streams.put(from, s);
		}
		s.push(seq, flags, opus);
	}

	/** Someone in your call who is also near you: heard once, through the call. */
	static boolean duplicate(RemoteStream s) {
		Room c = call;
		if (!s.proximity || c == null || s.userId == null) {
			return false;
		}
		for (Member m : c.members) {
			if (s.userId.equals(m.userId)) {
				return true;
			}
		}
		return false;
	}

	private static void dropStreams() {
		java.util.Set<Integer> keep = new java.util.HashSet<Integer>();
		for (Room r : new Room[] {call, prox}) {
			if (r != null) {
				for (Member m : r.members) {
					keep.add(m.sid);
				}
			}
		}
		streams.keySet().retainAll(keep);
	}

	// ── Relay events ──────────────────────────────────────────────────────

	/** `voice:state` from the mod event stream. */
	public static void onState(JsonObject p) {
		String room = str(p, "room");
		if (room == null) {
			return;
		}
		List<Member> members = members(p.getAsJsonArray("members"));
		Room c = call, x = prox;
		if (c != null && room.equals(c.room)) {
			c.members = members;
		} else if (x != null && room.equals(x.room)) {
			x.members = members;
		}
		String key = convKey(room);
		if (key != null && (c == null || !room.equals(c.room))) {
			if (members.isEmpty()) {
				live.remove(key);
			} else {
				live.put(key, members);
			}
			JsonObject started = p.has("started") && p.get("started").isJsonObject() ? p.getAsJsonObject("started") : null;
			String me = me();
			if (started != null && me != null && !me.equals(str(started, "userId")) && key.startsWith("g:")) {
				ring(key, str(started, "name"), started); // private calls ring through voice:ring (accept / decline)
			}
		}
		for (Member m : members) {
			RemoteStream s = streams.get(m.sid);
			if (s != null) {
				s.userId = m.userId;
			}
		}
		dropStreams();
	}

	/** After (re)connecting: which calls are already running in your conversations. */
	public static void refreshCalls() {
		pool.execute(() -> {
			try {
				JsonObject res = RelayClient.get().getApi("/v1/voice/calls");
				live.clear();
				for (JsonElement e : res.getAsJsonArray("calls")) {
					JsonObject o = e.getAsJsonObject();
					String key = convKey(str(o, "room"));
					List<Member> m = members(o.getAsJsonArray("members"));
					Room c = call;
					if (key != null && !m.isEmpty() && (c == null || !c.key.equals(key))) {
						live.put(key, m);
					}
					JsonObject ringing = o.has("ringing") && o.get("ringing").isJsonObject() ? o.getAsJsonObject("ringing") : null;
					if (key != null && ringing != null && (c == null || !c.key.equals(key))) {
						addIncoming(key, str(ringing, "userId"), str(ringing, "name"), null,
								ringing.has("timeoutMs") ? ringing.get("timeoutMs").getAsLong() : 45_000);
					}
				}
			} catch (Throwable t) {
				Log.debug("Voice calls: {}", t.toString());
			}
		});
	}

	private static void ring(String key, String who, JsonObject started) {
		RelayClient client = RelayClient.get();
		if (client == null) {
			return;
		}
		String title = who == null ? "Voice call" : who;
		String skin = null;
		if (key.startsWith("g:")) {
			Model.Group g = client.group(key.substring(2));
			title = g == null ? "Group call" : g.name;
		} else {
			Model.Friend f = client.friend(key.substring(3));
			if (f != null) {
				title = f.display();
				skin = f.skin;
			}
		}
		client.notices.add(new Model.Notice(key, title, (key.startsWith("g:") && who != null ? who + " started" : "Started") + " a voice call", who, skin));
	}

	// ── speaker test (Settings) ───────────────────────────────────────────

	private static volatile String speakerStatus;

	/** "Playing...", "Played ...", or "Could not play: ..."; null before the first test. */
	public static String speakerTestStatus() {
		return speakerStatus;
	}

	/** Plays a short three-note chime through the chosen output device so the player can check it. */
	public static void testSpeakers() {
		if ("Playing...".equals(speakerStatus)) {
			return;
		}
		speakerStatus = "Playing...";
		final String device = outDevice;
		final float gain = Math.max(0.25f, Math.min(1f, outVolume)) * 0.4f;
		Thread t = new Thread(() -> {
			javax.sound.sampled.SourceDataLine line = null;
			try {
				javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(48000, 16, 1, true, false);
				line = VoiceDevices.open(javax.sound.sampled.SourceDataLine.class, fmt, device);
				line.open(fmt, 48000 / 5 * 2);
				line.start();
				byte[] pcm = chime(48000, gain);
				line.write(pcm, 0, pcm.length);
				line.drain();
				speakerStatus = "Played: did you hear it?";
			} catch (Throwable e) {
				String m = e.getMessage();
				speakerStatus = "Could not play: " + (m == null || m.isEmpty() ? e.getClass().getSimpleName() : m);
			} finally {
				if (line != null) {
					try {
						line.close();
					} catch (Throwable ignored) {
						// already gone
					}
				}
			}
		}, "Native-Voice-SpeakerTest");
		t.setDaemon(true);
		t.start();
	}

	/** Three rising notes (C5, E5, G5) as 16-bit mono PCM, each with a quick fade in and a slow fade out. */
	static byte[] chime(int rate, float gain) {
		double[] freq = {523.25, 659.25, 783.99};
		int note = rate * 28 / 100;
		byte[] out = new byte[note * freq.length * 2];
		for (int n = 0; n < freq.length; n++) {
			for (int i = 0; i < note; i++) {
				double env = Math.min(1.0, i / (rate * 0.01)) * Math.pow(1.0 - i / (double) note, 1.6);
				short v = (short) (Math.sin(2 * Math.PI * freq[n] * i / rate) * env * gain * 32767);
				int at = (n * note + i) * 2;
				out[at] = (byte) v;
				out[at + 1] = (byte) (v >> 8);
			}
		}
		return out;
	}

	// ── private calls: ringing, accept, decline ───────────────────────────

	/** Someone is calling you: shown as a card with accept / decline until it is answered, cancelled or times out. */
	public static final class Ring {
		public final String key, userId, name, skin;
		public final long at, until;

		Ring(String key, String userId, String name, String skin, long timeoutMs) {
			this.key = key;
			this.userId = userId;
			this.name = name;
			this.skin = skin;
			this.at = System.currentTimeMillis();
			this.until = at + Math.max(5000, Math.min(120_000, timeoutMs));
		}
	}

	private static final Map<String, Ring> incoming = new ConcurrentHashMap<String, Ring>();
	private static volatile Thread ringer;

	/** The newest call that is ringing, or null. */
	public static Ring incoming() {
		long now = System.currentTimeMillis();
		Ring best = null;
		for (Ring r : incoming.values()) {
			if (now > r.until) {
				incoming.remove(r.key);
			} else if (best == null || r.at > best.at) {
				best = r;
			}
		}
		return best;
	}

	public static boolean hasIncoming() {
		return incoming() != null;
	}

	/** The ring for one conversation, or null. */
	public static Ring ringing(String convKey) {
		Ring r = incoming.get(convKey);
		return r != null && System.currentTimeMillis() <= r.until ? r : null;
	}

	/** Conversation key of your private call that nobody has joined yet ("Calling..."), or null. */
	public static String calling() {
		Room r = call;
		if (r == null || !r.key.startsWith("dm:")) {
			return null;
		}
		String me = me();
		for (Member m : r.members) {
			if (me == null || !me.equals(m.userId)) {
				return null;
			}
		}
		return r.key;
	}

	/** Picks up: joins the call (and switches Voice Chat on if it was off). */
	public static void accept(String convKey) {
		xyz.nativelaunch.ui.mod.Module m = xyz.nativelaunch.ui.mod.Modules.get("voice");
		if (m != null && !m.enabled) {
			m.setEnabled(true);
			xyz.nativelaunch.ui.mod.Modules.changed();
		}
		incoming.remove(convKey);
		joinCall(convKey);
	}

	/** Says no: the caller's call ends too (the server tells their game). */
	public static void decline(final String convKey) {
		incoming.remove(convKey);
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("room", convKey);
				RelayClient.get().apiPost("/v1/voice/decline", body);
			} catch (Throwable t) {
				Log.debug("Voice decline: {}", t.toString());
			}
		});
	}

	private static void addIncoming(String key, String userId, String name, String skin, long timeoutMs) {
		Room c = call;
		if (key == null || !key.startsWith("dm:") || c != null && c.key.equals(key)) {
			return;
		}
		RelayClient client = RelayClient.get();
		Model.Friend f = client == null ? null : client.friend(key.substring(3));
		if (f != null) {
			name = f.display();
			skin = f.skin;
		}
		incoming.put(key, new Ring(key, userId, name == null ? "Someone" : name, skin, timeoutMs));
		startRinger();
	}

	/** `voice:ring`: a friend is calling you. */
	public static void onRing(JsonObject p) {
		String key = convKey(str(p, "room") == null ? "" : str(p, "room"));
		JsonObject from = p.has("from") && p.get("from").isJsonObject() ? p.getAsJsonObject("from") : null;
		addIncoming(key, str(from, "userId"), str(from, "name"), str(from, "skin"), p.has("timeoutMs") ? p.get("timeoutMs").getAsLong() : 45_000);
	}

	/** `voice:ring-end`: the ring stopped (answered here or elsewhere, declined, the caller hung up, nobody answered). */
	public static void onRingEnd(JsonObject p) {
		String key = convKey(str(p, "room") == null ? "" : str(p, "room"));
		if (key == null) {
			return;
		}
		Ring r = incoming.remove(key);
		String reason = str(p, "reason");
		if (r != null && ("cancelled".equals(reason) || "missed".equals(reason) || "no-answer".equals(reason))) {
			RelayClient client = RelayClient.get();
			if (client != null) {
				client.notices.add(new Model.Notice(key, "Missed call", r.name + " tried to call you", r.name, r.skin));
			}
		}
	}

	/** `voice:declined`: the friend you were calling said no (or did not answer): your side of the call ends. */
	public static void onDeclined(JsonObject p) {
		String room = str(p, "room");
		Room c = call;
		if (room == null || c == null || !room.equals(c.room)) {
			return;
		}
		JsonObject by = p.has("by") && p.get("by").isJsonObject() ? p.getAsJsonObject("by") : null;
		String name = str(by, "name");
		RelayClient client = RelayClient.get();
		Model.Friend f = client == null || c.key.length() < 4 ? null : client.friend(c.key.substring(3));
		if (f != null) {
			name = f.display();
		}
		String who = name == null || name.isEmpty() ? "They" : name;
		boolean unanswered = "no-answer".equals(str(p, "reason"));
		call = null;
		final Room ended = c;
		pool.execute(() -> bye(ended));
		note(unanswered ? who + " did not answer" : who + " declined the call");
		if (client != null) {
			client.notices.add(new Model.Notice(c.key, unanswered ? "No answer" : "Call declined", unanswered ? who + " did not answer" : who + " declined your call", name,
					f == null ? null : f.skin));
		}
	}

	private static void startRinger() {
		Thread t = ringer;
		if (t != null && t.isAlive()) {
			return;
		}
		t = new Thread(Voice::ringLoop, "Native-Voice-Ring");
		t.setDaemon(true);
		ringer = t;
		t.start();
	}

	/** The ring tone: two short beeps every three seconds while a call is ringing (not while deafened). */
	private static void ringLoop() {
		javax.sound.sampled.SourceDataLine line = null;
		try {
			javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(48000, 16, 1, true, false);
			line = VoiceDevices.open(javax.sound.sampled.SourceDataLine.class, fmt, outDevice);
			line.open(fmt, 48000 / 5 * 2);
			line.start();
			byte[] chunk = new byte[960 * 2];
			long pos = 0;
			final int period = 48000 * 3;
			while (incoming() != null) {
				float gain = deafened ? 0f : Math.min(1f, outVolume) * 0.3f;
				for (int i = 0; i < 960; i++) {
					int at = (int) ((pos + i) % period);
					double env = beepEnvelope(at);
					double v = env == 0 ? 0 : (Math.sin(2 * Math.PI * 440 * at / 48000.0) + Math.sin(2 * Math.PI * 480 * at / 48000.0)) * 0.5 * env;
					short sample = (short) (v * gain * 32767);
					chunk[2 * i] = (byte) sample;
					chunk[2 * i + 1] = (byte) (sample >> 8);
				}
				pos += 960;
				line.write(chunk, 0, chunk.length); // blocks: paces the loop at real time
			}
			line.stop();
			line.flush();
		} catch (Throwable t) {
			Log.debug("Ring tone unavailable: {}", t.toString());
		} finally {
			if (line != null) {
				try {
					line.close();
				} catch (Throwable ignored) {
					// already gone
				}
			}
		}
	}

	/** Two beeps in a 3 s cycle (0 - 0.4 s and 0.6 - 1.0 s) with short fades so they do not click. */
	static double beepEnvelope(int sample) {
		int fade = 48 * 6;
		for (int start : new int[] {0, 28800}) {
			int rel = sample - start;
			if (rel >= 0 && rel < 19200) {
				return Math.min(1.0, Math.min(rel, 19200 - rel) / (double) fade);
			}
		}
		return 0;
	}

	/** "dm:a:b" -> "dm:<other>", "g:id" -> "g:id"; null for proximity rooms. */
	private static String convKey(String room) {
		if (room.startsWith("g:")) {
			return room;
		}
		if (room.startsWith("dm:")) {
			String[] ids = room.substring(3).split(":");
			String me = me();
			if (ids.length != 2 || me == null) {
				return null;
			}
			return "dm:" + (ids[0].equals(me) ? ids[1] : ids[0]);
		}
		return null;
	}

	// ── helpers ───────────────────────────────────────────────────────────

	private static McBridge mc() {
		try {
			return xyz.nativelaunch.ui.UiRuntime.mc();
		} catch (Throwable t) {
			return null;
		}
	}

	private static List<Member> members(JsonArray a) {
		List<Member> out = new ArrayList<Member>();
		if (a != null) {
			for (JsonElement e : a) {
				try {
					out.add(new Member(e.getAsJsonObject()));
				} catch (Throwable ignored) {
					// skip a malformed entry
				}
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static String message(Throwable t) {
		String m = t.getMessage();
		return m == null || m.isEmpty() ? "Voice is unavailable right now." : m;
	}

	static String str(JsonObject o, String k) {
		JsonElement e = o == null ? null : o.get(k);
		return e == null || e.isJsonNull() ? null : e.getAsString();
	}

	static boolean bool(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e != null && !e.isJsonNull() && e.getAsBoolean();
	}
}
