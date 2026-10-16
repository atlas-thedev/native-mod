package xyz.nativelaunch.relay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.nativelaunch.core.Log;
import xyz.nativelaunch.core.Session;
import xyz.nativelaunch.ui.ChatMedia;

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
import java.util.Locale;
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

	private final String api;
	public volatile State state = State.CONNECTING;
	public volatile String meId, meName, meSkin;
	public volatile List<Model.Friend> friends = Collections.emptyList();
	public volatile List<Model.Group> groups = Collections.emptyList();
	public volatile int requests;
	/** Friend requests (the Requests tab). */
	public volatile List<Model.Request> received = Collections.emptyList(), sent = Collections.emptyList();
	public volatile boolean requestsLoaded;
	/** Players you blocked (the Blocked tab). */
	public volatile List<Model.Person> blocked = Collections.emptyList();
	public volatile boolean blockedLoaded;
	/** "Add friend" search results, and the query they belong to. */
	public volatile List<Model.Person> found = Collections.emptyList();
	public volatile String foundFor = "";
	public volatile boolean searching;
	/** A short note after a friend action ("Request sent to Steve"), shown in the Requests tab. */
	public volatile String notice;
	public volatile boolean noticeError;
	/** Public profiles by lowercase name for the side panel. */
	private final Map<String, Model.Profile> profiles = new ConcurrentHashMap<String, Model.Profile>();
	private final java.util.Set<String> profileLoading = ConcurrentHashMap.newKeySet();
	public volatile boolean groupsLoaded, friendsLoaded;
	private final Map<String, Model.Conversation> conversations = new ConcurrentHashMap<String, Model.Conversation>();
	private final Map<String, Long> typing = new ConcurrentHashMap<String, Long>(); // conv|userId -> until
	public final ConcurrentLinkedQueue<Model.Notice> notices = new ConcurrentLinkedQueue<Model.Notice>();
	/** The conversation the player is looking at (null when the chat is closed): no notices, auto-read. */
	public volatile String viewing;
	private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "Native-Relay");
		t.setDaemon(true);
		return t;
	});
	private volatile long lastTypingSent;

	private RelayClient(String api) {
		this.api = api;
		if (ticket() == null) {
			state = State.NO_ACCOUNT;
		}
	}

	public static synchronized RelayClient get() {
		return instance;
	}

	public static synchronized void start(String api) {
		if (instance != null) {
			return;
		}
		instance = new RelayClient(api);
		// Always runs: it waits for a sign-in, so a ticket that arrives (or comes back) mid-game connects Relay.
		Thread t = new Thread(instance::streamLoop, "Native-Relay-Stream");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	/** The current game ticket (null when signed out or rejected). The launcher may replace it mid-game. */
	public String ticket() {
		return Session.ticket();
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
			if (viewing == null || !viewing.equals("dm:" + f.id)) {
				n += f.unread;
			}
		}
		for (Model.Group g : groups) {
			if (viewing == null || !viewing.equals("g:" + g.id)) {
				n += g.unread;
			}
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
		if (ticket() == null) {
			return;
		}
		pool.execute(this::loadFriends);
		pool.execute(this::loadGroups);
		pool.execute(this::loadRequests);
		pool.execute(this::loadBlocked);
	}

	/** The conversation if it was ever opened (null otherwise): the list reads its last message. */
	public Model.Conversation peek(String key) {
		return conversations.get(key);
	}

	/** Opens a conversation: loads the latest page and marks it read. */
	public void open(final String key) {
		viewing = key;
		final Model.Conversation c = conversation(key);
		clearUnread(key);
		markReadLater(key);
		if (ticket() == null || c.loading) {
			return;
		}
		c.loading = true;
		pool.execute(() -> {
			try {
				JsonObject page = getJson(messagesPath(c) + "?limit=60");
				List<Model.Message> latest = parseMessages(page.getAsJsonArray("messages"), c);
				boolean more = page.has("hasMore") && page.get("hasMore").getAsBoolean();
				synchronized (c) {
					// merge instead of replacing: reopening the chat used to drop the older pages already read, a message
					// that arrived while the page loaded and our own pending sends (rows vanished, the view jumped)
					List<Model.Message> merged = mergeLatest(c.messages, latest);
					boolean keptOlder = !latest.isEmpty() && merged.get(0) != latest.get(0);
					c.hasMore = keptOlder ? c.hasMore : more;
					c.messages = merged;
				}
				c.loaded = true;
				c.error = null;
			} catch (Exception e) {
				c.error = "Could not load messages.";
			} finally {
				c.loading = false;
			}
		});
	}

	/** The latest page plus what only we have: older pages already loaded, pending / failed sends, newer live rows. */
	static List<Model.Message> mergeLatest(List<Model.Message> have, List<Model.Message> page) {
		if (have.isEmpty()) {
			return page;
		}
		java.util.Set<String> ids = new java.util.HashSet<String>();
		for (Model.Message m : page) {
			ids.add(m.id);
		}
		long first = page.isEmpty() ? Long.MIN_VALUE : page.get(0).createdAt;
		long last = page.isEmpty() ? Long.MIN_VALUE : page.get(page.size() - 1).createdAt;
		List<Model.Message> out = new ArrayList<Model.Message>();
		for (Model.Message m : have) {
			if (!page.isEmpty() && m.createdAt < first && !ids.contains(m.id) && !m.pending && !m.failed) {
				out.add(m);
			}
		}
		out.addAll(page);
		for (Model.Message m : have) {
			if (!ids.contains(m.id) && (m.pending || m.failed || !page.isEmpty() && m.createdAt > last)) {
				out.add(m);
			}
		}
		return out;
	}

	/** Loads the page before the oldest loaded message. */
	public void loadOlder(final String key) {
		final Model.Conversation c = conversation(key);
		if (ticket() == null || c.loading || !c.hasMore || c.messages.isEmpty()) {
			return;
		}
		c.loading = true;
		final long before = c.messages.get(0).createdAt;
		pool.execute(() -> {
			try {
				JsonObject page = getJson(messagesPath(c) + "?limit=60&markRead=0&before=" + before);
				List<Model.Message> older = parseMessages(page.getAsJsonArray("messages"), c);
				synchronized (c) {
					java.util.Set<String> have = new java.util.HashSet<String>();
					for (Model.Message m : c.messages) {
						have.add(m.id);
					}
					List<Model.Message> merged = new ArrayList<Model.Message>();
					for (Model.Message m : older) {
						if (!have.contains(m.id)) {
							merged.add(m);
						}
					}
					merged.addAll(c.messages);
					c.messages = merged;
				}
				c.hasMore = page.has("hasMore") && page.get("hasMore").getAsBoolean();
			} catch (Exception e) {
				c.error = "Could not load older messages.";
			} finally {
				c.loading = false;
			}
		});
	}

	public void send(final String key, String text) {
		send(key, text, null);
	}

	/** Sends text, as an answer to {@code replyTo} when given. */
	public void send(final String key, String text, final Model.Message replyTo) {
		final String content = text == null ? "" : text.trim();
		if (content.isEmpty() || ticket() == null) {
			return;
		}
		final Model.Conversation c = conversation(key);
		final String tempId = "local-" + System.nanoTime();
		final Model.Message pending = new Model.Message(tempId, meId, meName, content, System.currentTimeMillis(), true, false, false, false);
		if (replyTo != null && replyTo.id != null && !replyTo.id.startsWith("local-")) {
			pending.reply = quote(replyTo);
		}
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>(c.messages);
			next.add(pending);
			c.messages = next;
		}
		bump(key, pending.createdAt);
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("content", content);
				if (pending.reply != null) {
					body.addProperty("replyTo", pending.reply.id);
				}
				JsonObject res = postJson(messagesPath(c), body);
				replacePending(c, tempId, parseMessage(res.getAsJsonObject("message"), c));
			} catch (Exception e) {
				fail(c, tempId);
			}
		});
	}

	/** Uploads a pasted picture (PNG/JPEG/GIF/WebP bytes) and posts it. Shows at once with a local preview. */
	public void sendImage(final String key, final byte[] bytes, final String mime) {
		if (ticket() == null || bytes == null || bytes.length == 0 || bytes.length > 8 * 1024 * 1024) {
			return;
		}
		final Model.Conversation c = conversation(key);
		final String tempId = "local-" + System.nanoTime();
		final String ext = "image/jpeg".equals(mime) ? "jpg" : "image/gif".equals(mime) ? "gif" : "image/webp".equals(mime) ? "webp" : "png";
		final String name = "image-" + System.currentTimeMillis() + "." + ext;
		ChatMedia.putLocal(tempId, bytes);
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>(c.messages);
			next.add(new Model.Message(tempId, meId, meName, "", System.currentTimeMillis(), true, false, false, false, null, name, tempId));
			c.messages = next;
		}
		pool.execute(() -> {
			try {
				JsonObject up = new JsonObject();
				up.addProperty("data", "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes));
				up.addProperty("name", name);
				JsonObject res = postJson("/v1/social/relay/upload", up);
				post(c, tempId, "", res.get("url").getAsString(), name);
			} catch (Exception e) {
				fail(c, tempId);
			}
		});
	}

	/** Sends a GIF picked from the picker (Giphy URL). */
	public void sendGif(final String key, final String url, final String title) {
		if (ticket() == null || url == null) {
			return;
		}
		final Model.Conversation c = conversation(key);
		final String tempId = "local-" + System.nanoTime();
		final String name = title == null || title.isEmpty() ? "GIF.gif" : title;
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>(c.messages);
			next.add(new Model.Message(tempId, meId, meName, "", System.currentTimeMillis(), true, false, false, false, url, name, null));
			c.messages = next;
		}
		bump(key, System.currentTimeMillis());
		pool.execute(() -> {
			try {
				post(c, tempId, "", url, name);
			} catch (Exception e) {
				fail(c, tempId);
			}
		});
	}

	private void post(Model.Conversation c, String tempId, String content, String mediaUrl, String mediaName) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("content", content);
		body.addProperty("mediaUrl", mediaUrl);
		body.addProperty("mediaName", mediaName);
		body.addProperty("mediaKind", "image");
		body.addProperty("isMedia", true);
		JsonObject res = postJson(messagesPath(c), body);
		replacePending(c, tempId, parseMessage(res.getAsJsonObject("message"), c));
	}

	/** Puts the server's copy where the pending one was (it used to jump to the end and drop the local picture). */
	private static void replacePending(Model.Conversation c, String tempId, Model.Message sent) {
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>();
			boolean have = false;
			for (Model.Message m : c.messages) {
				if (m.id.equals(sent.id)) {
					have = true;
				}
			}
			boolean placed = false;
			for (Model.Message m : c.messages) {
				if (m.id.equals(tempId)) {
					if (!have) {
						next.add(Model.withLocal(sent, m.localKey));
						placed = true;
					}
					continue;
				}
				next.add(m);
			}
			if (!have && !placed) {
				next.add(sent);
			}
			c.messages = next;
		}
	}

	private void fail(Model.Conversation c, String tempId) {
		synchronized (c) {
			List<Model.Message> next = new ArrayList<Model.Message>();
			for (Model.Message m : c.messages) {
				next.add(m.id.equals(tempId) ? Model.withState(m, false, true) : m);
			}
			c.messages = next;
		}
	}

	/** The quote a reply to {@code m} shows. */
	public static Model.Reply quote(Model.Message m) {
		return new Model.Reply(m.id, m.senderName, m.deleted ? "" : m.content, m.mediaUrl != null || m.localKey != null, m.deleted);
	}

	/** Replaces one message in a chat (by id) with {@code with(old)}; no-op when it isn't loaded. */
	private void change(Model.Conversation c, String id, java.util.function.Function<Model.Message, Model.Message> with) {
		synchronized (c) {
			List<Model.Message> next = null;
			for (int i = 0; i < c.messages.size(); i++) {
				if (id.equals(c.messages.get(i).id)) {
					if (next == null) {
						next = new ArrayList<Model.Message>(c.messages);
					}
					next.set(i, with.apply(c.messages.get(i)));
				}
			}
			if (next != null) {
				c.messages = next;
			}
		}
	}

	private String actionPath(Model.Conversation c, String id, String action) {
		return (c.isGroup() ? "/v1/social/relay/messages/" : "/v1/social/relay/dm-messages/") + enc(id) + "/" + action;
	}

	/** Toggles your reaction on a group message (the server flips it); shows at once, then takes the server's list. */
	public void react(final String key, final String messageId, final String emoji) {
		final Model.Conversation c = conversation(key);
		if (ticket() == null || !c.isGroup() || messageId == null || messageId.startsWith("local-")) {
			return;
		}
		change(c, messageId, m -> {
			List<String[]> next = new ArrayList<String[]>();
			boolean had = false;
			for (String[] r : m.reactions) {
				if (r[0] != null && r[0].equals(meId) && r[1].equals(emoji)) {
					had = true;
				} else {
					next.add(r);
				}
			}
			if (!had) {
				next.add(new String[] {meId, emoji});
			}
			Model.Message n = Model.withState(m, m.pending, m.failed);
			n.reactions = next;
			return n;
		});
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("reaction", emoji);
				JsonObject res = postJson(actionPath(c, messageId, "react"), body);
				if (res.has("reactions") && res.get("reactions").isJsonArray()) {
					final List<String[]> list = parseReactions(res.getAsJsonArray("reactions"));
					change(c, messageId, m -> {
						Model.Message n = Model.withState(m, m.pending, m.failed);
						n.reactions = list;
						return n;
					});
				}
			} catch (Exception e) {
				Log.debug("Relay react failed: {}", e.toString());
			}
		});
	}

	/** Changes the text of one of your messages. */
	public void edit(final String key, final String messageId, String text) {
		final String content = text == null ? "" : text.trim();
		final Model.Conversation c = conversation(key);
		if (ticket() == null || content.isEmpty() || messageId == null || messageId.startsWith("local-")) {
			return;
		}
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("content", content);
				JsonObject res = postJson(actionPath(c, messageId, "edit"), body);
				final Model.Message updated = parseMessage(res.getAsJsonObject("message"), c);
				change(c, messageId, m -> updated);
			} catch (Exception e) {
				c.error = "Could not edit that message.";
			}
		});
	}

	/** Deletes a message (yours, or anyone's when you are a group admin). It stays as "This message was deleted". */
	public void delete(final String key, final String messageId) {
		final Model.Conversation c = conversation(key);
		if (ticket() == null || messageId == null) {
			return;
		}
		if (messageId.startsWith("local-")) { // never sent: just drop it
			synchronized (c) {
				List<Model.Message> next = new ArrayList<Model.Message>();
				for (Model.Message m : c.messages) {
					if (!m.id.equals(messageId)) {
						next.add(m);
					}
				}
				c.messages = next;
			}
			return;
		}
		pool.execute(() -> {
			try {
				JsonObject res = postJson(actionPath(c, messageId, "delete"), new JsonObject());
				final Model.Message updated = parseMessage(res.getAsJsonObject("message"), c);
				change(c, messageId, m -> updated);
			} catch (Exception e) {
				c.error = "Could not delete that message.";
			}
		});
	}

	/** Tells the other side we are typing, at most every 3 s. */
	public void typingPing(final String key) {
		long now = System.currentTimeMillis();
		if (ticket() == null || now - lastTypingSent < 3000) {
			return;
		}
		lastTypingSent = now;
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("isTyping", true);
				postJson("/v1/social/relay/" + (key.startsWith("g:") ? "groups/" + enc(key.substring(2)) : "dm/" + enc(key.substring(3))) + "/typing", body);
			} catch (Exception ignored) {
				// cosmetic
			}
		});
	}

	/** Pins / unpins a chat (synced with the launcher). */
	public void setPinned(final String key, final boolean pinned) {
		if (key.startsWith("g:")) {
			List<Model.Group> next = new ArrayList<Model.Group>();
			for (Model.Group g : groups) {
				if (g.id.equals(key.substring(2))) {
					g = g.withUnread(g.unread);
					g.pinned = pinned;
				}
				next.add(g);
			}
			sortGroups(next);
			groups = next;
		} else {
			List<Model.Friend> next = new ArrayList<Model.Friend>();
			for (Model.Friend f : friends) {
				next.add(f.id.equals(key.substring(3)) ? f.withPrefs(f.nickname, pinned) : f);
			}
			sortFriends(next);
			friends = next;
		}
		JsonObject body = new JsonObject();
		body.addProperty("pinned", pinned);
		prefs(key, body);
	}

	/** Sets (or, when empty, removes) the nickname of a friend. */
	public void setNickname(final String friendId, String nickname) {
		final String nick = nickname == null || nickname.trim().isEmpty() ? null : nickname.trim();
		List<Model.Friend> next = new ArrayList<Model.Friend>();
		for (Model.Friend f : friends) {
			next.add(f.id.equals(friendId) ? f.withPrefs(nick, f.pinned) : f);
		}
		sortFriends(next);
		friends = next;
		JsonObject body = new JsonObject();
		body.addProperty("nickname", nick == null ? "" : nick);
		prefs("dm:" + friendId, body);
	}

	private void prefs(final String key, final JsonObject body) {
		if (ticket() == null) {
			return;
		}
		pool.execute(() -> {
			try {
				postJson("/v1/social/relay/" + (key.startsWith("g:") ? "groups/" + enc(key.substring(2)) : "dm/" + enc(key.substring(3))) + "/prefs", body);
			} catch (Exception e) {
				Log.debug("Relay prefs failed: {}", e.toString());
			}
		});
	}

	// ── "Delete chat": hides the history up to now on this device (the launcher's Clear chat does the same) ──

	private volatile java.util.Map<String, Long> cleared;

	private java.nio.file.Path clearedFile() {
		java.nio.file.Path dir = xyz.nativelaunch.core.NativeState.get().gameDir();
		return dir == null ? null : dir.resolve("config").resolve("native-relay-cleared.json");
	}

	private synchronized java.util.Map<String, Long> clearedMap() {
		if (cleared == null) {
			java.util.Map<String, Long> m = new ConcurrentHashMap<String, Long>();
			try {
				java.nio.file.Path f = clearedFile();
				if (f != null && java.nio.file.Files.exists(f)) {
					JsonObject o = new JsonParser().parse(new String(java.nio.file.Files.readAllBytes(f), StandardCharsets.UTF_8)).getAsJsonObject();
					for (java.util.Map.Entry<String, JsonElement> e : o.entrySet()) {
						m.put(e.getKey(), e.getValue().getAsLong());
					}
				}
			} catch (Exception ignored) {
				// no saved state: nothing is hidden
			}
			cleared = m;
		}
		return cleared;
	}

	/** Messages at or before this time are hidden in the chat (0 = none). */
	public long clearedAt(String key) {
		Long t = clearedMap().get(meId + "|" + key);
		return t == null ? 0 : t;
	}

	// ── friends: requests, search, remove, block, profiles ─────────────────

	public void loadRequests() {
		if (ticket() == null) {
			return;
		}
		try {
			JsonObject o = getJson("/v1/social/requests").getAsJsonObject("requests");
			received = requestList(o == null ? null : o.getAsJsonArray("received"));
			sent = requestList(o == null ? null : o.getAsJsonArray("sent"));
			requests = received.size();
			requestsLoaded = true;
		} catch (Exception e) {
			Log.debug("Relay requests failed: {}", e.toString());
		}
	}

	/** A toast for a friend request someone sent you, or one they accepted. */
	private void requestNotice(String action, String actor, String actorName, String receiver) {
		if (actor == null || actor.equals(meId) || actorName == null) {
			return;
		}
		if ("sent".equals(action) && receiver != null && receiver.equals(meId)) {
			String skin = null;
			for (Model.Request r : received) {
				if (actor.equals(r.userId)) {
					skin = r.skin;
				}
			}
			notices.add(new Model.Notice(REQUESTS_KEY, actorName, "Sent you a friend request", actorName, skin));
		} else if ("accepted".equals(action)) {
			Model.Friend f = friend(actor);
			notices.add(new Model.Notice("dm:" + actor, f == null ? actorName : f.display(), "Accepted your friend request", actorName, f == null ? null : f.skin));
		}
	}

	/** Toast key that opens Relay on the Pending friend requests. */
	public static final String REQUESTS_KEY = "home:pending";

	public void loadBlocked() {
		if (ticket() == null) {
			return;
		}
		try {
			JsonArray arr = getJson("/v1/social/blocked").getAsJsonArray("blocked");
			List<Model.Person> out = new ArrayList<Model.Person>();
			if (arr != null) {
				for (JsonElement e : arr) {
					JsonObject u = e.getAsJsonObject();
					String id = str(u, "id") != null ? str(u, "id") : str(u, "userId");
					out.add(new Model.Person(id, str(u, "name") != null ? str(u, "name") : str(u, "username"), hashOf(str(u, "skinUrl"))));
				}
			}
			blocked = Collections.unmodifiableList(out);
			blockedLoaded = true;
		} catch (Exception e) {
			Log.debug("Relay blocked list failed: {}", e.toString());
		}
	}

	public void unblock(final String userId) {
		if (userId == null || ticket() == null) {
			return;
		}
		List<Model.Person> next = new ArrayList<Model.Person>(blocked);
		next.removeIf(b -> userId.equals(b.id));
		blocked = Collections.unmodifiableList(next);
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("targetId", userId);
				postJson("/v1/social/unblock", body);
			} catch (Exception e) {
				note(errorText(e, "Could not unblock that player."), true);
			}
			loadBlocked();
		});
	}

	private static List<Model.Request> requestList(JsonArray arr) {
		List<Model.Request> out = new ArrayList<Model.Request>();
		if (arr != null) {
			for (JsonElement e : arr) {
				JsonObject r = e.getAsJsonObject();
				out.add(new Model.Request(str(r, "id"), str(r, "userId"), str(r, "name"), hashOf(str(r, "skinUrl")), lng(r, "createdAt")));
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** Players whose Native name contains {@code query} (2+ characters). */
	public void search(final String query) {
		final String q = query == null ? "" : query.trim();
		if (q.length() < 2 || ticket() == null) {
			found = Collections.emptyList();
			foundFor = q;
			return;
		}
		if (q.equals(foundFor) || searching) {
			return;
		}
		searching = true;
		foundFor = q;
		pool.execute(() -> {
			try {
				JsonObject o = getJson("/v1/social/search?q=" + enc(q));
				List<Model.Person> out = new ArrayList<Model.Person>();
				JsonArray arr = o.getAsJsonArray("results");
				if (arr != null) {
					for (JsonElement e : arr) {
						JsonObject u = e.getAsJsonObject();
						out.add(new Model.Person(str(u, "id"), str(u, "name") != null ? str(u, "name") : str(u, "username"), hashOf(str(u, "skinUrl"))));
					}
				}
				if (q.equals(foundFor)) {
					found = Collections.unmodifiableList(out);
				}
			} catch (Exception e) {
				Log.debug("Relay search failed: {}", e.toString());
			} finally {
				searching = false;
			}
		});
	}

	public void sendRequest(final String username) {
		final String name = username == null ? "" : username.trim();
		if (name.isEmpty() || ticket() == null) {
			return;
		}
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("username", name);
				JsonObject res = postJson("/v1/social/requests/send", body);
				boolean mutual = res.has("mutual") && res.get("mutual").getAsBoolean();
				note(mutual ? "You and " + name + " are now friends." : "Friend request sent to " + name + ".", false);
			} catch (Exception e) {
				note(errorText(e, "Could not send the request."), true);
			}
			loadRequests();
			loadFriends();
		});
	}

	/** @param action accept | decline | cancel */
	public void respond(final String requestId, final String action) {
		if (requestId == null || ticket() == null) {
			return;
		}
		// optimistic: drop it from the list straight away
		List<Model.Request> r = new ArrayList<Model.Request>(received);
		List<Model.Request> s = new ArrayList<Model.Request>(sent);
		r.removeIf(x -> requestId.equals(x.id));
		s.removeIf(x -> requestId.equals(x.id));
		received = Collections.unmodifiableList(r);
		sent = Collections.unmodifiableList(s);
		requests = received.size();
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("requestId", requestId);
				body.addProperty("action", action);
				postJson("/v1/social/requests/respond", body);
			} catch (Exception e) {
				note(errorText(e, "That didn't work. Try again."), true);
			}
			loadRequests();
			loadFriends();
		});
	}

	public void removeFriend(final String friendId) {
		List<Model.Friend> next = new ArrayList<Model.Friend>(friends);
		next.removeIf(f -> f.id.equals(friendId));
		friends = next;
		pool.execute(() -> {
			try {
				HttpURLConnection con = open("/v1/social/friends/" + enc(friendId), "DELETE");
				try {
					read(con);
				} finally {
					con.disconnect();
				}
			} catch (Exception e) {
				note(errorText(e, "Could not remove that friend."), true);
			}
			loadFriends();
		});
	}

	public void block(final String userId) {
		List<Model.Friend> next = new ArrayList<Model.Friend>(friends);
		next.removeIf(f -> f.id.equals(userId));
		friends = next;
		pool.execute(() -> {
			try {
				JsonObject body = new JsonObject();
				body.addProperty("targetId", userId);
				postJson("/v1/social/block", body);
				note("Blocked. They can't message you or send requests.", false);
			} catch (Exception e) {
				note(errorText(e, "Could not block that player."), true);
			}
			loadFriends();
			loadRequests();
			loadBlocked();
		});
	}

	/** The public profile for the side panel; null while it loads (fetched once per name). */
	public Model.Profile profile(final String name) {
		if (name == null) {
			return null;
		}
		final String key = name.toLowerCase(java.util.Locale.ROOT);
		Model.Profile p = profiles.get(key);
		if (p == null && profileLoading.add(key)) {
			pool.execute(() -> {
				try {
					JsonObject o = getJson("/v1/profiles/" + enc(name)).getAsJsonObject("profile");
					List<String> badges = new ArrayList<String>();
					JsonArray b = o.has("badges") && o.get("badges").isJsonArray() ? o.getAsJsonArray("badges") : null;
					if (b != null) {
						for (JsonElement e : b) {
							badges.add(e.isJsonObject() ? str(e.getAsJsonObject(), "name") : e.getAsString());
						}
					}
					int friendCount = 0;
					if (o.has("friends") && o.get("friends").isJsonArray()) {
						friendCount = o.getAsJsonArray("friends").size();
					} else if (o.has("friends") && o.get("friends").isJsonPrimitive()) {
						friendCount = o.get("friends").getAsInt();
					} else if (o.has("friends") && o.get("friends").isJsonObject() && o.getAsJsonObject("friends").has("count")) {
						friendCount = o.getAsJsonObject("friends").get("count").getAsInt();
					}
					profiles.put(key, new Model.Profile(str(o, "name"), str(o, "bio"), badges, lng(o, "joinedAt"), friendCount, bool(o, "premium")));
				} catch (Exception e) {
					profiles.put(key, new Model.Profile(name, null, Collections.<String>emptyList(), 0, 0, false));
				} finally {
					profileLoading.remove(key);
				}
			});
		}
		return p;
	}

	private void note(String text, boolean error) {
		notice = text;
		noticeError = error;
		noticeAt = System.currentTimeMillis();
	}

	public volatile long noticeAt;

	/** The server's own error message ("Already friends."), else the fallback. */
	private static String errorText(Exception e, String fallback) {
		String m = e.getMessage();
		return m != null && m.startsWith("HTTP ") ? fallback : (m == null || m.isEmpty() ? fallback : m);
	}

	public void clearChat(String key) {
		clearedMap().put(meId + "|" + key, System.currentTimeMillis() + 1000); // +1 s: tolerate small clock drift to the server
		try {
			JsonObject o = new JsonObject();
			for (java.util.Map.Entry<String, Long> e : clearedMap().entrySet()) {
				o.addProperty(e.getKey(), e.getValue());
			}
			java.nio.file.Path f = clearedFile();
			if (f != null) {
				java.nio.file.Files.createDirectories(f.getParent());
				java.nio.file.Files.write(f, o.toString().getBytes(StandardCharsets.UTF_8));
			}
		} catch (Exception ignored) {
			// still hidden for this session
		}
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
					String fid = str(f, "id");
					int unread = (viewing != null && viewing.equals("dm:" + fid)) ? 0 : num(f, "unread");
					list.add(new Model.Friend(fid, str(f, "name"), str(f, "nickname"), str(f, "status"), str(f, "activity"),
							str(f, "serverAddress"), skinRef(str(f, "skin"), str(f, "mcUuid")), bool(f, "online"), unread, bool(f, "pinned"), lng(f, "lastSeen"), Math.max(lng(f, "lastMessageAt"), lastMsgOf(fid))));
				}
			}
			sortFriends(list);
			friends = list;
			JsonObject req = o.getAsJsonObject("requests");
			requests = req != null && req.has("received") ? req.getAsJsonArray("received").size() : 0;
			friendsLoaded = true;
			state = State.ONLINE;
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				prefetch("dm:" + list.get(i).id);
			}
		} catch (Exception e) {
			Log.debug("Relay friends failed: {}", e.toString());
			if (!friendsLoaded) {
				state = State.OFFLINE;
			}
		}
	}

	/** The newest message time we already know for a friend (kept when the list is fetched again). */
	private long lastMsgOf(String id) {
		for (Model.Friend f : friends) {
			if (f.id.equals(id)) {
				return f.lastMsg;
			}
		}
		return 0L;
	}

	/** Pinned first, then the group with the newest message. */
	private static void sortGroups(List<Model.Group> list) {
		Collections.sort(list, (a, b) -> a.pinned != b.pinned ? (a.pinned ? -1 : 1) : Long.compare(b.lastAt, a.lastAt));
	}

	/** A message was just sent or received in this chat: it moves to the top of the list (below pinned ones). */
	private void bump(String key, long at) {
		if (key.startsWith("g:")) {
			String gid = key.substring(2);
			List<Model.Group> next = new ArrayList<Model.Group>();
			for (Model.Group g : groups) {
				next.add(g.id.equals(gid) && g.lastAt < at ? g.withLast(g.lastText, g.lastSender, at, g.unread) : g);
			}
			sortGroups(next);
			groups = next;
		} else if (key.startsWith("dm:")) {
			String fid = key.substring(3);
			List<Model.Friend> next = new ArrayList<Model.Friend>();
			for (Model.Friend f : friends) {
				next.add(f.id.equals(fid) ? f.withLastMsg(at) : f);
			}
			sortFriends(next);
			friends = next;
		}
	}

	private static void sortFriends(List<Model.Friend> list) {
		Collections.sort(list, (a, b) -> {
			if (a.pinned != b.pinned) {
				return a.pinned ? -1 : 1;
			}
			if (a.lastMsg != b.lastMsg) {
				return a.lastMsg > b.lastMsg ? -1 : 1; // the chat with the newest message first
			}
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
					Model.Group g = parseGroup(e.getAsJsonObject());
					if (viewing != null && viewing.equals("g:" + g.id)) {
						g = g.withUnread(0);
					}
					list.add(g);
				}
			}
			sortGroups(list);
			groups = list;
			groupsLoaded = true;
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				prefetch("g:" + list.get(i).id);
			}
		} catch (Exception e) {
			Log.debug("Relay groups failed: {}", e.toString());
		}
	}

	/** GIF search through the Native API (Giphy proxy). Blocking. Each entry: {url, preview, title}. */
	public List<String[]> gifs(String query) throws IOException {
		JsonObject o = getJson("/v1/social/relay/gifs?limit=30&q=" + enc(query == null ? "" : query.trim()));
		List<String[]> out = new ArrayList<String[]>();
		JsonArray arr = o.getAsJsonArray("gifs");
		if (arr != null) {
			for (JsonElement e : arr) {
				JsonObject g = e.getAsJsonObject();
				String url = str(g, "url"), preview = str(g, "preview");
				if (url != null) {
					out.add(new String[] {url, preview == null ? url : preview, str(g, "title")});
				}
			}
		}
		return out;
	}

	/** Warms a chat's latest page in the background (doesn't mark it read) so it opens instantly. */
	private void prefetch(final String key) {
		final Model.Conversation c = conversation(key);
		if (c.loaded || c.loading || ticket() == null) {
			return;
		}
		pool.execute(() -> {
			if (c.loaded || c.loading) {
				return;
			}
			try {
				JsonObject page = getJson(messagesPath(c) + "?limit=50&markRead=0");
				List<Model.Message> latest = parseMessages(page.getAsJsonArray("messages"), c);
				synchronized (c) {
					if (c.loaded) {
						return;
					}
					c.messages = mergeLatest(c.messages, latest);
					c.hasMore = page.has("hasMore") && page.get("hasMore").getAsBoolean();
					c.loaded = true;
				}
			} catch (Exception ignored) {
				// opening the chat loads it normally
			}
		});
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
		Model.Group group = new Model.Group(str(g, "id"), str(g, "name"), last == null ? null : str(last, "content"), last == null ? null : str(last, "senderName"),
				last == null ? 0 : lng(last, "createdAt"), num(g, "unreadCount"), num(g, "memberCount"), members);
		if (str(g, "role") != null) {
			group.role = str(g, "role");
		}
		group.pinned = bool(g, "pinned");
		return group;
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
		String mediaUrl = deleted ? null : str(m, "mediaUrl");
		String mediaName = str(m, "mediaName");
		String kind = str(m, "mediaKind");
		String lowerUrl = mediaUrl == null ? "" : mediaUrl.toLowerCase(Locale.ROOT);
		boolean isImgExt = lowerUrl.matches(".*\\.(png|jpe?g|gif|webp)(\\?.*)?$");
		boolean image = mediaUrl != null && (kind == null || "image".equals(kind) || isImgExt) && ChatMedia.allowed(mediaUrl, api);
		if ((content == null || content.isEmpty()) && !deleted) {
			content = mediaName != null ? "[" + mediaName + "]" : (mediaUrl != null || bool(m, "isMedia") ? "[attachment]" : "");
		}
		Model.Message out = new Model.Message(str(m, "id"), sender, nameOf(sender, str(m, "senderName")), deleted ? "Message deleted" : content,
				lng(m, "createdAt"), false, false, bool(m, "isSystem"), deleted, (image || mediaUrl != null) ? mediaUrl : null, mediaName, null);
		out.edited = !deleted && lng(m, "editedAt") > 0;
		out.read = bool(m, "isRead");
		if (!deleted && m.has("reactions") && m.get("reactions").isJsonArray()) {
			out.reactions = parseReactions(m.getAsJsonArray("reactions"));
		}
		if (m.has("reply") && m.get("reply").isJsonObject()) {
			JsonObject r = m.getAsJsonObject("reply");
			boolean gone = bool(r, "deleted");
			String rc = gone ? "" : str(r, "content");
			boolean media = !gone && (str(r, "mediaName") != null || str(r, "mediaUrl") != null);
			out.reply = new Model.Reply(str(r, "id"), nameOf(str(r, "senderId"), str(r, "senderName")), rc, media, gone);
		}
		return out;
	}

	private static List<String[]> parseReactions(JsonArray arr) {
		List<String[]> out = new ArrayList<String[]>();
		for (JsonElement e : arr) {
			if (e.isJsonObject() && str(e.getAsJsonObject(), "reaction") != null) {
				out.add(new String[] {str(e.getAsJsonObject(), "userId"), str(e.getAsJsonObject(), "reaction")});
			}
		}
		return out;
	}

	// ── live stream ───────────────────────────────────────────────────────

	private void streamLoop() {
		long backoff = 2000;
		boolean loaded = false;
		while (true) {
			// Not signed in (guest, checking, expired): wait for the session instead of hammering Native.
			if (!Session.signedIn()) {
				if (ticket() == null) {
					state = State.NO_ACCOUNT;
				}
				loaded = false;
				try {
					Thread.sleep(1500);
				} catch (InterruptedException e) {
					return;
				}
				continue;
			}
			if (!loaded) {
				loaded = true;
				if (state == State.NO_ACCOUNT) {
					state = State.CONNECTING;
				}
				pool.execute(this::loadFriends);
				pool.execute(this::loadGroups);
				pool.execute(this::loadRequests);
				pool.execute(this::loadBlocked);
			}
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
			pool.execute(this::loadFriends);
		}
	}

	private void stream() throws IOException {
		HttpURLConnection con = open("/v1/mod/stream?relay=1&activity=" + enc("Playing Minecraft"), "GET");
		con.setReadTimeout(70_000);
		con.setRequestProperty("Accept", "text/event-stream");
		int status = con.getResponseCode();
		if (status == 401) {
			state = State.NO_ACCOUNT;
			Session.rejected(Session.ticket());
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
				xyz.nativelaunch.voice.Voice.refreshCalls();
				for (Model.Conversation c : conversations.values()) {
					if (c.loaded && c.key.equals(viewing)) {
						open(c.key);
					}
				}
				break;
			case "voice:state":
				xyz.nativelaunch.voice.Voice.onState(p);
				break;
			case "presence": {
				String id = str(p, "userId");
				List<Model.Friend> next = new ArrayList<Model.Friend>();
				for (Model.Friend f : friends) {
					next.add(f.id.equals(id) ? f.withPresence(str(p, "status"), str(p, "activity"), str(p, "serverAddress"), lng(p, "lastSeen")) : f);
				}
				sortFriends(next);
				friends = next;
				break;
			}
			case "friends:changed":
			case "request:changed": {
				final String action = str(p, "action"), actor = str(p, "actorId"), actorName = str(p, "actorName"), receiver = str(p, "receiverId");
				pool.execute(() -> {
					loadRequests();
					loadFriends();
					requestNotice(action, actor, actorName, receiver);
				});
				break;
			}
			case "blocks:changed":
				pool.execute(this::loadBlocked);
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
			case "group:message:reaction": {
				final List<String[]> list = p.has("reactions") && p.get("reactions").isJsonArray() ? parseReactions(p.getAsJsonArray("reactions")) : new ArrayList<String[]>();
				change(conversation("g:" + str(p, "groupId")), str(p, "messageId"), m -> {
					Model.Message n = Model.withState(m, m.pending, m.failed);
					n.reactions = list;
					return n;
				});
				break;
			}
			case "message:read": {
				final java.util.Set<String> ids = new java.util.HashSet<String>();
				if (p.has("messageIds") && p.get("messageIds").isJsonArray()) {
					for (JsonElement e : p.getAsJsonArray("messageIds")) {
						ids.add(e.getAsString());
					}
				}
				for (Model.Conversation c : conversations.values()) {
					if (!c.isGroup()) {
						for (String id : ids) {
							change(c, id, m -> {
								Model.Message n = Model.withState(m, m.pending, m.failed);
								n.read = true;
								return n;
							});
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
					// a pending copy of our own message is replaced by the real one, in place (keeps its local picture)
					boolean replaced = false;
					boolean media = msg.mediaUrl != null;
					for (Model.Message x : c.messages) {
						if (!replaced && mine && x.pending && x.content.equals(msg.content) && media == (x.mediaUrl != null || x.localKey != null)) {
							replaced = true;
							next.add(Model.withLocal(msg, x.localKey));
							continue;
						}
						next.add(x);
					}
					if (!replaced) {
						next.add(msg);
					}
					c.messages = next;
				}
			}
		}
		if (mine) {
			bump(key, msg.createdAt);
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
					next.add(hit);
				} else {
					next.add(g);
				}
			}
			sortGroups(next);
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
					hit = (watching ? f.withUnread(0) : f.withUnread(f.unread + 1)).withLastMsg(msg.createdAt);
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
		if (ticket() == null) {
			throw new IOException("not signed in");
		}
		HttpURLConnection con = (HttpURLConnection) new URL(api + path).openConnection();
		con.setRequestMethod(method);
		con.setConnectTimeout(8000);
		con.setReadTimeout(15000);
		con.setRequestProperty("User-Agent", "NativeMod-Relay (Minecraft)");
		con.setRequestProperty("Authorization", "Bearer " + ticket());
		return con;
	}

	/** Authenticated call to another Native API (voice). Blocking. */
	public JsonObject apiPost(String path, JsonObject body) throws IOException {
		return postJson(path, body);
	}

	public JsonObject getApi(String path) throws IOException {
		return getJson(path);
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
		// Only the core game endpoints can say the ticket itself is bad. A 401 from the friend endpoints
		// (/v1/social/requests, search, ...) means "not allowed here" (e.g. an older server) and must not lock Relay.
		String path = con.getURL().getPath();
		if (status == 401 && (path.startsWith("/v1/mod/") || path.startsWith("/v1/social/relay/"))) {
			Session.rejected(Session.ticket());
		}
		if (status / 100 != 2) {
			String message = null;
			try (InputStream err = con.getErrorStream()) {
				if (err != null && status / 100 == 4) {
					ByteArrayOutputStream eo = new ByteArrayOutputStream();
					byte[] eb = new byte[4096];
					int en;
					while ((en = err.read(eb)) > 0 && eo.size() < 16 * 1024) {
						eo.write(eb, 0, en);
					}
					JsonObject e = new JsonParser().parse(new String(eo.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
					message = str(e, "error");
				}
			} catch (Exception ignored) {
				// no readable error body
			}
			throw new IOException(message != null ? message : "HTTP " + status);
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
	/** ".../csl/textures/<sha256>" -> "<sha256>" (the skin reference avatars use), else null. */
	static String hashOf(String url) {
		if (url == null) {
			return null;
		}
		String h = url.substring(url.lastIndexOf('/') + 1);
		return h.matches("[a-f0-9]{64}") ? h : null;
	}

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
