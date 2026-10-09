package xyz.nativelaunch.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The in-game side of Native Relay: friends with live presence, DMs and groups. Talks to the Native API with the
 * game-only ticket the launcher hands the mod (/v1/mod/friends, /v1/social/relay/* and the /v1/mod/stream SSE
 * stream with ?relay=1). All network work runs on daemon threads; the UI only reads volatile snapshots.
 */
public final class RelayClient {
	public enum State { NO_ACCOUNT, CONNECTING, ONLINE, OFFLINE }

	private static RelayClient instance;

	private final String api, ticket;
	public volatile State state = State.CONNECTING;
	public volatile String meId, meName, meSkin;
	public volatile List<Model.Friend> friends = Collections.emptyList();
	public volatile List<Model.Group> groups = Collections.emptyList();
	public volatile int requests;
	public volatile boolean groupsLoaded, friendsLoaded;
	private final Map<String, Model.Conversation> conversations = new ConcurrentHashMap<String, Model.Conversation>();
	private final Map<String, Long> typing = new ConcurrentHashMap<String, Long>(); // conv|userId -> until
	public final ConcurrentLinkedQueue<Model.Notice> notices = new ConcurrentLinkedQueue<Model.Notice>();
	/** The conversation the player is looking at (null when the chat is closed): no notices, auto-read. */
	public volatile String viewing;
	private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "Native-Relay");
		t.setDaemon(true);
		return t;
	});
	private volatile long lastTypingSent;

	private RelayClient(String api, String ticket) {
		this.api = api;
		this.ticket = ticket;
		if (ticket == null) {
			state = State.NO_ACCOUNT;
		}
	}

	public static synchronized RelayClient get() {
		return instance;
	}

	public static synchronized void start(String api, String ticket) {
		if (instance != null) {
			return;
		}
		instance = new RelayClient(api, ticket);
		if (ticket != null) {
			Thread t = new Thread(instance::streamLoop, "Native-Relay-Stream");
			t.setDaemon(true);
			t.setPriority(Thread.MIN_PRIORITY);
			t.start();
		}
	}

	public String api() {
		return api;
	}

	// ── reads ─────────────────────────────────────────────────────────────

	public Model.Conversation conversation(String key) {
		Model.Conversation c = conversations.get(key);
		if (c == null) {
			c = new Model.Conversation(key);
			Model.Conversation prev = conversations.putIfAbsent(key, c);
			if (prev != null) {
				c = prev;
			}
		}
		return c;
	}

	public Model.Friend friend(String id) {
		for (Model.Friend f : friends) {
			if (f.id.equals(id)) {
				return f;
			}
		}
		return null;
	}

	public Model.Group group(String id) {
		for (Model.Group g : groups) {
			if (g.id.equals(id)) {
				return g;
			}
		}
		return null;
	}

	public int unreadTotal() {
		int n = 0;
		for (Model.Friend f : friends) {
			n += f.unread;
		}
		for (Model.Group g : groups) {
			n += g.unread;
		}
		return n;
	}

	/** Names typing in a conversation right now. */
	public List<String> typing(String convKey) {
		List<String> out = new ArrayList<String>();
		long now = System.currentTimeMillis();
		for (Map.Entry<String, Long> e : typing.entrySet()) {
			if (e.getValue() < now || !e.getKey().startsWith(convKey + "|")) {
				continue;
			}
			String uid = e.getKey().substring(convKey.length() + 1);
			out.add(nameOf(uid, null));
		}
		return out;
	}

	String nameOf(String userId, String fallback) {
		if (userId == null) {
			return fallback == null ? "Someone" : fallback;
		}
		if (userId.equals(meId)) {
			return meName == null ? "You" : meName;
		}
		Model.Friend f = friend(userId);
		if (f != null) {
			return f.display();
		}
		for (Model.Group g : groups) {
			for (String[] m : g.memberList) {
				if (userId.equals(m[0])) {
					return m[1];
				}
			}
		}
		return fallback == null ? "Someone" : fallback;
	}

	// ── actions (called from the render thread; never block) ──────────────

	public void refresh() {
		if (ticket == null) {
			return;
		}
		pool.execute(this::loadFriends);
		pool.execute(this::loadGroups);
	}

	/** Opens a conversation: loads the latest page and marks it read. */
	public void open(final String key) {
		viewing = key;
		final Model.Conversation c = conversation(key);
		clearUnread(key);
		if (ticket == null || c.loading) {
			return;
		}
		c.loading = true;
		pool.execute(() -> {
			try {
				JsonObject page = getJson(messagesPath(c) + "?limit=60");
				c.messages = parseMessages(page.getAsJsonArray("messages"), c);
				c.hasMore = page.has("hasMore") && page.get("hasMore").getAsBoolean();
				c.loaded = true;
				c.error = null;
			} catch (Exception e) {
				c.error = "Could not load messages.";
			} finally {
				c.loading = false;
			}
		});
	}

	/** Loads the page before the oldest loaded message. */
	public void loadOlder(final String key) {
		final Model.Conversation c = conversation(key);
		if (ticket == null || c.loading || !c.hasMore || c.messages.isEmpty()) {
			return;
		}
		c.loading = true;
		final long before = c.messages.get(0).createdAt;
		pool.execute(() -> {
			try {
				JsonObject page = getJson(messagesPath(c) + "?limit=60&markRead=0&before=" + before);
				List<Model.Message> older = parseMessages(page.getAsJsonArray("messages"), c);
				List<Model.Message> merged = new ArrayList<Model.Message>(older);
				merged.addAll(c.messages);
				c.messages = merged;
				c.hasMore = page.has("hasMore") && page.get("hasMore").getAsBoolean();
			} catch (Exception e) {
				c.error = "Could not load older messages.";
			} finally {
				c.loading = false;
			}
		});
	}

	public void send(final String key, String text) {
		final String content = text == null ? "" : text.trim();
		if (content.isEmpty() || ticket == null) {
			return;
		}
		final Model.Conversation c = conversation(key);
		final String tempId = "local-" + System.nanoTime();
		final Model.Message pending = new Model.Message(tempId, meId, meName, content, System.currentTimeMillis(), true, false, false, false);
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>(c.messages);
			next.add(pending);
			c.messages = next;
		}
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("content", content);
				JsonObject res = postJson(messagesPath(c), body);
				Model.Message sent = parseMessage(res.getAsJsonObject("message"), c);
				synchronized (c) {
					List<Model.Message> next = new ArrayList<Model.Message>();
					boolean have = false;
					for (Model.Message m : c.messages) {
						if (m.id.equals(sent.id)) {
							have = true;
						}
						if (!m.id.equals(tempId)) {
							next.add(m);
						}
					}
					if (!have) {
						next.add(sent);
					}
					c.messages = next;
				}
			} catch (Exception e) {
				synchronized (c) {
					List<Model.Message> next = new ArrayList<Model.Message>();
					for (Model.Message m : c.messages) {
						next.add(m.id.equals(tempId) ? new Model.Message(tempId, m.senderId, m.senderName, m.content, m.createdAt, false, true, false, false) : m);
					}
					c.messages = next;
				}
			}
		});
	}

	/** Tells the other side we are typing (groups only: DMs have no ticket route for it), at most every 3 s. */
	public void typingPing(final String key) {
		long now = System.currentTimeMillis();
		if (ticket == null || !key.startsWith("g:") || now - lastTypingSent < 3000) {
			return;
		}
		lastTypingSent = now;
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("isTyping", true);
				postJson("/v1/social/relay/groups/" + enc(key.substring(2)) + "/typing", body);
			} catch (Exception ignored) {
				// cosmetic
			}
		});
	}

	private void clearUnread(String key) {
		if (key.startsWith("dm:")) {
			String id = key.substring(3);
			List<Model.Friend> next = new ArrayList<Model.Friend>();
			for (Model.Friend f : friends) {
				next.add(f.id.equals(id) && f.unread != 0 ? f.withUnread(0) : f);
			}
			friends = next;
		} else {
			String id = key.substring(2);
			List<Model.Group> next = new ArrayList<Model.Group>();
			for (Model.Group g : groups) {
				next.add(g.id.equals(id) && g.unread != 0 ? g.withUnread(0) : g);
			}
			groups = next;
		}
	}

	private String messagesPath(Model.Conversation c) {
		return c.isGroup() ? "/v1/social/relay/groups/" + enc(c.targetId()) + "/messages" : "/v1/social/relay/dm/" + enc(c.targetId()) + "/messages";
	}

	// ── loading ───────────────────────────────────────────────────────────

	private void loadFriends() {
		try {
			JsonObject o = getJson("/v1/mod/friends");
			JsonObject account = o.getAsJsonObject("account");
			if (account != null) {
				meId = str(account, "id");
				meName = str(account, "name");
				meSkin = skinRef(str(account, "skin"), str(account, "minecraftUuid"));
			}
			List<Model.Friend> list = new ArrayList<Model.Friend>();
			JsonArray arr = o.getAsJsonArray("friends");
			if (arr != null) {
				for (JsonElement e : arr) {
					JsonObject f = e.getAsJsonObject();
					list.add(new Model.Friend(str(f, "id"), str(f, "name"), str(f, "nickname"), str(f, "status"), str(f, "activity"),
							str(f, "serverAddress"), skinRef(str(f, "skin"), str(f, "mcUuid")), bool(f, "online"), num(f, "unread")));
				}
			}
			sortFriends(list);
			friends = list;
			JsonObject req = o.getAsJsonObject("requests");
			requests = req != null && req.has("received") ? req.getAsJsonArray("received").size() : 0;
			friendsLoaded = true;
			state = State.ONLINE;
		} catch (Exception e) {
			Log.debug("Relay friends failed: {}", e.toString());
			if (!friendsLoaded) {
				state = State.OFFLINE;
			}
		}
	}

	private static void sortFriends(List<Model.Friend> list) {
		Collections.sort(list, (a, b) -> {
			if (a.online != b.online) {
				return a.online ? -1 : 1;
			}
			if ((a.unread > 0) != (b.unread > 0)) {
				return a.unread > 0 ? -1 : 1;
			}
			return a.display().compareToIgnoreCase(b.display());
		});
	}

	private void loadGroups() {
		try {
			JsonObject o = getJson("/v1/social/relay/groups");
			List<Model.Group> list = new ArrayList<Model.Group>();
			JsonArray arr = o.getAsJsonArray("groups");
			if (arr != null) {
				for (JsonElement e : arr) {
					list.add(parseGroup(e.getAsJsonObject()));
				}
			}
			groups = list;
			groupsLoaded = true;
		} catch (Exception e) {
			Log.debug("Relay groups failed: {}", e.toString());
		}
	}

	private static Model.Group parseGroup(JsonObject g) {
		JsonObject last = g.has("lastMessage") && g.get("lastMessage").isJsonObject() ? g.getAsJsonObject("lastMessage") : null;
		List<String[]> members = new ArrayList<String[]>();
		if (g.has("members") && g.get("members").isJsonArray()) {
			for (JsonElement m : g.getAsJsonArray("members")) {
				JsonObject mo = m.getAsJsonObject();
				String name = str(mo, "name");
				if (name == null) {
					name = str(mo, "username");
				}
				members.add(new String[] {str(mo, "id"), name == null ? "Member" : name});
			}
		}
		return new Model.Group(str(g, "id"), str(g, "name"), last == null ? null : str(last, "content"), last == null ? null : str(last, "senderName"),
				last == null ? 0 : lng(last, "createdAt"), num(g, "unreadCount"), num(g, "memberCount"), members);
	}

	private List<Model.Message> parseMessages(JsonArray arr, Model.Conversation c) {
		List<Model.Message> out = new ArrayList<Model.Message>();
		if (arr != null) {
			for (JsonElement e : arr) {
				out.add(parseMessage(e.getAsJsonObject(), c));
			}
		}
		return out;
	}

	private Model.Message parseMessage(JsonObject m, Model.Conversation c) {
		String sender = str(m, "senderId");
		String content = str(m, "content");
		boolean deleted = bool(m, "isDeleted");
		if ((content == null || content.isEmpty()) && !deleted) {
			String media = str(m, "mediaName");
			content = media != null ? "[" + media + "]" : (bool(m, "isMedia") ? "[attachment]" : "");
		}
		return new Model.Message(str(m, "id"), sender, nameOf(sender, str(m, "senderName")), deleted ? "Message deleted" : content,
				lng(m, "createdAt"), false, false, bool(m, "isSystem"), deleted);
	}

	// ── live stream ───────────────────────────────────────────────────────

	private void streamLoop() {
		long backoff = 2000;
		loadFriends();
		loadGroups();
		while (true) {
			long started = System.currentTimeMillis();
			try {
				stream();
			} catch (Exception e) {
				Log.debug("Relay stream dropped: {}", e.toString());
			}
			if (System.currentTimeMillis() - started > 60_000) {
				backoff = 2000;
			}
			if (state == State.ONLINE) {
				state = State.CONNECTING;
			}
			try {
				Thread.sleep(backoff);
			} catch (InterruptedException e) {
				return;
			}
			backoff = Math.min(backoff * 2, 60_000);
			loadFriends();
		}
	}

	private void stream() throws IOException {
		HttpURLConnection con = open("/v1/mod/stream?relay=1&activity=" + enc("Playing Minecraft"), "GET");
		con.setReadTimeout(70_000);
		con.setRequestProperty("Accept", "text/event-stream");
		int status = con.getResponseCode();
		if (status == 401) {
			state = State.NO_ACCOUNT;
			throw new IOException("ticket rejected");
		}
		if (status / 100 != 2) {
			throw new IOException("HTTP " + status);
		}
		try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
			String type = "message";
			StringBuilder data = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				if (line.isEmpty()) {
					if (data.length() > 0) {
						try {
							handle(type, new JsonParser().parse(data.toString()).getAsJsonObject());
						} catch (Exception e) {
							Log.debug("Relay event {} ignored: {}", type, e.toString());
						}
					}
					type = "message";
					data.setLength(0);
				} else if (line.startsWith("event:")) {
					type = line.substring(6).trim();
				} else if (line.startsWith("data:")) {
					data.append(line.substring(5).trim());
				}
			}
		} finally {
			con.disconnect();
		}
	}

	private void handle(String type, JsonObject p) {
		switch (type) {
			case "hello":
				state = State.ONLINE;
				refresh();
				for (Model.Conversation c : conversations.values()) {
					if (c.loaded && c.key.equals(viewing)) {
						open(c.key);
					}
				}
				break;
			case "presence": {
				String id = str(p, "userId");
				List<Model.Friend> next = new ArrayList<Model.Friend>();
				for (Model.Friend f : friends) {
					next.add(f.id.equals(id) ? f.withPresence(str(p, "status"), str(p, "activity"), str(p, "serverAddress")) : f);
				}
				sortFriends(next);
				friends = next;
				break;
			}
			case "friends:changed":
			case "request:changed":
				pool.execute(this::loadFriends);
				break;
			case "group:created":
			case "group:updated":
			case "group:removed":
			case "group:deleted":
				pool.execute(this::loadGroups);
				break;
			case "message:new": {
				JsonObject m = p.getAsJsonObject("message");
				String sender = str(m, "senderId"), receiver = str(m, "receiverId");
				String other = sender != null && sender.equals(meId) ? receiver : sender;
				incoming("dm:" + other, m, sender);
				break;
			}
			case "group:message": {
				JsonObject m = p.getAsJsonObject("message");
				incoming("g:" + str(p, "groupId"), m, str(m, "senderId"));
				break;
			}
			case "message:updated":
			case "group:message:updated": {
				JsonObject m = p.getAsJsonObject("message");
				String id = str(m, "id");
				for (Model.Conversation c : conversations.values()) {
					synchronized (c) {
						List<Model.Message> next = null;
						for (int i = 0; i < c.messages.size(); i++) {
							if (c.messages.get(i).id.equals(id)) {
								next = new ArrayList<Model.Message>(c.messages);
								next.set(i, parseMessage(m, c));
							}
						}
						if (next != null) {
							c.messages = next;
						}
					}
				}
				break;
			}
			case "typing": {
				String uid = str(p, "userId");
				setTyping("dm:" + uid, uid, bool(p, "isTyping"));
				break;
			}
			case "group:typing":
				setTyping("g:" + str(p, "groupId"), str(p, "userId"), bool(p, "isTyping"));
				break;
			default:
				break;
		}
	}

	private void setTyping(String conv, String uid, boolean on) {
		if (uid == null) {
			return;
		}
		if (on) {
			typing.put(conv + "|" + uid, System.currentTimeMillis() + 6000);
		} else {
			typing.remove(conv + "|" + uid);
		}
	}

	private void incoming(String key, JsonObject m, String sender) {
		Model.Conversation c = conversation(key);
		Model.Message msg = parseMessage(m, c);
		typing.remove(key + "|" + sender);
		boolean mine = sender != null && sender.equals(meId);
		if (c.loaded) {
			synchronized (c) {
				boolean have = false;
				for (Model.Message x : c.messages) {
					if (x.id.equals(msg.id)) {
						have = true;
						break;
					}
				}
				if (!have) {
					List<Model.Message> next = new ArrayList<Model.Message>();
					// a pending copy of our own message is replaced by the real one
					boolean replaced = false;
					for (Model.Message x : c.messages) {
						if (!replaced && mine && x.pending && x.content.equals(msg.content)) {
							replaced = true;
							continue;
						}
						next.add(x);
					}
					next.add(msg);
					c.messages = next;
				}
			}
		}
		if (mine) {
			return;
		}
		boolean watching = key.equals(viewing);
		if (key.startsWith("g:")) {
			String gid = key.substring(2);
			List<Model.Group> next = new ArrayList<Model.Group>();
			Model.Group hit = null;
			for (Model.Group g : groups) {
				if (g.id.equals(gid)) {
					hit = g.withLast(msg.content, msg.senderName, msg.createdAt, watching ? 0 : g.unread + 1);
					next.add(0, hit);
				} else {
					next.add(g);
				}
			}
			groups = next;
			if (watching) {
				markReadLater(key);
			} else if (!msg.system) {
				notices.add(new Model.Notice(key, (hit == null ? "Group" : hit.name), msg.senderName + ": " + msg.content, msg.senderName, friend(sender) == null ? null : friend(sender).skin));
			}
		} else {
			String fid = key.substring(3);
			List<Model.Friend> next = new ArrayList<Model.Friend>();
			Model.Friend hit = null;
			for (Model.Friend f : friends) {
				if (f.id.equals(fid)) {
					hit = watching ? f : f.withUnread(f.unread + 1);
					next.add(hit);
				} else {
					next.add(f);
				}
			}
			sortFriends(next);
			friends = next;
			if (watching) {
				markReadLater(key);
			} else {
				notices.add(new Model.Notice(key, hit == null ? msg.senderName : hit.display(), msg.content, hit == null ? msg.senderName : hit.name, hit == null ? null : hit.skin));
			}
		}
	}

	/** A message arrived in the open chat: fetch the page again so the server marks it read. */
	private void markReadLater(final String key) {
		final Model.Conversation c = conversation(key);
		pool.execute(() -> {
			try {
				if (c.isGroup()) {
					postJson("/v1/social/relay/groups/" + enc(c.targetId()) + "/read", new JsonObject());
				} else {
					getJson(messagesPath(c) + "?limit=1");
				}
			} catch (Exception ignored) {
				// best effort
			}
		});
	}

	// ── HTTP ──────────────────────────────────────────────────────────────

	private HttpURLConnection open(String path, String method) throws IOException {
		HttpURLConnection con = (HttpURLConnection) new URL(api + path).openConnection();
		con.setRequestMethod(method);
		con.setConnectTimeout(8000);
		con.setReadTimeout(15000);
		con.setRequestProperty("User-Agent", "NativeMod-Relay (Minecraft)");
		con.setRequestProperty("Authorization", "Bearer " + ticket);
		return con;
	}

	private JsonObject getJson(String path) throws IOException {
		HttpURLConnection con = open(path, "GET");
		con.setRequestProperty("Accept", "application/json");
		try {
			return read(con);
		} finally {
			con.disconnect();
		}
	}

	private JsonObject postJson(String path, JsonObject body) throws IOException {
		HttpURLConnection con = open(path, "POST");
		byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
		con.setDoOutput(true);
		con.setRequestProperty("Content-Type", "application/json");
		con.setFixedLengthStreamingMode(bytes.length);
		try {
			try (OutputStream out = con.getOutputStream()) {
				out.write(bytes);
			}
			return read(con);
		} finally {
			con.disconnect();
		}
	}

	private static JsonObject read(HttpURLConnection con) throws IOException {
		int status = con.getResponseCode();
		if (status / 100 != 2) {
			throw new IOException("HTTP " + status);
		}
		InputStream in = con.getInputStream();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) {
			if (out.size() > 8 * 1024 * 1024) {
				throw new IOException("Response too large");
			}
			out.write(buf, 0, n);
		}
		return new JsonParser().parse(new String(out.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
		} catch (Exception e) {
			return s;
		}
	}

	static String str(JsonObject o, String k) {
		return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
	}

	static boolean bool(JsonObject o, String k) {
		if (o == null || !o.has(k) || !o.get(k).isJsonPrimitive()) {
			return false;
		}
		com.google.gson.JsonPrimitive v = o.getAsJsonPrimitive(k);
		return v.isNumber() ? v.getAsDouble() != 0 : v.getAsBoolean();
	}

	static int num(JsonObject o, String k) {
		try {
			return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	/** Native skin hash, else "mj:&lt;uuid&gt;" for a linked Mojang account (see Avatars), else null. */
	static String skinRef(String nativeSkin, String mojangUuid) {
		if (nativeSkin != null && !nativeSkin.isEmpty()) {
			return nativeSkin;
		}
		return mojangUuid == null || mojangUuid.isEmpty() ? null : "mj:" + mojangUuid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}

	static long lng(JsonObject o, String k) {
		try {
			return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : 0;
		} catch (Exception e) {
			return 0;
		}
	}
}
