package xyz.nativelaunch.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
	static volatile boolean selfSpeaking;
	static volatile float selfLevel;

	private static volatile Room call, prox;
	private static volatile String joining;
	private static volatile String error;
	private static final Map<String, List<Member>> live = new ConcurrentHashMap<String, List<Member>>();
	private static final Map<Integer, RemoteStream> streams = new ConcurrentHashMap<Integer, RemoteStream>();
	private static final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Native-Voice");
		t.setDaemon(true);
		return t;
	});
	private static VoiceNet net;
	private static AudioEngine audio;
	private static volatile long proxRetryAt;
	private static volatile String proxFor;
	private static volatile boolean proxBusy;
	private static final double[] earPos = new double[5];
	private static volatile boolean earValid;

	private Voice() {
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
		return error;
	}

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
		return active() && !muted && !deafened;
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
		String want = enabled && proximityOn && server != null && !"Singleplayer".equals(server) ? "srv:" + server : null;
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
			if (started != null && me != null && !me.equals(str(started, "userId"))) {
				ring(key, str(started, "name"), started);
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
