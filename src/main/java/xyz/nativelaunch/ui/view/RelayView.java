package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Ads;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.ChatMedia;
import xyz.nativelaunch.ui.ClipboardImage;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.McBridge;
import xyz.nativelaunch.ui.Scroll;
import xyz.nativelaunch.ui.TextField;
import xyz.nativelaunch.ui.TextLayout;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiConfig;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

/** The in-game Relay chat: friends and groups on the left, the conversation on the right (like the launcher). */
public final class RelayView {
	private static volatile String pendingSelect;
	private String selected;
	private int tab; // 0 chats, 1 groups
	/** Friends home (no chat open): 0 online, 1 all, 2 pending, 3 blocked, 4 add friend. */
	private int homeTab;
	private final java.util.Set<String> collapsed = new java.util.HashSet<String>();
	private final TextField addField = new TextField("relay:add");
	private final Scroll homeScroll = new Scroll();
	private final Scroll profileScroll = new Scroll();
	/** The friend profile panel on the right of a chat. */
	private boolean profileOpen = true;
	/** Two-click confirm for remove / block: which action is armed and since when. */
	private String armed;
	private long armedAt;
	private final TextField search = new TextField("relay:search");
	private final TextField input = new TextField("relay:input");
	private final Scroll listScroll = new Scroll();
	private final Map<String, Scroll> chatScrolls = new HashMap<String, Scroll>();
	private final Map<String, String> drafts = new HashMap<String, String>();
	private final Map<String, String> firstIds = new HashMap<String, String>();
	/** The top visible message last frame and its content-space y: kept in place while rows above it change. */
	private String anchorConv, anchorId, nextAnchorId;
	private float anchorY, nextAnchorY, anchorNow = Float.NaN;
	private float[] jumpRect;
	private Object lastScreen;
	private boolean settingsOpen, bindingKey;
	private int lastCount = -1;
	// reply / edit / react (the launcher's message actions)
	private Model.Message replyTo;
	private String editingId, reactFor, jumpTo, flashId;
	private int jumpTries;
	private long flashAt;
	private float jumpContentY = Float.NaN;
	private float[] pickerRect, toolbarRect;
	private final float[] pickRow = new float[4];
	private boolean pickRowSet;
	private final Map<String, xyz.nativelaunch.ui.gfx.Image> emojiImages = new HashMap<String, xyz.nativelaunch.ui.gfx.Image>();
	private static final String[] PALETTE = {"\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDD25", "\uD83D\uDC4D", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83C\uDF89", "\uD83D\uDC80"};
	// right-click menu on a chat row, nickname dialog
	private String menuKey, nickFor;
	private float menuX, menuY;
	private boolean menuFresh;
	private final TextField nick = new TextField("relay:nick");
	private boolean rowTyping, rowPinned;
	// screenshot picker (the attach button)
	private boolean shotsOpen;
	private volatile List<java.io.File> shots;
	private final Map<String, xyz.nativelaunch.ui.gfx.Image> shotThumbs = new java.util.concurrent.ConcurrentHashMap<String, xyz.nativelaunch.ui.gfx.Image>();
	// pasted picture waiting to be sent
	private byte[] staged;
	private String stagedMime, stagedKey;
	private volatile String stagedNote;
	private final AtomicBoolean pasting = new AtomicBoolean();
	private volatile byte[] pasted;
	/** A file picker is open (one at a time). */
	private final java.util.concurrent.atomic.AtomicBoolean picking = new java.util.concurrent.atomic.AtomicBoolean();
	private ChatMedia.Media viewer;
	private String viewerUrl;
	// GIF picker
	private boolean gifOpen;
	private final TextField gifSearch = new TextField("relay:gifs");
	private final Scroll gifScroll = new Scroll();
	private volatile List<String[]> gifResults;
	private volatile boolean gifLoading, gifFailed;
	private String gifQuery;
	private long gifTyped;
	private int gifSerial;
	private final Map<String, Object[]> wrapCache = new HashMap<String, Object[]>();
	private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.ROOT);
	private final SimpleDateFormat shortDay = new SimpleDateFormat("d MMM", Locale.ENGLISH);
	private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);

	public RelayView() {
		input.maxLength = 2000;
		input.bare = true;
		search.bare = true;
		addField.bare = true;
		chatScrollsInit();
	}

	private void chatScrollsInit() {
	}

	/** The next time the chat opens it shows this conversation. */
	public static void selectNext(String key) {
		pendingSelect = key;
	}

	/** Esc: closes popovers / clears search first; false = close the chat. */
	public boolean consumeEscape(Ui ui) {
		if (nickFor != null) {
			nickFor = null;
			return true;
		}
		if (menuKey != null) {
			menuKey = null;
			return true;
		}
		if (shotsOpen) {
			shotsOpen = false;
			return true;
		}
		if (reactFor != null) {
			reactFor = null;
			return true;
		}
		if (editingId != null) {
			editingId = null;
			input.clear();
			return true;
		}
		if (replyTo != null) {
			replyTo = null;
			return true;
		}
		if (viewer != null) {
			viewer = null;
			viewerUrl = null;
			return true;
		}
		if (gifOpen) {
			gifOpen = false;
			return true;
		}
		if (staged != null) {
			staged = null;
			return true;
		}
		if (settingsOpen) {
			settingsOpen = false;
			bindingKey = false;
			return true;
		}
		return false;
	}

	public void draw(Ui ui, Object screen, boolean embedded) {
		Canvas c = ui.c;
		RelayClient client = RelayClient.get();
		if (screen != lastScreen) {
			lastScreen = screen;
			ui.setAnim("relay#in", 0);
			if (pendingSelect != null) {
				select(pendingSelect, client);
				pendingSelect = null;
			} else if (selected != null && client != null) {
				client.open(selected);
			}
			input.focus(ui);
		}
		float W = c.width(), H = c.height();
		float in = ui.anim("relay#in", 1f, 9f);
		boolean overTitle = !UiRuntime.mc().inWorld();
		c.fill(0, 0, W, H, Theme.alpha(overTitle ? 0xB8000000 : 0x8C000000, in));
		float pw = Math.min(1080, W - 48), ph = Math.min(680, H - 48);
		float px = (W - pw) / 2, py = (H - ph) / 2 + (1 - in) * 18;
		c.pushAlpha(in);
		c.shadow(px, py + 12, pw, ph, 20, 44, 0xAA000000);
		c.round(px, py, pw, ph, 20, 0xFA0A0B0F);
		// the sidebar sits on a slightly darker plate, like the launcher
		float sbw = Math.min(300, pw * 0.34f);
		c.pushClip(px, py, sbw, ph);
		c.round(px, py, sbw + 24, ph, 20, 0xFF07080B);
		c.popClip();
		c.outline(px, py, pw, ph, 20, 1, Theme.HAIRLINE_STRONG);

		if (bindingKey) {
			for (int[] k : ui.keys) {
				if (k[0] != Ui.KEY_ESCAPE) {
					UiRuntime.setRelayKey(k[0]);
				}
				bindingKey = false;
				ui.keys.clear();
				break;
			}
		}
		ui.interactive = !settingsOpen && menuKey == null && nickFor == null;
		float sw = Math.min(300, pw * 0.34f);
		if (SessionLock.locked()) {
			// Not signed in / sign-in expired: lock the whole page instead of showing stale chats.
			wordmark(c, px + 18, py + 19);
			SessionLock.draw(ui, "relay:lock", px, py + 48, pw, ph - 48);
		} else {
			sidebar(ui, client, px, py, sw, ph, screen);
			c.fill(px + sw, py, 1, ph, Theme.HAIRLINE);
			boolean showProfile = profileOpen && selected != null && selected.startsWith("dm:") && pw - sw > 640;
			float pp = showProfile ? Math.min(270, (pw - sw) * 0.36f) : 0;
			chat(ui, client, px + sw + 1, py, pw - sw - 1 - pp, ph, screen);
			if (pp > 0) {
				c.fill(px + pw - pp, py, 1, ph, Theme.HAIRLINE);
				profilePanel(ui, client, px + pw - pp + 1, py, pp - 1, ph, screen);
			}
		}
		ui.interactive = true;
		if (settingsOpen) {
			settings(ui, px, py, sw);
		}
		if (menuKey != null) {
			contextMenu(ui, client, W, H);
		}
		if (nickFor != null) {
			nicknameDialog(ui, client, W, H);
		}
		if (viewer != null) {
			lightbox(ui, W, H);
		}
		c.popAlpha();
	}

	private void select(String key, RelayClient client) {
		if (key == null) {
			return;
		}
		if (key.startsWith("home:")) {
			goHome(client, RelayClient.REQUESTS_KEY.equals(key) ? 2 : 0);
			return;
		}
		if (selected != null) {
			drafts.put(selected, input.value());
		}
		selected = key;
		replyTo = null;
		editingId = null;
		reactFor = null;
		jumpTo = null;
		String draft = drafts.get(key);
		input.set(draft == null ? "" : draft);
		lastCount = -1;
		staged = null;
		gifOpen = false;
		if (client != null) {
			client.open(key);
		}
		tab = key.startsWith("g:") ? 1 : 0;
	}

	// ── left column: the launcher's inbox ─────────────────────────────────

	private void goHome(RelayClient client, int homeTab) {
		if (selected != null) {
			drafts.put(selected, input.value());
		}
		selected = null;
		this.homeTab = homeTab;
		replyTo = null;
		editingId = null;
		reactFor = null;
		jumpTo = null;
		staged = null;
		gifOpen = false;
		shotsOpen = false;
		armed = null;
		if (client != null) {
			client.viewing = null;
		}
	}

	/** "RELAY" in the launcher's tracked wordmark; returns the x after the last letter. */
	private static float wordmark(Canvas c, float x, float y) {
		String s = "RELAY";
		for (int i = 0; i < s.length(); i++) {
			String ch = s.substring(i, i + 1);
			c.text(Fonts.BOLD, 17, ch, x, y, Theme.TEXT_STRONG);
			x += c.textWidth(Fonts.BOLD, 17, ch) + (i < s.length() - 1 ? 2.4f : 0);
		}
		return x;
	}

	private void sidebar(Ui ui, RelayClient client, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		float pad = 16;
		float wx = wordmark(c, x + pad + 2, y + 19);
		float dy = y + 19 + c.lineHeight(Fonts.BOLD, 17) / 2;
		RelayClient.State st = client == null ? RelayClient.State.OFFLINE : client.state;
		int live = st == RelayClient.State.ONLINE ? Theme.ONLINE : st == RelayClient.State.CONNECTING ? Theme.IDLE : 0xFF52525B;
		c.circle(wx + 8, dy, 3, live);
		if (ui.hover(wx + 2, dy - 8, 14, 16)) {
			ui.tip(st == RelayClient.State.ONLINE ? "Realtime connected" : st == RelayClient.State.CONNECTING ? "Connecting" : "Offline");
		}
		if (ui.iconButton("relay:settings", x + w - pad - 28, y + 15, 28, Theme.I_SETTINGS, "Chat settings")) {
			settingsOpen = !settingsOpen;
		}
		float hbx = x + w - pad - 28 - 32;
		if (selected == null) {
			c.round(hbx, y + 15, 28, 28, 7, Theme.SUBTLE_HOVER);
		}
		if (ui.iconButton("relay:home", hbx, y + 15, 28, Theme.I_USERS, "Friends")) {
			goHome(client, 0);
		}
		float ty = y + 56;
		// search: icon + bare field in one box
		float sf = ui.anim("relay:search#f", search.focused(ui), 14f);
		c.round(x + pad, ty, w - pad * 2, 34, 10, Theme.mix(0xFF0B0C10, 0xFF0E1014, sf));
		c.outline(x + pad, ty, w - pad * 2, 34, 10, 1, Theme.mix(Theme.HAIRLINE, 0x47FFFFFF, sf));
		c.icon(Theme.I_SEARCH, 13, x + pad + 15, ty + 17, Theme.TEXT_MUTED);
		search.draw(ui, x + pad + 18, ty, w - pad * 2 - 18, 34, "Search conversations", 12.5f);
		ty += 46;
		// Chats / Groups
		float tw = (w - pad * 2) / 2;
		c.round(x + pad, ty, w - pad * 2, 34, 10, 0xFF0C0D11);
		c.outline(x + pad, ty, w - pad * 2, 34, 10, 1, Theme.HAIRLINE);
		float tabPos = ui.anim("relay:tab", tab, 16f);
		c.shadow(x + pad + 3 + tabPos * tw, ty + 4, tw - 6, 28, 8, 6, 0x66000000);
		c.round(x + pad + 3 + tabPos * tw, ty + 3, tw - 6, 28, 8, 0xFF1F2027);
		int fu = 0, gu = 0;
		if (client != null) {
			for (Model.Friend f : client.friends) {
				fu += ("dm:" + f.id).equals(client.viewing) ? 0 : f.unread;
			}
			for (Model.Group g : client.groups) {
				gu += ("g:" + g.id).equals(client.viewing) ? 0 : g.unread;
			}
		}
		for (int i = 0; i < 2; i++) {
			String label = i == 0 ? "Chats" : "Groups";
			int n = i == 0 ? fu : gu;
			float bx = x + pad + i * tw;
			if (ui.clicked("relay:tab" + i, bx, ty, tw, 34)) {
				tab = i;
			}
			float lw = c.textWidth(Fonts.SEMIBOLD, 12f, label) + (n > 0 ? 24 : 0);
			int col = tab == i ? Theme.TEXT_STRONG : Theme.TEXT_MUTED;
			c.text(Fonts.SEMIBOLD, 12f, label, bx + (tw - lw) / 2, ty + 17 - c.lineHeight(Fonts.SEMIBOLD, 12f) / 2, col);
			if (n > 0) {
				TitleView.badge(c, bx + (tw + lw) / 2, ty + 17, n);
			}
		}
		ty += 46;
		c.fill(x, ty - 1, w, 1, Theme.HAIRLINE);
		ty += 8;
		float listH = y + h - ty - 8;
		if (client == null || SessionLock.locked()) {
			SessionLock.draw(ui, "relay:list:lock", x, ty, w, listH);
			return;
		}
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		float off = listScroll.begin(ui, x, ty, w, listH);
		float ry = ty - off;
		float rh = 52;
		if (tab == 0) {
			List<Model.Friend> pinned = new ArrayList<Model.Friend>(), direct = new ArrayList<Model.Friend>();
			for (Model.Friend f : client.friends) {
				String sn = snippet(client, "dm:" + f.id);
				if (!q.isEmpty() && !f.display().toLowerCase(Locale.ROOT).contains(q) && !f.name.toLowerCase(Locale.ROOT).contains(q)
						&& (sn == null || !sn.toLowerCase(Locale.ROOT).contains(q))) {
					continue;
				}
				(f.pinned ? pinned : direct).add(f);
			}
			if (!client.friendsLoaded) {
				ry = section(ui, "direct", "Direct messages", 0, x + 8, ry, w - 16);
				skeletonRows(ui, x + 8, ry, w - 16, 6);
			} else if (client.friends.isEmpty()) {
				emptyInbox(ui, client, x, ry, w, Math.min(listH, 260));
			} else {
				if (!pinned.isEmpty()) {
					ry = section(ui, "pinned", "Pinned", pinned.size(), x + 8, ry, w - 16);
					if (!collapsed.contains("pinned")) {
						for (Model.Friend f : pinned) {
							ry = friendRow(ui, client, f, x + 8, ry, w - 16, rh, ty, listH);
						}
					}
					ry += 6;
				}
				ry = section(ui, "direct", "Direct messages", direct.size(), x + 8, ry, w - 16);
				if (!collapsed.contains("direct")) {
					if (direct.isEmpty()) {
						c.text(Fonts.REGULAR, 11.5f, q.isEmpty() ? "No direct messages" : "No matching chats", x + 18, ry + 6, Theme.TEXT_MUTED);
						ry += 30;
					}
					for (Model.Friend f : direct) {
						ry = friendRow(ui, client, f, x + 8, ry, w - 16, rh, ty, listH);
					}
				}
			}
		} else {
			List<Model.Group> pinned = new ArrayList<Model.Group>(), rest = new ArrayList<Model.Group>();
			for (Model.Group g : client.groups) {
				if (!q.isEmpty() && (g.name == null || !g.name.toLowerCase(Locale.ROOT).contains(q))) {
					continue;
				}
				(g.pinned ? pinned : rest).add(g);
			}
			if (!client.groupsLoaded) {
				ry = section(ui, "groups", "Groups", 0, x + 8, ry, w - 16);
				skeletonRows(ui, x + 8, ry, w - 16, 3);
			} else {
				if (!pinned.isEmpty()) {
					ry = section(ui, "gpinned", "Pinned", pinned.size(), x + 8, ry, w - 16);
					if (!collapsed.contains("gpinned")) {
						for (Model.Group g : pinned) {
							ry = groupRow(ui, client, g, x + 8, ry, w - 16, rh, ty, listH);
						}
					}
					ry += 6;
				}
				ry = section(ui, "groups", "Groups", rest.size(), x + 8, ry, w - 16);
				if (!collapsed.contains("groups")) {
					if (rest.isEmpty()) {
						c.text(Fonts.REGULAR, 11.5f, q.isEmpty() ? "No groups yet. Create one in the launcher." : "No matching groups", x + 18, ry + 6, Theme.TEXT_MUTED);
						ry += 30;
					}
					for (Model.Group g : rest) {
						ry = groupRow(ui, client, g, x + 8, ry, w - 16, rh, ty, listH);
					}
				}
			}
		}
		listScroll.end(ui, ry + off - ty + 8);
	}

	/** Collapsible "v Direct messages ........ 3" header. */
	private float section(Ui ui, String id, String label, int count, float x, float y, float w) {
		Canvas c = ui.c;
		float sh = 26;
		boolean over = ui.hover(x, y, w, sh);
		if (ui.clicked("relay:sec:" + id, x, y, w, sh)) {
			if (!collapsed.remove(id)) {
				collapsed.add(id);
			}
		}
		int col = over ? Theme.TEXT_SECONDARY : Theme.TEXT_MUTED;
		caret(c, x + 12, y + sh / 2, !collapsed.contains(id), col);
		c.text(Fonts.SEMIBOLD, 12, label, x + 22, y + (sh - c.lineHeight(Fonts.SEMIBOLD, 12)) / 2, col);
		if (count > 0) {
			String n = String.valueOf(count);
			c.text(Fonts.REGULAR, 11, n, x + w - 8 - c.textWidth(Fonts.REGULAR, 11, n), y + (sh - c.lineHeight(Fonts.REGULAR, 11)) / 2, Theme.alpha(Theme.TEXT_MUTED, 0.75f));
		}
		return y + sh + 2;
	}

	/** A small solid caret (down when open, right when collapsed). */
	private static void caret(Canvas c, float cx, float cy, boolean open, int col) {
		for (int i = 0; i < 4; i++) {
			float len = 7 - i * 2;
			if (open) {
				c.fill(cx - len / 2, cy - 2 + i, len, 1, col);
			} else {
				c.fill(cx - 2 + i, cy - len / 2, 1, len, col);
			}
		}
	}

	private void emptyInbox(Ui ui, RelayClient client, float x, float y, float w, float h) {
		Canvas c = ui.c;
		TitleView.empty(c, x, y - 20, w, h, Theme.I_USERS, "Nothing here yet", "Add friends by their Native or Minecraft username.");
		float bw = 128;
		if (ui.button("relay:empty:add", x + (w - bw) / 2, y + h - 34, bw, 30, "Add friend", Theme.I_USER_PLUS, Ui.BTN_GLASS)) {
			goHome(client, 4);
			addField.focus(ui);
			ui.focusClaimed = true;
		}
	}

	/** The last message of a chat that was opened before ("You: hi", "Picture"), null when unknown. */
	private String snippet(RelayClient client, String key) {
		Model.Conversation cv = client.peek(key);
		List<Model.Message> ms = cv == null ? null : cv.messages;
		if (ms == null || ms.isEmpty()) {
			return null;
		}
		Model.Message m = ms.get(ms.size() - 1);
		if (m.createdAt <= client.clearedAt(key)) {
			return null;
		}
		String body;
		boolean media = m.mediaUrl != null || m.localKey != null;
		if (m.deleted) {
			body = "Message deleted";
		} else if (media && (m.content == null || m.content.trim().isEmpty() || m.content.startsWith("["))) {
			body = m.mediaName != null && !m.mediaName.isEmpty() ? m.mediaName : "Picture";
		} else {
			body = m.content == null ? "" : m.content.replace('\n', ' ');
		}
		return (eq(m.senderId, client.meId) ? "You: " : "") + body;
	}

	private Model.Message lastOf(RelayClient client, String key) {
		Model.Conversation cv = client.peek(key);
		List<Model.Message> ms = cv == null ? null : cv.messages;
		return ms == null || ms.isEmpty() ? null : ms.get(ms.size() - 1);
	}

	/** "18:44" today, "Yesterday", else "3 Jun". */
	private String shortTime(long t) {
		if (t <= 0) {
			return "";
		}
		long now = System.currentTimeMillis();
		if (sameDay(t, now)) {
			return timeFmt.format(new Date(t));
		}
		if (sameDay(t, now - 86_400_000L)) {
			return "Yesterday";
		}
		return shortDay.format(new Date(t));
	}

	private float friendRow(Ui ui, RelayClient client, Model.Friend f, float x, float ry, float w, float rh, float top, float listH) {
		String key = "dm:" + f.id;
		if (ry + rh >= top && ry <= top + listH) {
			String sn = snippet(client, key);
			Model.Message last = lastOf(client, key);
			rowTyping = !client.typing(key).isEmpty();
			rowPinned = f.pinned;
			rowDot = f.dot();
			rowTime = shortTime(last != null ? Math.max(last.createdAt, f.lastMsg) : f.lastMsg);
			rowCheck = sn != null && last != null && eq(last.senderId, client.meId) && !last.pending && !last.failed && !last.deleted ? (last.read ? 2 : 1) : 0;
			int unread = key.equals(client.viewing) ? 0 : f.unread;
			String sub = sn != null ? sn : f.lastMsg > 0 ? f.line() : "No messages";
			if (row(ui, client, key, x, ry, w, rh, f.display(), f.name, f.skin, sub, unread, f.online ? 1 : 0)) {
				select(key, client);
				input.focus(ui);
			}
		}
		return ry + rh + 2;
	}

	private float groupRow(Ui ui, RelayClient client, Model.Group g, float x, float ry, float w, float rh, float top, float listH) {
		String key = "g:" + g.id;
		if (ry + rh >= top && ry <= top + listH) {
			String who = g.lastSender == null ? "" : (g.lastSender.equals(client.meName) ? "You" : g.lastSender) + ": ";
			String sub = g.lastText == null ? g.members + " members" : who + (g.lastText.isEmpty() ? "Picture" : g.lastText);
			rowDot = 0;
			rowTyping = !client.typing(key).isEmpty();
			rowPinned = g.pinned;
			rowTime = shortTime(g.lastAt);
			rowCheck = 0;
			int unread = key.equals(client.viewing) ? 0 : g.unread;
			if (row(ui, client, key, x, ry, w, rh, g.name, g.name, null, sub, unread, -1)) {
				select(key, client);
				input.focus(ui);
			}
		}
		return ry + rh + 2;
	}

	private int rowDot, rowCheck;
	private String rowTime;

	/** One inbox row like the launcher's ThreadRow. presence: 1 online, 0 offline, -1 group (no dot) */
	private boolean row(Ui ui, RelayClient client, String key, float x, float y, float w, float h, String title, String avatarName, String skin, String sub, int unread, int presence) {
		Canvas c = ui.c;
		boolean sel = key.equals(selected);
		boolean over = ui.hover(x, y, w, h);
		boolean click = ui.clicked("row:" + key, x, y, w, h);
		if (over && ui.rightPressed) {
			menuKey = key;
			menuX = ui.mx;
			menuY = ui.my;
			menuFresh = true;
		}
		float hv = ui.anim("row:" + key, over, 14f);
		float sv = ui.anim("rowsel:" + key, sel, 14f);
		c.round(x, y, w, h, 10, Theme.mix(Theme.alpha(0x0DFFFFFF, hv), 0x10FFFFFF, sv));
		if (sv > 0.01f) {
			c.outline(x, y, w, h, 10, 1, Theme.alpha(Theme.HAIRLINE_STRONG, sv));
			c.round(x, y + 8 + (h - 16) * (1 - sv) / 2, 2, (h - 16) * sv, 1, Theme.alpha(Theme.WHITE, sv));
		}
		float av = 36;
		float ax = x + 10, ay = y + (h - av) / 2;
		int ring = sel ? 0xFF15161A : 0xFF07080B;
		if (presence == -1) {
			c.round(ax, ay, av, av, 8, Theme.mix(Theme.nameColor(title), 0xFF000000, 0.45f));
			c.icon(Theme.I_HASH, 17, ax + av / 2, ay + av / 2, 0xFFFFFFFF);
		} else {
			Avatars.draw(c, client.api(), avatarName, skin, ax, ay, av, 7);
			c.circle(ax + av - 1, ay + av - 1, 5.5f, ring);
			c.circle(ax + av - 1, ay + av - 1, 3.5f, rowDot != 0 ? rowDot : (presence == 1 ? Theme.ONLINE : 0xFF52525B));
		}
		float tx = ax + av + 10;
		float right = x + w - 10;
		float timeW = rowTime == null || rowTime.isEmpty() ? 0 : c.textWidth(Fonts.REGULAR, 10, rowTime);
		float topY = y + 9;
		float titleW = right - tx - timeW - 8 - (rowPinned ? 16 : 0);
		String shownTitle = c.ellipsize(Fonts.SEMIBOLD, 13, title, titleW);
		c.text(Fonts.SEMIBOLD, 13, shownTitle, tx, topY, sel ? Theme.TEXT_STRONG : Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, hv));
		if (rowPinned) {
			c.icon(Theme.I_MAP_PIN, 10, tx + c.textWidth(Fonts.SEMIBOLD, 13, shownTitle) + 10, topY + c.lineHeight(Fonts.SEMIBOLD, 13) / 2, Theme.TEXT_MUTED);
		}
		if (timeW > 0) {
			c.text(Fonts.REGULAR, 10, rowTime, right - timeW, topY + 2, unread > 0 ? Theme.TEXT_STRONG : Theme.TEXT_MUTED);
		}
		float by = y + 28;
		float subW = right - tx - (unread > 0 ? 26 : 0) - (rowCheck > 0 ? 16 : 0);
		if (rowTyping) {
			float tw2 = c.text(Fonts.MEDIUM, 11, "typing", tx, by, Theme.ONLINE);
			ui.dots(tx + tw2 + 14, by + c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.ONLINE);
		} else {
			String s = c.ellipsize(Fonts.REGULAR, 11, sub, subW);
			float sw2 = c.text(Fonts.REGULAR, 11, s, tx, by, unread > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
			if (rowCheck > 0) {
				c.icon(rowCheck == 2 ? Theme.I_CHECKS : Theme.I_CHECK, 11, tx + sw2 + 9, by + c.lineHeight(Fonts.REGULAR, 11) / 2, rowCheck == 2 ? 0xFF60A5FA : Theme.TEXT_MUTED);
			}
		}
		if (unread > 0) {
			TitleView.badge(c, right, by + c.lineHeight(Fonts.REGULAR, 11) / 2, unread);
		}
		return click;
	}

	// ── right column ──────────────────────────────────────────────────────

	private void chat(Ui ui, RelayClient client, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		if (ui.iconButton("relay:close", x + w - 46, y + 14, 32, Theme.I_X, "Close (Esc)")) {
			UiRuntime.closeHost(screen);
			return;
		}
		if (selected != null && selected.startsWith("dm:")
				&& ui.iconButton("relay:profile", x + w - 46 - 38, y + 14, 32, Theme.I_USER, profileOpen ? "Hide profile" : "Show profile")) {
			profileOpen = !profileOpen;
		}
		if (client == null) {
			TitleView.empty(c, x, y, w, h, Theme.I_MESSAGE, "Pick a chat", "Choose a friend or a group on the left. Press " + UiRuntime.keyName(UiRuntime.config().relayKey) + " in game to open Relay any time.");
			return;
		}
		if (selected == null) {
			friendsHome(ui, client, x, y, w, h, screen);
			return;
		}
		Model.Conversation conv = client.conversation(selected);
		boolean group = conv.isGroup();
		Model.Friend friend = group ? null : client.friend(conv.targetId());
		Model.Group grp = group ? client.group(conv.targetId()) : null;
		String name = group ? (grp == null ? "Group" : grp.name) : (friend == null ? "Friend" : friend.display());
		// header
		float hx = x + 20, hy = y + 14;
		if (group) {
			c.round(hx, hy, 36, 36, 10, Theme.mix(Theme.nameColor(name), 0xFF000000, 0.45f));
			c.icon(Theme.I_HASH, 17, hx + 18, hy + 18, 0xFFFFFFFF);
		} else {
			Avatars.draw(c, client.api(), friend == null ? name : friend.name, friend == null ? null : friend.skin, hx, hy, 36, 10);
		}
		c.text(Fonts.SEMIBOLD, 15, c.ellipsize(Fonts.SEMIBOLD, 15, name, w - 140), hx + 48, hy, Theme.TEXT_STRONG);
		String status;
		int statusColor = Theme.TEXT_MUTED;
		if (group) {
			status = grp == null ? "" : grp.members + " members";
		} else if (friend != null && friend.online) {
			status = friend.line();
			statusColor = Theme.mix(friend.dot(), 0xFFFFFFFF, 0.45f);
		} else {
			status = "Offline";
		}
		c.text(Fonts.REGULAR, 11.5f, c.ellipsize(Fonts.REGULAR, 11.5f, status, w - 140), hx + 48, hy + 20, statusColor);
		c.fill(x, y + 64, w, 1, Theme.HAIRLINE);

		// messages
		float inputH = 46;
		float barH = replyTo != null || editingId != null ? 36 : 0;
		float my0 = y + 65, mh = h - 65 - inputH - 34 - barH;
		Scroll sc = chatScrolls.get(selected);
		if (sc == null) {
			sc = new Scroll();
			sc.stickBottom = true;
			chatScrolls.put(selected, sc);
		}
		List<Model.Message> messages = conv.messages;
		long clearedAt = client.clearedAt(selected);
		if (clearedAt > 0 && !messages.isEmpty()) {
			List<Model.Message> kept = new ArrayList<Model.Message>();
			for (Model.Message m : messages) {
				if (m.createdAt > clearedAt) {
					kept.add(m);
				}
			}
			messages = kept;
		}
		float off = sc.begin(ui, x, my0, w, mh);
		float pad = 20;
		float textX = x + pad + 46;
		float textW = w - pad * 2 - 46 - 10;
		float cy = my0 + 14 - off;
		if (conv.hasMore) {
			if (conv.loading) {
				// older page on its way: one skeleton message in the space it already keeps (no jumping)
				shimmer(ui, x + pad, cy - 2, 22, 22, 7);
				shimmer(ui, x + pad + 32, cy + 1, 70, 8, 4);
				shimmer(ui, x + pad + 32, cy + 13, Math.min(w - pad * 2 - 40, 190), 7, 3.5f);
			} else if (firstIds.get(selected) != null && (sc.content <= mh || sc.offset < 30 && !sc.atBottom())) {
				// only when the reader scrolled up to the top: not while the chat is still opening (that loaded
				// page after page and the view jumped up and down)
				client.loadOlder(selected);
			}
			cy += 26;
		}
		float before = cy;
		if (!conv.loaded && conv.error == null) {
			skeletonMessages(ui, x + pad, my0 + 14, w - pad * 2, mh);
		} else if (conv.loaded && messages.isEmpty()) {
			TitleView.empty(c, x, my0, w, mh, Theme.I_SMILE, "Say hi to " + name, "This is the beginning of your conversation.");
		} else if (conv.error != null && messages.isEmpty()) {
			TitleView.empty(c, x, my0, w, mh, Theme.I_WIFI_OFF, "Couldn't load messages", conv.error);
		}
		Model.Message prev = null;
		float lh = 19;
		nextAnchorId = null;
		anchorNow = Float.NaN;
		pickRowSet = false;
		jumpContentY = Float.NaN;
		boolean canModerate = group && grp != null && grp.canModerate();
		boolean pickerHover = pickerRect != null && ui.hover(pickerRect[0], pickerRect[1], pickerRect[2], pickerRect[3]);
		for (Model.Message m : messages) {
			if (jumpTo != null && jumpTo.equals(m.id)) {
				jumpContentY = cy + off;
			}
			if (m.id != null) {
				if (m.id.equals(anchorId)) {
					anchorNow = cy + off; // where the anchor message starts now, in content space
				}
				if (nextAnchorId == null && cy >= my0) {
					nextAnchorId = m.id;
					nextAnchorY = cy + off;
				}
			}
			boolean newDay = prev == null || !sameDay(prev.createdAt, m.createdAt);
			if (newDay) {
				String day = dayLabel(m.createdAt);
				float dw = c.textWidth(Fonts.SEMIBOLD, 10.5f, day);
				if (visible(cy, 30, my0, mh)) {
					c.fill(x + pad, cy + 12, (w - pad * 2 - dw) / 2 - 10, 1, Theme.HAIRLINE);
					c.fill(x + (w + dw) / 2 + 10, cy + 12, (w - pad * 2 - dw) / 2 - 10, 1, Theme.HAIRLINE);
					c.text(Fonts.SEMIBOLD, 10.5f, day, x + (w - dw) / 2, cy + 12 - c.lineHeight(Fonts.SEMIBOLD, 10.5f) / 2, Theme.TEXT_MUTED);
				}
				cy += 30;
			}
			if (m.system) {
				List<String> lines = wrapped(c, m, textW + 46, Fonts.REGULAR, 11.5f);
				for (String line : lines) {
					if (visible(cy, lh, my0, mh)) {
						float lw = c.textWidth(Fonts.REGULAR, 11.5f, line);
						c.text(Fonts.REGULAR, 11.5f, line, x + (w - lw) / 2, cy, Theme.TEXT_MUTED);
					}
					cy += lh;
				}
				cy += 6;
				prev = m;
				continue;
			}
			float rowTop = cy;
			boolean head = newDay || prev == null || prev.system || !eq(prev.senderId, m.senderId) || m.createdAt - prev.createdAt > 5 * 60_000;
			if (head) {
				if (prev != null && !newDay) {
					cy += 10;
				}
				String who = m.senderName == null ? "Someone" : m.senderName;
				if (visible(cy, 40, my0, mh)) {
					String skin = null;
					Model.Friend sf = m.senderId == null ? null : client.friend(m.senderId);
					if (sf != null) {
						skin = sf.skin;
					} else if (eq(m.senderId, client.meId)) {
						skin = client.meSkin;
					}
					Avatars.draw(c, client.api(), sf != null ? sf.name : who, skin, x + pad, cy + 1, 34, 10);
					boolean mine = eq(m.senderId, client.meId);
					float nw = c.text(Fonts.SEMIBOLD, 13, who, textX, cy, mine ? Theme.TEXT_STRONG : Theme.mix(Theme.nameColor(who), 0xFFFFFFFF, 0.55f));
					String stamp = timeFmt.format(new Date(m.createdAt));
					float tw2 = c.text(Fonts.REGULAR, 10.5f, stamp, textX + nw + 8, cy + 2.5f, Theme.TEXT_MUTED);
					if (!group && mine && !m.pending && !m.failed && !m.deleted) { // delivered / read, like the launcher
						c.icon(m.read ? Theme.I_CHECKS : Theme.I_CHECK, 12, textX + nw + 8 + tw2 + 12, cy + 8, m.read ? 0xFF60A5FA : Theme.TEXT_MUTED);
					}
				}
				cy += 20;
			}
			boolean mineMsg = eq(m.senderId, client.meId);
			if (m.reply != null) { // the message this one answers; click to jump to it
				if (visible(cy, 18, my0, mh)) {
					Model.Reply r = m.reply;
					String qn = r.senderName == null ? "Someone" : r.senderName;
					String snip = r.deleted ? "Original message was deleted" : (r.content.isEmpty() ? (r.media ? "Picture" : "") : r.content.replace('\n', ' '));
					boolean qOver = ui.hover(textX, cy - 1, textW, 17);
					int qc = qOver ? Theme.TEXT : Theme.TEXT_MUTED;
					c.fill(textX, cy + 1, 2, 12, Theme.HAIRLINE_STRONG);
					c.icon(Theme.I_REPLY_ARROW, 11, textX + 11, cy + 7, qc);
					float qw = c.text(Fonts.SEMIBOLD, 11.5f, c.ellipsize(Fonts.SEMIBOLD, 11.5f, qn, Math.max(40, textW * 0.35f)), textX + 21, cy, Theme.mix(Theme.nameColor(qn), 0xFFFFFFFF, 0.55f));
					c.text(Fonts.REGULAR, 11.5f, c.ellipsize(Fonts.REGULAR, 11.5f, snip, Math.max(20, textW - 21 - qw - 8)), textX + 21 + qw + 6, cy, qc);
					if (qOver) {
						ui.cursorHand = true;
					}
					if (ui.clicked("relay:quote:" + m.id, textX, cy - 1, textW, 17) && r.id != null) {
						jumpTo = r.id;
						jumpTries = 0;
					}
				}
				cy += 19;
			}
			boolean hasMedia = m.mediaUrl != null || m.localKey != null;
			boolean autoText = hasMedia && (m.content == null || m.content.trim().isEmpty() || m.content.startsWith("["));
			int color = m.deleted ? Theme.TEXT_MUTED : (m.pending ? Theme.alpha(Theme.TEXT, 0.5f) : Theme.TEXT);
			if (m.deleted) {
				if (visible(cy, lh, my0, mh)) {
					c.text(Fonts.REGULAR, 12.5f, "This message was deleted", textX, cy, Theme.alpha(Theme.TEXT_MUTED, 0.85f));
				}
				cy += lh;
			} else if (!autoText) {
				List<String> lines = wrapped(c, m, textW, Fonts.REGULAR, 13);
				for (int li = 0; li < lines.size(); li++) {
					String line = lines.get(li);
					if (visible(cy, lh, my0, mh)) {
						float lw2 = c.text(Fonts.REGULAR, 13, line, textX, cy, color);
						if (m.edited && li == lines.size() - 1) {
							c.text(Fonts.REGULAR, 10, "(edited)", textX + lw2 + 6, cy + 3, Theme.TEXT_MUTED);
						}
					}
					cy += lh;
				}
			}
			if (hasMedia) {
				cy += media(ui, client, m, textX, cy, Math.min(textW, 340), my0, mh) + 4;
			}
			if (m.failed) {
				c.text(Fonts.MEDIUM, 10.5f, "Not sent \u2014 check your connection", textX, cy, 0xFFF87171);
				cy += 16;
			}
			if (!m.reactions.isEmpty() && !m.deleted) {
				cy = reactions(ui, client, m, textX, cy + 1, textW, my0, mh) + 2;
			}
			cy += 2;
			if (m.id != null && m.id.equals(flashId)) {
				float fa = 1f - Math.min(1f, (ui.now - flashAt) / 1600f);
				if (fa > 0) {
					c.fill(x, rowTop - 2, w - 10, cy - rowTop + 3, Theme.alpha(0xFF60A5FA, 0.16f * fa));
				} else {
					flashId = null;
				}
			}
			boolean rowHover = ui.interactive && viewer == null && !gifOpen && !pickerHover && ui.my >= my0 && ui.my <= my0 + mh && ui.hover(x, rowTop, w - 10, cy - rowTop);
			if (rowHover) {
				c.round(x + 8, rowTop - 2, w - 22, cy - rowTop + 3, 8, 0x0AFFFFFF);
			}
			if ((rowHover || m.id != null && m.id.equals(reactFor)) && !m.system && visible(rowTop, 24, my0, mh)) {
				tools(ui, client, m, group, mineMsg, canModerate, x + w - 22, rowTop - 4, my0, mh);
			}
			prev = m;
		}
		float content = cy - before + (before - (my0 - off)) + 10;
		// Older messages were loaded in on top: keep the message under the cursor where it was instead of
		// jumping (and immediately loading the next page because the view landed at the top again).
		String firstId = messages.isEmpty() ? null : messages.get(0).id;
		String prevFirst = firstIds.get(selected);
		// Rows above the reader changed height (older page loaded, a picture got its real size): move with them.
		if (selected.equals(anchorConv) && anchorId != null && !Float.isNaN(anchorNow) && sc.content > 0 && !sc.atBottom()) {
			float d = anchorNow - anchorY;
			if (Math.abs(d) > 0.5f) {
				sc.shift(d);
			}
		} else if (firstId != null && prevFirst != null && !firstId.equals(prevFirst) && sc.content > 0 && !sc.atBottom()) {
			sc.shift(content - sc.content);
		}
		anchorConv = selected;
		anchorId = nextAnchorId;
		anchorY = nextAnchorY;
		firstIds.put(selected, firstId);
		sc.end(ui, content);
		if (prevFirst == null && firstId != null) {
			sc.snapBottom(); // first messages in: open at the latest one, no glide from the top
		}
		if (jumpTo != null) {
			if (!Float.isNaN(jumpContentY)) {
				sc.target = Math.max(0, jumpContentY - my0 - mh * 0.25f);
				flashId = jumpTo;
				flashAt = ui.now;
				jumpTo = null;
			} else if (conv.hasMore && jumpTries < 12) {
				if (!conv.loading) {
					jumpTries++;
					client.loadOlder(selected);
				}
			} else {
				jumpTo = null; // the original is too far back (or gone)
			}
		}
		pickerRect = null;
		if (reactFor != null) {
			if (pickRowSet) {
				picker(ui, client, pickRow[0], pickRow[1], pickRow[2], my0, mh);
			} else {
				reactFor = null;
			}
		}
		jumpRect = null;
		if (!sc.atBottom() && sc.content > mh + 40) {
			String t = "Jump to latest";
			float pw2 = c.textWidth(Fonts.SEMIBOLD, 11.5f, t) + 40, ph2 = 28;
			float pxx = x + (w - pw2) / 2, pyy = my0 + mh - ph2 - 10;
			jumpRect = new float[] {pxx, pyy, pw2, ph2};
			boolean ov = ui.hover(pxx, pyy, pw2, ph2);
			c.shadow(pxx, pyy + 3, pw2, ph2, 14, 14, 0x88000000);
			c.round(pxx, pyy, pw2, ph2, 14, ov ? 0xFF2A2B31 : 0xFF1E1F25);
			c.outline(pxx, pyy, pw2, ph2, 14, 1, Theme.HAIRLINE_STRONG);
			c.text(Fonts.SEMIBOLD, 11.5f, t, pxx + 14, pyy + (ph2 - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2, Theme.TEXT_STRONG);
			c.icon(Theme.I_ARROWS_UP, 12, pxx + pw2 - 16, pyy + ph2 / 2, Theme.TEXT_SECONDARY);
			if (ui.clicked("relay:jump", pxx, pyy, pw2, ph2)) {
				sc.toBottom();
			}
		}
		if (messages.size() != lastCount) {
			if (lastCount == -1) {
				sc.snapBottom();
			} else if (!messages.isEmpty() && (eq(messages.get(messages.size() - 1).senderId, client.meId) || sc.atBottom())) {
				sc.toBottom();
			}
			lastCount = messages.size();
		}

		// typing + staged picture + input
		List<String> typers = client.typing(selected);
		float iy = y + h - inputH - 16;
		if (!typers.isEmpty()) {
			String t = typers.size() == 1 ? typers.get(0) + " is typing" : typers.size() + " people are typing";
			ui.dots(x + pad + 12, iy - 12, Theme.TEXT_SECONDARY);
			c.text(Fonts.MEDIUM, 11, t, x + pad + 30, iy - 12 - c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.TEXT_SECONDARY);
		}
		if (barH > 0) {
			float by = my0 + mh + 3, bw = w - pad * 2;
			c.round(x + pad, by, bw, barH - 8, 9, 0xFF121318);
			c.outline(x + pad, by, bw, barH - 8, 9, 1, Theme.HAIRLINE);
			float bm = by + (barH - 8) / 2;
			if (editingId != null) {
				c.icon(Theme.I_PENCIL, 13, x + pad + 18, bm, Theme.TEXT_SECONDARY);
				float ew = c.text(Fonts.SEMIBOLD, 12, "Editing message", x + pad + 34, bm - c.lineHeight(Fonts.SEMIBOLD, 12) / 2, Theme.TEXT_STRONG);
				c.text(Fonts.REGULAR, 11.5f, "Enter to save, Esc to cancel", x + pad + 34 + ew + 10, bm - c.lineHeight(Fonts.REGULAR, 11.5f) / 2, Theme.TEXT_MUTED);
			} else if (replyTo != null) {
				Model.Reply q = RelayClient.quote(replyTo);
				String qn = q.senderName == null ? "Someone" : q.senderName;
				String snip = q.deleted ? "" : (q.content.isEmpty() ? (q.media ? "Picture" : "") : q.content.replace('\n', ' '));
				c.icon(Theme.I_REPLY_ARROW, 13, x + pad + 18, bm, Theme.TEXT_SECONDARY);
				float rw = c.text(Fonts.SEMIBOLD, 12, "Replying to " + qn, x + pad + 34, bm - c.lineHeight(Fonts.SEMIBOLD, 12) / 2, Theme.TEXT_STRONG);
				c.text(Fonts.REGULAR, 11.5f, c.ellipsize(Fonts.REGULAR, 11.5f, snip, Math.max(20, bw - 34 - rw - 70)), x + pad + 34 + rw + 10, bm - c.lineHeight(Fonts.REGULAR, 11.5f) / 2, Theme.TEXT_MUTED);
			}
			if (ui.iconButton("relay:barx", x + pad + bw - 30, by + (barH - 8 - 24) / 2, 24, Theme.I_X, "Cancel (Esc)")) {
				if (editingId != null) {
					input.clear();
				}
				editingId = null;
				replyTo = null;
			}
		}
		byte[] got = pasted;
		if (got != null) {
			pasted = null;
			String mime = ClipboardImage.mime(got);
			if (mime == null) {
				stagedNote = "That clipboard picture isn't supported.";
			} else if (got.length > 8 * 1024 * 1024) {
				stagedNote = "Picture is too large (max 8 MB).";
			} else {
				staged = got;
				stagedMime = mime;
				stagedKey = "staged-" + System.nanoTime();
				ChatMedia.putLocal(stagedKey, got);
				stagedNote = null;
			}
		}
		if (staged != null) {
			ChatMedia.Media sm = ChatMedia.get(stagedKey, client.api());
			float cw = Math.min(w - pad * 2, 330), ch = 76;
			float cx = x + pad, cyy = iy - ch - 10 - (typers.isEmpty() ? 0 : 14);
			c.shadow(cx, cyy + 4, cw, ch, 12, 16, 0x77000000);
			c.round(cx, cyy, cw, ch, 12, 0xFF121318);
			c.outline(cx, cyy, cw, ch, 12, 1, Theme.HAIRLINE_STRONG);
			float tb = ch - 16;
			c.round(cx + 8, cyy + 8, tb, tb, 8, 0xFF0B0C10);
			Object img = sm.frame(ui.now);
			if (img != null) {
				c.pushClip(cx + 8, cyy + 8, tb, tb);
				c.imageCover((xyz.nativelaunch.ui.gfx.Image) img, cx + 8, cyy + 8, tb, tb, 1f, 0, 0, 0xFFFFFFFF);
				c.popClip();
			}
			c.outline(cx + 8, cyy + 8, tb, tb, 8, 1, Theme.HAIRLINE);
			c.text(Fonts.SEMIBOLD, 12.5f, "Picture ready", cx + tb + 22, cyy + 18, Theme.TEXT_STRONG);
			c.text(Fonts.REGULAR, 11, (staged.length < 1024 ? staged.length + " B" : (staged.length / 1024) + " KB") + " \u2014 press Enter to send", cx + tb + 22, cyy + 38, Theme.TEXT_MUTED);
			if (ui.iconButton("relay:unstage", cx + cw - 36, cyy + 8, 28, Theme.I_X, "Remove")) {
				staged = null;
			}
		} else if (stagedNote != null) {
			c.text(Fonts.MEDIUM, 11, stagedNote, x + pad, iy - 26, 0xFFF87171);
		}
		if (!ui.focusClaimed && ui.focus == null && !settingsOpen && search.value().isEmpty() && !gifOpen && nickFor == null && !shotsOpen) {
			input.focus(ui);
		}
		String before2 = input.value();
		float btn = 36;
		// one composer box: the text on the left, attach / GIF / send inside it on the right
		float boxW = w - pad * 2;
		float fa = ui.anim("relay:composer#f", input.focused(ui), 14f);
		c.round(x + pad, iy, boxW, inputH, 14, Theme.mix(0xFF101116, 0xFF13141A, fa));
		c.outline(x + pad, iy, boxW, inputH, 14, 1, Theme.mix(Theme.HAIRLINE_STRONG, Theme.BORDER_HOVER, fa));
		boolean send = input.draw(ui, x + pad + 4, iy, boxW - btn * 3 - 26, inputH, editingId != null ? "Edit your message" : staged != null ? "Add a caption (optional)" : "Message " + (group ? "#" + name : "@" + name), 13.5f);
		float by0 = iy + (inputH - btn) / 2;
		if (input.pasteMiss && pasting.compareAndSet(false, true)) {
			new Thread(() -> {
				try {
					pasted = ClipboardImage.read();
				} finally {
					pasting.set(false);
				}
			}, "Native-Clip").start();
		}
		if (!input.value().equals(before2) && !input.value().isEmpty()) {
			client.typingPing(selected);
		}
		// attach button: pick a picture from disk, like the launcher's (Ctrl+V still pastes one)
		float ax = x + w - pad - btn * 3 - 13;
		boolean aOver = ui.hover(ax, by0, btn, btn);
		boolean busy = picking.get();
		float aHv = ui.anim("relay:attach", aOver || busy || shotsOpen, 14f);
		boolean aClick = ui.clicked("relay:attach", ax, by0, btn, btn);
		c.round(ax, by0, btn, btn, 10, Theme.alpha(0xFFFFFFFF, 0.1f * aHv));
		if (busy) {
			ui.dots(ax + btn / 2, by0 + btn / 2, Theme.TEXT_SECONDARY);
		} else {
			c.icon(Theme.I_PLUS, 17, ax + btn / 2, by0 + btn / 2, staged != null || aHv > 0.5f ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
		}
		if (aOver) {
			ui.cursorHand = true;
			ui.tip(busy ? "Choose a picture in the window that opened" : "Send a screenshot or picture");
		}
		if (aClick && !busy) {
			shotsOpen = !shotsOpen;
			gifOpen = false;
			stagedNote = null;
			if (shotsOpen) {
				loadShots();
			}
		}
		// GIF button
		float gx = x + w - pad - btn * 2 - 9;
		boolean gOver = ui.hover(gx, by0, btn, btn);
		float gHv = ui.anim("relay:gifbtn", gOver || gifOpen, 14f);
		boolean gClick = ui.clicked("relay:gifbtn", gx, by0, btn, btn);
		c.round(gx, by0, btn, btn, 10, Theme.alpha(0xFFFFFFFF, 0.1f * gHv));
		float gw = c.textWidth(Fonts.BOLD, 11.5f, "GIF");
		c.text(Fonts.BOLD, 11.5f, "GIF", gx + (btn - gw) / 2, by0 + (btn - c.lineHeight(Fonts.BOLD, 11.5f)) / 2, gifOpen || gHv > 0.5f ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
		if (gClick) {
			gifOpen = !gifOpen;
			shotsOpen = false;
			if (gifOpen) {
				gifQuery = null;
				gifSearch.clear();
				gifSearch.focus(ui);
				ui.focusClaimed = true;
			} else {
				input.focus(ui);
			}
		}
		boolean canSend = !input.value().trim().isEmpty() || staged != null;
		float sx = x + w - pad - btn - 5;
		boolean over = ui.hover(sx, by0, btn, btn);
		float hv = ui.anim("relay:send", over && canSend, 14f);
		float ready = ui.anim("relay:send#on", canSend, 14f);
		boolean click = ui.clicked("relay:sendbtn", sx, by0, btn, btn);
		c.round(sx, by0, btn, btn, 10, Theme.mix(Theme.SUBTLE, Theme.mix(0xFFE4E4E7, 0xFFFFFFFF, hv), ready));
		c.icon(Theme.I_SEND, 16, sx + btn / 2, by0 + btn / 2, Theme.mix(Theme.TEXT_MUTED, Theme.SOLID_FG, ready));
		if ((send || click) && canSend) {
			if (editingId != null) {
				if (!input.value().trim().isEmpty()) {
					client.edit(selected, editingId, input.value());
				}
				editingId = null;
			} else {
				if (staged != null) {
					client.sendImage(selected, staged, stagedMime);
					staged = null;
				}
				if (!input.value().trim().isEmpty()) {
					client.send(selected, input.value(), replyTo);
					replyTo = null;
				}
			}
			input.clear();
			drafts.remove(selected);
			sc.toBottom();
			input.focus(ui);
		}
		if (gifOpen) {
			gifPicker(ui, client, x + w - pad - 372, iy - 360 - 8, 372, 360);
		}
		if (shotsOpen) {
			shotsPicker(ui, ax, iy, x + w - pad);
		}
	}

	// ── screenshots: the newest pictures from the game's screenshots folder ─────────────────

	private void browseFiles() {
		if (!picking.compareAndSet(false, true)) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				xyz.nativelaunch.ui.FilePicker.Result r = xyz.nativelaunch.ui.FilePicker.pickImage();
				if (r != null && r.bytes != null) {
					pasted = r.bytes; // staged by the same path as a pasted picture (type + size checks)
				} else if (r != null) {
					stagedNote = r.note;
				}
			} finally {
				picking.set(false);
			}
		}, "Native-Pick");
		t.setDaemon(true);
		t.start();
	}

	private void loadShots() {
		shots = null;
		Thread t = new Thread(() -> {
			List<java.io.File> out = new ArrayList<java.io.File>();
			try {
				java.nio.file.Path dir = xyz.nativelaunch.core.NativeState.get().gameDir();
				java.io.File[] all = dir == null ? null : dir.resolve("screenshots").toFile().listFiles();
				if (all != null) {
					List<java.io.File> png = new ArrayList<java.io.File>();
					for (java.io.File f : all) {
						String n = f.getName().toLowerCase(Locale.ROOT);
						if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) {
							png.add(f);
						}
					}
					java.util.Collections.sort(png, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
					out.addAll(png.subList(0, Math.min(9, png.size())));
				}
			} catch (Throwable ignored) {
				// no folder: the empty state shows
			}
			shots = out;
			for (java.io.File f : out) {
				String key = f.getAbsolutePath() + "|" + f.lastModified();
				if (shotThumbs.containsKey(key)) {
					continue;
				}
				try {
					xyz.nativelaunch.ui.gfx.Image img = xyz.nativelaunch.ui.gfx.Image.decode(java.nio.file.Files.readAllBytes(f.toPath()));
					if (img != null) {
						float k = Math.min(1f, 240f / img.width);
						img = img.scaled(Math.max(1, Math.round(img.width * k)), Math.max(1, Math.round(img.height * k)));
						img.linear = true;
						shotThumbs.put(key, img);
					}
				} catch (Throwable ignored) {
					// unreadable file: its tile stays blank
				}
			}
		}, "Native-Shots");
		t.setDaemon(true);
		t.start();
	}

	private void shotsPicker(Ui ui, float anchorX, float anchorY, float right) {
		Canvas c = ui.c;
		float pw = 388, cols = 3, tile = (pw - 24 - 16) / cols, th = tile * 9f / 16f;
		List<java.io.File> list = shots;
		int rows = list == null || list.isEmpty() ? 1 : (list.size() + 2) / 3;
		float ph = 52 + (list == null || list.isEmpty() ? 96 : rows * (th + 8)) + 8;
		float px = Math.max(8, right - pw), py = anchorY - ph - 8;
		c.shadow(px, py + 6, pw, ph, 14, 28, 0x88000000);
		c.round(px, py, pw, ph, 14, 0xFF121318);
		c.outline(px, py, pw, ph, 14, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.SEMIBOLD, 13, "Screenshots", px + 14, py + 16, Theme.TEXT_STRONG);
		if (ui.button("relay:shots:browse", px + pw - 14 - 108, py + 9, 108, 28, "Browse files", Theme.I_PLUS, Ui.BTN_GLASS)) {
			shotsOpen = false;
			browseFiles();
			return;
		}
		float gy = py + 46;
		if (list == null) {
			ui.dots(px + pw / 2, gy + 40, Theme.TEXT_MUTED);
		} else if (list.isEmpty()) {
			c.text(Fonts.MEDIUM, 12, "No screenshots yet", px + 14, gy + 24, Theme.TEXT_SECONDARY);
			c.text(Fonts.REGULAR, 11.5f, "Press F2 in game to take one, then pick it here.", px + 14, gy + 44, Theme.TEXT_MUTED);
		} else {
			for (int i = 0; i < list.size(); i++) {
				java.io.File f = list.get(i);
				float tx = px + 12 + (i % 3) * (tile + 8), ty = gy + (i / 3) * (th + 8);
				boolean over = ui.hover(tx, ty, tile, th);
				float hv = ui.anim("relay:shot:" + i, over, 16f);
				c.round(tx, ty, tile, th, 8, 0xFF08090C);
				xyz.nativelaunch.ui.gfx.Image img = shotThumbs.get(f.getAbsolutePath() + "|" + f.lastModified());
				if (img != null) {
					c.imageCover(img, tx, ty, tile, th, 1f, 0f, 0f, 0xFFFFFFFF);
				} else {
					ui.dots(tx + tile / 2, ty + th / 2, Theme.TEXT_MUTED);
				}
				c.outline(tx, ty, tile, th, 8, 1, Theme.mix(Theme.HAIRLINE, 0xCCFFFFFF, hv));
				if (over) {
					ui.tip(new SimpleDateFormat("d MMM, HH:mm", Locale.ENGLISH).format(new Date(f.lastModified())));
				}
				if (ui.clicked("relay:shot:" + i, tx, ty, tile, th)) {
					shotsOpen = false;
					final java.io.File pick = f;
					Thread t = new Thread(() -> {
						try {
							pasted = java.nio.file.Files.readAllBytes(pick.toPath());
						} catch (Throwable e) {
							stagedNote = "Could not read that screenshot.";
						}
					}, "Native-Shot");
					t.setDaemon(true);
					t.start();
				}
			}
		}
		if (ui.pressed && !ui.hover(px, py, pw, ph) && !ui.hover(anchorX, anchorY, 46, 46)) {
			shotsOpen = false;
		}
	}

	// ── friends home: the launcher's FriendsHome (Online / All / Pending / Blocked / Add Friend) ──

	private static final String[] HOME_TABS = {"Online", "All", "Pending", "Blocked"};

	private void friendsHome(Ui ui, RelayClient client, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		float barH = 58;
		List<Model.Friend> online = new ArrayList<Model.Friend>();
		for (Model.Friend f : client.friends) {
			if (f.online) {
				online.add(f);
			}
		}
		int pending = client.received.size() + client.sent.size();
		int[] counts = {online.size(), client.friends.size(), pending, client.blocked.size()};
		// right side of the bar: tabs, divider, Add Friend (the close button sits after it)
		float right = x + w - 58;
		String al = "Add Friend";
		float aw = c.textWidth(Fonts.SEMIBOLD, 12.5f, al) + 44;
		float tabsW = 0;
		float[] tws = new float[4];
		for (int i = 0; i < 4; i++) {
			tws[i] = c.textWidth(Fonts.SEMIBOLD, 13, HOME_TABS[i]) + 22 + countW(c, i, counts[i]);
			tabsW += tws[i] + 4;
		}
		float ax = right - aw;
		float tx0 = ax - 25 - tabsW;
		boolean brand = tx0 > x + 130;
		if (brand) {
			c.icon(Theme.I_USERS, 17, x + 32, y + barH / 2, Theme.TEXT_MUTED);
			c.text(Fonts.BOLD, 14, "Friends", x + 48, y + (barH - c.lineHeight(Fonts.BOLD, 14)) / 2, Theme.TEXT_STRONG);
		}
		float tx = Math.max(x + 12, tx0);
		for (int i = 0; i < 4; i++) {
			float bw = tws[i], bh = 28, by = y + (barH - bh) / 2;
			boolean on = homeTab == i;
			boolean over = ui.hover(tx, by, bw, bh);
			float hv = ui.anim("relay:home:tab" + i, over || on, 16f);
			c.round(tx, by, bw, bh, 14, Theme.alpha(0xFFFFFFFF, on ? 0.1f : 0.05f * hv));
			int col = on ? Theme.TEXT_STRONG : Theme.mix(Theme.TEXT_SECONDARY, 0xFFDDDDDD, hv);
			float lw = c.text(Fonts.SEMIBOLD, 13, HOME_TABS[i], tx + 11, by + (bh - c.lineHeight(Fonts.SEMIBOLD, 13)) / 2, col);
			if (i == 2) {
				if (counts[i] > 0) {
					TitleView.badge(c, tx + 11 + lw + 6 + countW(c, i, counts[i]) - 6, by + bh / 2, counts[i]);
				}
			} else {
				String n = String.valueOf(counts[i]);
				c.text(Fonts.BOLD, 11, n, tx + 11 + lw + 6, by + (bh - c.lineHeight(Fonts.BOLD, 11)) / 2, col);
			}
			if (over) {
				ui.cursorHand = true;
			}
			if (ui.clicked("relay:home:tab" + i, tx, by, bw, bh)) {
				homeTab = i;
				armed = null;
			}
			tx += bw + 4;
		}
		c.fill(ax - 13, y + barH / 2 - 9, 1, 18, Theme.HAIRLINE_STRONG);
		{
			float bh = 30, by = y + (barH - bh) / 2;
			boolean on = homeTab == 4;
			boolean over = ui.hover(ax, by, aw, bh);
			float hv = ui.anim("relay:home:add", over, 16f);
			c.round(ax, by, aw, bh, 9, on ? 0xFFFFFFFF : Theme.alpha(0xFFFFFFFF, 0.06f + 0.06f * hv));
			c.outline(ax, by, aw, bh, 9, 1, on ? 0xFFFFFFFF : Theme.alpha(0xFFFFFFFF, 0.12f + 0.1f * hv));
			int col = on ? 0xFF000000 : Theme.TEXT_STRONG;
			c.icon(Theme.I_USER_PLUS, 13, ax + 22, by + bh / 2, col);
			c.text(Fonts.SEMIBOLD, 12.5f, al, ax + 34, by + (bh - c.lineHeight(Fonts.SEMIBOLD, 12.5f)) / 2, col);
			if (over) {
				ui.cursorHand = true;
			}
			if (ui.clicked("relay:home:add", ax, by, aw, bh)) {
				homeTab = 4;
				addField.focus(ui);
				ui.focusClaimed = true;
			}
		}
		c.fill(x, y + barH, w, 1, Theme.HAIRLINE);

		// body
		float px = x + 28, pw = w - 56;
		float top = y + barH + 1;
		float by = top + 18;
		String note = client.notice;
		if (note != null && System.currentTimeMillis() - client.noticeAt < 6000) {
			int col = client.noticeError ? 0xFFF87171 : 0xFF23A55A;
			float nh = 36;
			c.round(px, by, pw, nh, 9, Theme.alpha(col, 0.12f));
			c.outline(px, by, pw, nh, 9, 1, Theme.alpha(col, 0.35f));
			c.text(Fonts.MEDIUM, 12, c.ellipsize(Fonts.MEDIUM, 12, note, pw - 60), px + 14, by + (nh - c.lineHeight(Fonts.MEDIUM, 12)) / 2, Theme.mix(col, 0xFFFFFFFF, 0.4f));
			if (ui.iconButton("relay:home:note:x", px + pw - 30, by + 6, 24, Theme.I_X, "Dismiss")) {
				client.notice = null;
			}
			by += nh + 14;
		}
		if (armed != null && ui.now - armedAt > 3000) {
			armed = null;
		}
		float viewH = y + h - by;
		float off = homeScroll.begin(ui, x, by, w, viewH);
		float cy = by - off;
		float vTop = by;
		switch (homeTab) {
			case 4:
				cy = addPanel(ui, client, px, cy, pw);
				break;
			case 2:
				cy = homeTitle(c, "PENDING \u2014 " + pending, px, cy);
				if (pending == 0) {
					homeEmpty(c, x, cy, w, viewH, Theme.I_USERS, client.requestsLoaded ? "There are no pending friend requests." : "Loading\u2026");
				}
				for (Model.Request r : client.received) {
					if (visible(cy, 68, vTop, viewH)) {
						personCard(ui, client, "req:" + r.id, px, cy, pw, r.name, r.skin, 0, "Incoming Friend Request", Theme.TEXT_MUTED);
						float bcy = cy + 34;
						if (circleBtn(ui, "relay:req:ok:" + r.id, px + pw - 18 - 17 - 42, bcy, Theme.I_CHECK, 0xFF23A55A, "Accept")) {
							client.respond(r.id, "accept");
						}
						if (circleBtn(ui, "relay:req:no:" + r.id, px + pw - 18 - 17, bcy, Theme.I_X, 0xFFF23F43, "Decline")) {
							client.respond(r.id, "decline");
						}
					}
					cy += 76;
				}
				for (Model.Request r : client.sent) {
					if (visible(cy, 68, vTop, viewH)) {
						personCard(ui, client, "req:" + r.id, px, cy, pw, r.name, r.skin, 0, "Outgoing Friend Request", Theme.TEXT_MUTED);
						if (ui.button("relay:req:cancel:" + r.id, px + pw - 18 - 92, cy + 20, 92, 28, "Cancel", Theme.I_X, Ui.BTN_GLASS)) {
							client.respond(r.id, "cancel");
						}
					}
					cy += 76;
				}
				break;
			case 3:
				cy = homeTitle(c, "BLOCKED USERS \u2014 " + client.blocked.size(), px, cy);
				if (client.blocked.isEmpty()) {
					homeEmpty(c, x, cy, w, viewH, Theme.I_BAN, client.blockedLoaded ? "You haven't blocked anyone." : "Loading\u2026");
				}
				for (Model.Person b : client.blocked) {
					if (visible(cy, 68, vTop, viewH)) {
						personCard(ui, client, "blk:" + b.id, px, cy, pw, b.name, b.skin, 0, "Blocked", Theme.TEXT_MUTED);
						if (ui.button("relay:unblock:" + b.id, px + pw - 18 - 92, cy + 20, 92, 28, "Unblock", Theme.I_CHECK, Ui.BTN_GLASS)) {
							client.unblock(b.id);
						}
					}
					cy += 76;
				}
				break;
			default: {
				List<Model.Friend> list = homeTab == 0 ? online : client.friends;
				cy = homeTitle(c, (homeTab == 0 ? "ONLINE \u2014 " : "ALL FRIENDS \u2014 ") + list.size(), px, cy);
				if (list.isEmpty()) {
					homeEmpty(c, x, cy, w, viewH, Theme.I_USERS, !client.friendsLoaded ? "Loading\u2026" : homeTab == 0 ? "No one is online right now." : "You have no friends yet.");
				}
				for (Model.Friend f : list) {
					if (visible(cy, 68, vTop, viewH)) {
						friendCard(ui, client, f, px, cy, pw, screen);
					}
					cy += 76;
				}
			}
		}
		homeScroll.end(ui, cy + off - by + 12);
	}

	private static float countW(Canvas c, int tab, int n) {
		if (tab == 2) {
			return n > 0 ? Math.max(18, c.textWidth(Fonts.BOLD, 10.5f, n > 99 ? "99+" : String.valueOf(n)) + 10) + 6 : 0;
		}
		return c.textWidth(Fonts.BOLD, 11, String.valueOf(n)) + 6;
	}

	private static float homeTitle(Canvas c, String label, float x, float y) {
		c.text(Fonts.SEMIBOLD, 11.5f, label, x, y, Theme.TEXT_MUTED);
		return y + 26;
	}

	/** Big muted icon + one line, like the launcher's empty friends list. */
	private static void homeEmpty(Canvas c, float x, float y, float w, float h, int icon, String text) {
		float cy = y + Math.min(110, Math.max(50, h * 0.22f));
		c.icon(icon, 44, x + w / 2, cy, Theme.alpha(Theme.TEXT_MUTED, 0.85f));
		float tw = c.textWidth(Fonts.MEDIUM, 13, text);
		c.text(Fonts.MEDIUM, 13, text, x + (w - tw) / 2, cy + 38, Theme.TEXT_SECONDARY);
	}

	/** Card shell: avatar 40 (with presence when dot != 0), name, line. Buttons are drawn by the caller. */
	private void personCard(Ui ui, RelayClient client, String id, float x, float y, float w, String name, String skin, int dot, String line, int lineColor) {
		Canvas c = ui.c;
		float ch = 68;
		float hv = ui.anim("relay:card:" + id, ui.hover(x, y, w, ch), 16f);
		c.round(x, y, w, ch, 12, Theme.alpha(0xFFFFFFFF, 0.025f + 0.02f * hv));
		c.outline(x, y, w, ch, 12, 1, Theme.alpha(0xFFFFFFFF, 0.07f + 0.05f * hv));
		float av = 40, ax = x + 16, ay = y + (ch - av) / 2;
		Avatars.draw(c, client.api(), name == null ? "Steve" : name, skin, ax, ay, av, 8);
		if (dot != 0) {
			c.circle(ax + av - 1, ay + av - 1, 6.5f, 0xFF0B0C10);
			c.circle(ax + av - 1, ay + av - 1, 4.5f, dot);
		}
		float tx = ax + av + 14, maxW = w - (tx - x) - 170;
		c.text(Fonts.SEMIBOLD, 14, c.ellipsize(Fonts.SEMIBOLD, 14, name == null ? "Player" : name, maxW), tx, y + 15, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 12, c.ellipsize(Fonts.REGULAR, 12, line, maxW), tx, y + 36, lineColor);
	}

	private void friendCard(Ui ui, RelayClient client, Model.Friend f, float x, float y, float w, Object screen) {
		Canvas c = ui.c;
		float ch = 68;
		boolean playing = f.online && f.serverAddress != null && !f.serverAddress.isEmpty();
		float actionsW = 3 * 38 + (playing ? 70 : 0) + 18;
		boolean click = ui.clicked("relay:fcard:" + f.id, x, y, w - actionsW, ch);
		if (ui.hover(x, y, w - actionsW, ch)) {
			ui.cursorHand = true;
		}
		int lc = !f.online ? Theme.TEXT_MUTED : playing || f.activity != null && !f.activity.isEmpty() ? Theme.mix(Theme.ONLINE, 0xFFFFFFFF, 0.35f) : Theme.TEXT_SECONDARY;
		personCard(ui, client, "f:" + f.id, x, y, w, f.display(), f.skin, f.dot(), f.online ? f.line() : "Offline", lc);
		// "@handle" after the name, like the launcher
		float tx = x + 16 + 40 + 14;
		float nw = c.textWidth(Fonts.SEMIBOLD, 14, c.ellipsize(Fonts.SEMIBOLD, 14, f.display(), w - (tx - x) - 170));
		if (tx + nw + 8 + 60 < x + w - actionsW) {
			c.text(Fonts.REGULAR, 12, c.ellipsize(Fonts.REGULAR, 12, "@" + f.name, x + w - actionsW - tx - nw - 8), tx + nw + 8, y + 17, Theme.TEXT_MUTED);
		}
		float bx = x + w - 18 - 34, bcy = y + ch / 2;
		String bk = "block:" + f.id, rk = "remove:" + f.id;
		if (armed != null && armed.equals(bk)) {
			c.round(bx, bcy - 17, 34, 34, 17, Theme.alpha(Theme.DANGER, 0.25f));
		}
		if (ui.iconButton("relay:fcard:block:" + f.id, bx, bcy - 17, 34, Theme.I_BAN, bk.equals(armed) ? "Click again to block" : "Block")) {
			if (bk.equals(armed)) {
				armed = null;
				client.block(f.id);
			} else {
				armed = bk;
				armedAt = ui.now;
			}
		}
		bx -= 38;
		if (armed != null && armed.equals(rk)) {
			c.round(bx, bcy - 17, 34, 34, 17, Theme.alpha(Theme.DANGER, 0.25f));
		}
		if (ui.iconButton("relay:fcard:remove:" + f.id, bx, bcy - 17, 34, Theme.I_TRASH, rk.equals(armed) ? "Click again to remove" : "Remove Friend")) {
			if (rk.equals(armed)) {
				armed = null;
				client.removeFriend(f.id);
			} else {
				armed = rk;
				armedAt = ui.now;
			}
		}
		bx -= 38;
		if (ui.iconButton("relay:fcard:msg:" + f.id, bx, bcy - 17, 34, Theme.I_MESSAGE, "Message")) {
			click = true;
		}
		if (playing && ui.button("relay:fcard:join:" + f.id, bx - 70, bcy - 14, 64, 28, "Join", Theme.I_PLAY, Ui.BTN_PRIMARY)) {
			UiRuntime.closeHost(screen);
			UiRuntime.mc().connect(f.serverAddress, null);
			return;
		}
		if (click) {
			select("dm:" + f.id, client);
			input.focus(ui);
		}
	}

	/** Round tinted action (accept / decline). */
	private static boolean circleBtn(Ui ui, String id, float cx, float cy, int icon, int color, String tip) {
		Canvas c = ui.c;
		boolean over = ui.hover(cx - 17, cy - 17, 34, 34);
		float hv = ui.anim(id + "#h", over, 16f);
		c.circle(cx, cy, 17, Theme.mix(Theme.alpha(color, 0.2f), color, hv));
		c.icon(icon, 17, cx, cy, Theme.mix(color, 0xFFFFFFFF, hv));
		if (over) {
			ui.cursorHand = true;
			ui.tip(tip);
		}
		return ui.clicked(id, cx - 17, cy - 17, 34, 34);
	}

	/** ADD FRIEND: exact-name request box, plus live player search under it. */
	private float addPanel(Ui ui, RelayClient client, float x, float y, float w) {
		Canvas c = ui.c;
		c.text(Fonts.BOLD, 15, "ADD FRIEND", x, y, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 12.5f, "You can add friends with their exact Native or Minecraft username.", x, y + 24, Theme.TEXT_SECONDARY);
		float fy = y + 54, fh = 50;
		boolean focused = addField.focused(ui);
		float fa = ui.anim("relay:addbox#f", focused, 14f);
		c.round(x, fy, w, fh, 10, 0x73000000);
		c.outline(x, fy, w, fh, 10, 1, Theme.mix(0x1AFFFFFF, 0x59FFFFFF, fa));
		String bl = "Send Friend Request";
		float bw = c.textWidth(Fonts.BOLD, 12.5f, bl) + 32, bh = 32;
		float bxx = x + w - bw - 9, byy = fy + (fh - bh) / 2;
		addField.maxLength = 32;
		boolean enter = addField.draw(ui, x + 4, fy, w - bw - 22, fh, "You can add friends with their username", 13.5f);
		String query = addField.value().trim();
		boolean can = !query.isEmpty();
		boolean over = can && ui.hover(bxx, byy, bw, bh);
		float hv = ui.anim("relay:addbtn", over, 16f);
		c.round(bxx, byy, bw, bh, 9, can ? Theme.mix(0xFFFFFFFF, 0xE0FFFFFF, hv) : 0x40FFFFFF);
		c.text(Fonts.BOLD, 12.5f, bl, bxx + 16, byy + (bh - c.lineHeight(Fonts.BOLD, 12.5f)) / 2, can ? 0xFF000000 : 0x99000000);
		if (over) {
			ui.cursorHand = true;
		}
		boolean click = ui.clicked("relay:addbtn", bxx, byy, bw, bh);
		if ((enter || click) && can) {
			client.sendRequest(query);
			addField.set("");
			query = "";
		}
		client.search(query);
		float ry = fy + fh + 26;
		if (query.length() >= 2) {
			ry = homeTitle(c, "PLAYERS", x, ry);
			if (client.found.isEmpty()) {
				c.text(Fonts.REGULAR, 12, client.searching ? "Searching\u2026" : "No Native player with that name.", x, ry, Theme.TEXT_MUTED);
				ry += 28;
			}
			for (Model.Person p : client.found) {
				boolean friend = false, pending = false;
				for (Model.Friend f : client.friends) {
					friend |= f.id.equals(p.id);
				}
				for (Model.Request r : client.sent) {
					pending |= r.userId != null && r.userId.equals(p.id);
				}
				personCard(ui, client, "find:" + p.id, x, ry, w, p.name, p.skin, 0, friend ? "Already friends" : pending ? "Request sent" : "Native player", Theme.TEXT_MUTED);
				if (!friend && !pending && ui.button("relay:find:" + p.id, x + w - 18 - 76, ry + 20, 76, 28, "Add", Theme.I_USER_PLUS, Ui.BTN_PRIMARY)) {
					client.sendRequest(p.name);
				}
				ry += 76;
			}
		}
		return ry;
	}

	private float heading(Canvas c, String label, float x, float y) {
		c.text(Fonts.SEMIBOLD, 10.5f, label.toUpperCase(Locale.ROOT), x, y + 4, Theme.TEXT_MUTED);
		return y + 26;
	}

	/** A player row (avatar, name, a muted line); buttons are drawn by the caller on the right. */
	private void person(Ui ui, RelayClient client, float x, float y, float w, float h, String name, String skin, String sub) {
		Canvas c = ui.c;
		float hv = ui.anim("relay:person:" + name + y, ui.hover(x, y, w, h), 14f);
		c.round(x, y, w, h, 12, Theme.alpha(Theme.SUBTLE_HOVER, hv * 0.7f));
		float av = 32;
		Avatars.draw(c, client.api(), name == null ? "Steve" : name, skin, x + 9, y + (h - av) / 2, av, 9);
		float tx = x + 9 + av + 11, maxW = w - (tx - x) - 90;
		c.text(Fonts.SEMIBOLD, 12.5f, c.ellipsize(Fonts.SEMIBOLD, 12.5f, name == null ? "Player" : name, maxW), tx, y + 9, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, sub, maxW), tx, y + 27, Theme.TEXT_MUTED);
	}

	// ── friend profile panel ──────────────────────────────────────────────

	private void profilePanel(Ui ui, RelayClient client, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		c.pushClip(x, y, w, h);
		c.round(x - 20, y, w + 20, h, 20, 0xFF0C0D11);
		Model.Friend f = client.friend(selected.substring(3));
		if (f == null) {
			TitleView.empty(c, x, y, w, h, Theme.I_USER, "Not a friend", "This chat is no longer in your friends list.");
			c.popClip();
			return;
		}
		float pad = 18;
		float off = profileScroll.begin(ui, x, y, w, h);
		float ty = y + 22 - off;
		float iw = w - pad * 2;
		Model.Profile p = client.profile(f.name);
		boolean plus = p != null && p.badges.contains("plus");

		// round avatar with the presence dot, like the launcher's profile panel
		float av = 76;
		c.shadow(x + pad, ty + 6, av, av, av / 2, 22, Theme.alpha(f.dot(), 0.22f));
		Avatars.drawRound(c, client.api(), f.name, f.skin, x + pad, ty, av);
		c.circle(x + pad + av - 9, ty + av - 9, 11, 0xFF0C0D11);
		c.circle(x + pad + av - 9, ty + av - 9, 7.5f, f.dot());
		ty += av + 12;

		// name (+ Native+ mark), @handle, presence
		String display = c.ellipsize(Fonts.BOLD, 19, f.display(), iw - (plus ? 26 : 0));
		c.text(Fonts.BOLD, 19, display, x + pad, ty, Theme.TEXT_STRONG);
		if (plus) {
			float mx = x + pad + c.textWidth(Fonts.BOLD, 19, display) + 7;
			Image mark = badgeArt("plus");
			if (mark != null) {
				c.image(mark, mx, ty + 3, 17, 17, 0, 0, 1, 1, 0xFFFFFFFF);
			}
			if (ui.hover(mx, ty + 3, 17, 17)) {
				ui.tip("Native+ member");
			}
		}
		ty += 26;
		c.text(Fonts.REGULAR, 12, "@" + f.name, x + pad, ty, Theme.TEXT_MUTED);
		ty += 19;
		String line = f.online ? f.line() : f.lastSeen > 0 ? "Last seen " + ago(f.lastSeen) : "Offline";
		c.text(Fonts.MEDIUM, 11.5f, c.ellipsize(Fonts.MEDIUM, 11.5f, line, iw), x + pad, ty, f.online ? Theme.mix(f.dot(), 0xFFFFFFFF, 0.45f) : Theme.TEXT_MUTED);
		ty += 26;

		if (p == null) {
			c.skeleton(x + pad, ty, iw, 46, 12);
			c.skeleton(x + pad, ty + 60, iw * 0.6f, 10, 5);
			ty += 90;
		} else {
			// badges: the launcher's own badge art in a pill, name on hover
			java.util.List<String> shown = new java.util.ArrayList<String>();
			for (String b : p.badges) { // the player's own order, like the launcher
				if (badgeArt(b) != null && !shown.contains(b)) {
					shown.add(b);
				}
			}
			if (!shown.isEmpty()) {
				float ch = 46;
				c.round(x + pad, ty, iw, ch, 12, Theme.SUBTLE);
				c.outline(x + pad, ty, iw, ch, 12, 1, Theme.HAIRLINE);
				c.text(Fonts.SEMIBOLD, 9.5f, "BADGES", x + pad + 14, ty + (ch - c.lineHeight(Fonts.SEMIBOLD, 9.5f)) / 2, Theme.TEXT_MUTED);
				float icon = 22, gap = 6;
				int fit = Math.max(1, Math.min(shown.size(), (int) ((iw - 90) / (icon + gap))));
				float pw = fit * icon + (fit - 1) * gap + 14;
				float px = x + pad + iw - pw - 8, py = ty + (ch - 32) / 2;
				c.round(px, py, pw, 32, 16, 0xFF15121F);
				c.outline(px, py, pw, 32, 16, 1, Theme.HAIRLINE);
				for (int i = 0; i < fit; i++) {
					String b = shown.get(i);
					float bx = px + 7 + i * (icon + gap), by = py + 5;
					boolean over = ui.hover(bx - 2, by - 2, icon + 4, icon + 4);
					float lift = ui.anim("relay:badge:" + b, over, 16f) * 2;
					c.image(badgeArt(b), bx, by - lift, icon, icon, 0, 0, 1, 1, 0xFFFFFFFF);
					if (over) {
						ui.tip(badgeName(b));
					}
				}
				ty += ch + 12;
			}
			if (ui.button("relay:prof:web", x + pad, ty, iw, 32, "View full profile", Theme.I_USER, Ui.BTN_GLASS)) {
				if (f.name.matches("[A-Za-z0-9_]{1,32}")) {
					xyz.nativelaunch.ui.Ads.open("https://playnative.net/u/" + f.name);
				}
			}
			ty += 44;

			// connections: the linked Minecraft account
			if (p.premium) {
				ty = heading(c, "Connections", x + pad, ty);
				Avatars.draw(c, client.api(), f.name, f.skin, x + pad, ty, 32, 8);
				c.text(Fonts.SEMIBOLD, 12.5f, f.name, x + pad + 42, ty + 1, Theme.TEXT_STRONG);
				c.icon(Theme.I_CHECK_CIRCLE, 12, x + pad + 42 + c.textWidth(Fonts.SEMIBOLD, 12.5f, f.name) + 10, ty + 8, Theme.ONLINE);
				c.text(Fonts.REGULAR, 11, "Minecraft · Premium", x + pad + 42, ty + 18, Theme.TEXT_MUTED);
				ty += 46;
			}

			// activity: playing on a server, with Join
			if (f.online && f.serverAddress != null && !f.serverAddress.isEmpty()) {
				ty = heading(c, "Activity", x + pad, ty);
				float bh = 106;
				c.round(x + pad, ty, iw, bh, 12, Theme.SUBTLE);
				c.outline(x + pad, ty, iw, bh, 12, 1, Theme.HAIRLINE);
				c.text(Fonts.SEMIBOLD, 9.5f, "PLAYING ON A SERVER", x + pad + 14, ty + 12, Theme.TEXT_MUTED);
				c.round(x + pad + 14, ty + 30, 34, 34, 9, 0xFF000000);
				c.icon(Theme.I_SERVER, 16, x + pad + 31, ty + 47, Theme.TEXT_SECONDARY);
				c.text(Fonts.BOLD, 12.5f, c.ellipsize(Fonts.BOLD, 12.5f, f.serverAddress, iw - 76), x + pad + 58, ty + 32, Theme.TEXT_STRONG);
				c.text(Fonts.REGULAR, 11, "Minecraft server", x + pad + 58, ty + 50, Theme.TEXT_MUTED);
				if (ui.button("relay:join:" + f.id, x + pad + 12, ty + bh - 38, iw - 24, 28, "Join server", Theme.I_PLAY, Ui.BTN_PRIMARY)) {
					UiRuntime.closeHost(screen);
					UiRuntime.mc().connect(f.serverAddress, null);
				}
				ty += bh + 16;
			}

			// details
			ty = heading(c, "Details", x + pad, ty);
			if (p.joinedAt > 0) {
				ty = detail(c, "Member since", new java.text.SimpleDateFormat("MMM d, yyyy", Locale.ROOT).format(new java.util.Date(p.joinedAt)), x + pad, ty, iw);
			}
			ty = detail(c, "Friends", String.valueOf(p.friends), x + pad, ty, iw);
			ty += 8;
			if (p.bio != null && !p.bio.trim().isEmpty()) {
				ty = heading(c, "About me", x + pad, ty);
				for (String l : xyz.nativelaunch.ui.TextLayout.wrap(c, Fonts.REGULAR, 12, p.bio.trim(), iw)) {
					c.text(Fonts.REGULAR, 12, l, x + pad, ty, Theme.TEXT);
					ty += 18;
				}
				ty += 10;
			}
		}
		ty += 4;

		// actions
		ty = heading(c, "Friend", x + pad, ty);
		float bw = w - pad * 2;
		if (ui.button("relay:prof:nick", x + pad, ty, bw, 32, f.nickname == null ? "Set nickname" : "Change nickname", Theme.I_PENCIL, Ui.BTN_GLASS)) {
			nickFor = f.id;
			nick.maxLength = 28;
			nick.set(f.nickname == null ? "" : f.nickname);
			nick.focus(ui);
			ui.focusClaimed = true;
		}
		ty += 38;
		if (ui.button("relay:prof:pin", x + pad, ty, bw, 32, f.pinned ? "Unpin chat" : "Pin chat", Theme.I_MAP_PIN, Ui.BTN_GLASS)) {
			client.setPinned("dm:" + f.id, !f.pinned);
		}
		ty += 38;
		if (armed != null && ui.now - armedAt > 3000) {
			armed = null;
		}
		String rk = "remove:" + f.id, bk = "block:" + f.id;
		if (ui.button("relay:prof:remove", x + pad, ty, bw, 32, rk.equals(armed) ? "Click again to remove" : "Remove friend", Theme.I_TRASH, Ui.BTN_DANGER)) {
			if (rk.equals(armed)) {
				armed = null;
				client.removeFriend(f.id);
				selected = null;
			} else {
				armed = rk;
				armedAt = ui.now;
			}
		}
		ty += 38;
		if (ui.button("relay:prof:block", x + pad, ty, bw, 32, bk.equals(armed) ? "Click again to block" : "Block", Theme.I_BAN, Ui.BTN_DANGER)) {
			if (bk.equals(armed)) {
				armed = null;
				client.block(f.id);
				selected = null;
			} else {
				armed = bk;
				armedAt = ui.now;
			}
		}
		ty += 44;
		profileScroll.end(ui, ty + off - y);
		c.popClip();
	}

	/** "Member since ........ Oct 11, 2026" with a hairline under it. */
	private float detail(Canvas c, String label, String value, float x, float y, float w) {
		c.text(Fonts.REGULAR, 12, label, x, y, Theme.TEXT_MUTED);
		c.text(Fonts.SEMIBOLD, 12, value, x + w - c.textWidth(Fonts.SEMIBOLD, 12, value), y, Theme.TEXT_STRONG);
		c.fill(x, y + 24, w, 1, Theme.HAIRLINE);
		return y + 34;
	}

	/** The launcher's badge art, bundled with the mod (null for unknown badges). */
	private static final java.util.Map<String, Image> BADGE_ART = new java.util.HashMap<String, Image>();

	private static Image badgeArt(String id) {
		if (id == null || !id.matches("[a-z_]{2,32}")) {
			return null;
		}
		if (!BADGE_ART.containsKey(id)) {
			Image img = Image.resource("/assets/native/badges/" + id + ".png");
			if (img != null) {
				img.linear = true;
			}
			BADGE_ART.put(id, img);
		}
		return BADGE_ART.get(id);
	}

	private float fact(Canvas c, int icon, String text, float x, float y, float w) {
		c.icon(icon, 13, x + 7, y + 8, Theme.TEXT_MUTED);
		c.text(Fonts.MEDIUM, 12, c.ellipsize(Fonts.MEDIUM, 12, text, w - 24), x + 22, y + 1, Theme.TEXT_SECONDARY);
		return y + 24;
	}

	/** "early_supporter" -> "Early Supporter". */
	/** Same names and colours as the launcher's badges. */
	private static String badgeName(String id) {
		switch (String.valueOf(id)) {
			case "developer": return "Active Developer";
			case "early_supporter": return "Early Supporter";
			case "bug_hunter": return "Bug Hunter";
			case "staff": return "Native Staff";
			case "super_beta_tester": return "Super Beta Tester";
			case "partner": return "Native Partner";
			case "plus": return "Native+";
			case "verified": return "Verified";
			default: break;
		}
		StringBuilder out = new StringBuilder();
		for (String part : String.valueOf(id).replace('-', '_').split("_")) {
			if (!part.isEmpty()) {
				out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return out.toString();
	}

	private static int badgeColor(String id) {
		switch (String.valueOf(id)) {
			case "developer": return 0xFF5865F2;
			case "early_supporter": return 0xFFF47B67;
			case "bug_hunter": return 0xFF3BA55D;
			case "staff": return 0xFFEB459E;
			case "super_beta_tester": return 0xFF2FD4C9;
			case "partner": return 0xFF8B5CF6;
			case "plus": return 0xFFF5B94A;
			case "verified": return 0xFF3B82F6;
			default: return Theme.nameColor(id);
		}
	}

	private static String ago(long at) {
		long m = Math.max(0, (System.currentTimeMillis() - at) / 60000);
		if (m < 1) {
			return "just now";
		}
		if (m < 60) {
			return m + " min ago";
		}
		long hrs = m / 60;
		if (hrs < 24) {
			return hrs + " h ago";
		}
		long d = hrs / 24;
		return d == 1 ? "yesterday" : d + " days ago";
	}

	// ── right-click menu + nickname ──────────────────────────────────────────────────────────

	private void contextMenu(Ui ui, RelayClient client, float W, float H) {
		Canvas c = ui.c;
		String key = menuKey;
		boolean group = key.startsWith("g:");
		boolean pinned = false;
		String who = null;
		if (group) {
			for (Model.Group g : client.groups) {
				if (key.equals("g:" + g.id)) {
					pinned = g.pinned;
				}
			}
		} else {
			for (Model.Friend f : client.friends) {
				if (key.equals("dm:" + f.id)) {
					pinned = f.pinned;
					who = f.display();
				}
			}
		}
		String[] labels = group ? new String[] {pinned ? "Unpin chat" : "Pin chat", "Delete chat"}
				: new String[] {pinned ? "Unpin chat" : "Pin chat", "Set nickname", "Delete chat"};
		int[] icons = group ? new int[] {Theme.I_MAP_PIN, Theme.I_TRASH} : new int[] {Theme.I_MAP_PIN, Theme.I_PENCIL, Theme.I_TRASH};
		float mw = 196, ih = 34, mh = labels.length * ih + 12;
		float mx = Math.min(menuX, W - mw - 8), my = Math.min(menuY, H - mh - 8);
		c.shadow(mx, my + 6, mw, mh, 12, 24, 0x99000000);
		c.round(mx, my, mw, mh, 12, 0xFF121318);
		c.outline(mx, my, mw, mh, 12, 1, Theme.HAIRLINE_STRONG);
		int picked = -1;
		for (int i = 0; i < labels.length; i++) {
			float iy = my + 6 + i * ih;
			boolean danger = i == labels.length - 1;
			boolean over = ui.hover(mx + 6, iy, mw - 12, ih);
			float hv = ui.anim("relay:ctx:" + i, over, 18f);
			c.round(mx + 6, iy, mw - 12, ih, 8, Theme.alpha(danger ? 0xFFEF4444 : 0xFFFFFFFF, 0.1f * hv));
			int col = danger ? Theme.mix(0xFFFCA5A5, 0xFFFFFFFF, hv * 0.3f) : Theme.mix(Theme.TEXT, Theme.TEXT_STRONG, hv);
			c.icon(icons[i], 14, mx + 24, iy + ih / 2, col);
			c.text(Fonts.MEDIUM, 12.5f, labels[i], mx + 40, iy + (ih - c.lineHeight(Fonts.MEDIUM, 12.5f)) / 2, col);
			if (ui.clicked("relay:ctx:" + i, mx + 6, iy, mw - 12, ih)) {
				picked = i;
			}
		}
		if (picked >= 0) {
			String k = key;
			menuKey = null;
			if (picked == 0) {
				client.setPinned(k, !pinned);
			} else if (!group && picked == 1) {
				nickFor = k.substring(3);
				String cur = null;
				for (Model.Friend f : client.friends) {
					if (f.id.equals(nickFor)) {
						cur = f.nickname;
					}
				}
				nick.maxLength = 28;
				nick.set(cur == null ? "" : cur);
				nick.focus(ui);
				ui.focusClaimed = true;
			} else {
				client.clearChat(k);
				chatScrolls.remove(k);
				lastCount = -1;
			}
			return;
		}
		if (menuFresh) {
			menuFresh = false;
		} else if ((ui.pressed || ui.rightPressed) && !(ui.mx >= mx && ui.mx < mx + mw && ui.my >= my && ui.my < my + mh)) {
			menuKey = null;
		}
	}

	private void nicknameDialog(Ui ui, RelayClient client, float W, float H) {
		Canvas c = ui.c;
		String name = nickFor;
		for (Model.Friend f : client.friends) {
			if (f.id.equals(nickFor)) {
				name = f.name;
			}
		}
		c.fill(0, 0, W, H, 0x99000000);
		float dw = 340, dh = 168, dx = (W - dw) / 2, dy = (H - dh) / 2;
		c.shadow(dx, dy + 8, dw, dh, 16, 32, 0xAA000000);
		c.round(dx, dy, dw, dh, 16, 0xFF121318);
		c.outline(dx, dy, dw, dh, 16, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.BOLD, 15, "Set nickname", dx + 20, dy + 18, Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 12, "Only you see this name for " + c.ellipsize(Fonts.REGULAR, 12, name, dw - 190) + ".", dx + 20, dy + 42, Theme.TEXT_MUTED);
		boolean enter = nick.draw(ui, dx + 20, dy + 68, dw - 40, 38, "Nickname", 13.5f);
		if (ui.focus == null) {
			nick.focus(ui);
		}
		ui.focusClaimed = true;
		boolean save = enter;
		if (ui.button("relay:nick:save", dx + dw - 20 - 96, dy + dh - 20 - 34, 96, 34, "Save", Theme.I_CHECK, Ui.BTN_PRIMARY)) {
			save = true;
		}
		if (ui.button("relay:nick:cancel", dx + dw - 20 - 96 - 8 - 96, dy + dh - 20 - 34, 96, 34, "Cancel", Theme.I_X, Ui.BTN_GLASS)) {
			nickFor = null;
			return;
		}
		if (save) {
			client.setNickname(nickFor, nick.value());
			nickFor = null;
			input.focus(ui);
		}
	}

	// ── message actions: reactions, hover tools, reaction picker ──────────

	private xyz.nativelaunch.ui.gfx.Image emoji(String e) {
		String hex = Integer.toHexString(e.codePointAt(0));
		if (!emojiImages.containsKey(hex)) {
			emojiImages.put(hex, xyz.nativelaunch.ui.gfx.Image.resource("/assets/native/ui/emoji-" + hex + ".png"));
		}
		return emojiImages.get(hex);
	}

	private void drawEmoji(Canvas c, String e, float x, float y, float size) {
		xyz.nativelaunch.ui.gfx.Image img = emoji(e);
		if (img != null) {
			c.stamp("emoji:" + Integer.toHexString(e.codePointAt(0)), img, x, y, size, size, true, 0xFFFFFFFF);
		} else {
			c.icon(Theme.I_SMILE, size * 0.9f, x + size / 2, y + size / 2, Theme.TEXT_SECONDARY);
		}
	}

	/** The reaction chips under a message ({emoji + count}, yours lit up); click one to toggle it. Returns the y below them. */
	private float reactions(Ui ui, RelayClient client, Model.Message m, float x, float y, float width, float top, float viewH) {
		Canvas c = ui.c;
		java.util.LinkedHashMap<String, int[]> counts = new java.util.LinkedHashMap<String, int[]>();
		for (String[] r : m.reactions) {
			int[] n = counts.get(r[1]);
			if (n == null) {
				n = new int[2];
				counts.put(r[1], n);
			}
			n[0]++;
			if (r[0] != null && r[0].equals(client.meId)) {
				n[1] = 1;
			}
		}
		float cx = x, cy = y, ch = 22;
		for (Map.Entry<String, int[]> en : counts.entrySet()) {
			String label = String.valueOf(en.getValue()[0]);
			float cw = 10 + 14 + 5 + c.textWidth(Fonts.SEMIBOLD, 11.5f, label) + 10;
			if (cx + cw > x + width && cx > x) {
				cx = x;
				cy += ch + 4;
			}
			if (visible(cy, ch, top, viewH)) {
				boolean mine = en.getValue()[1] == 1;
				boolean over = ui.hover(cx, cy, cw, ch);
				c.round(cx, cy, cw, ch, 11, mine ? Theme.alpha(0xFF60A5FA, over ? 0.3f : 0.2f) : (over ? 0xFF26272D : Theme.SUBTLE));
				c.outline(cx, cy, cw, ch, 11, 1, mine ? Theme.alpha(0xFF60A5FA, 0.6f) : Theme.HAIRLINE);
				drawEmoji(c, en.getKey(), cx + 8, cy + 4, 14);
				c.text(Fonts.SEMIBOLD, 11.5f, label, cx + 27, cy + (ch - c.lineHeight(Fonts.SEMIBOLD, 11.5f)) / 2, mine ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
				if (over) {
					ui.cursorHand = true;
				}
				if (ui.clicked("relay:re:" + m.id + ":" + en.getKey(), cx, cy, cw, ch)) {
					client.react(selected, m.id, en.getKey());
				}
			}
			cx += cw + 6;
		}
		return cy + ch;
	}

	/** The pill of tools at the top right of the hovered message: react (groups), reply, edit (yours), delete. */
	private void tools(Ui ui, RelayClient client, Model.Message m, boolean group, boolean mine, boolean moderator, float right, float y, float top, float viewH) {
		Canvas c = ui.c;
		boolean sent = m.id != null && !m.id.startsWith("local-") && !m.pending;
		boolean hasMedia = m.mediaUrl != null || m.localKey != null;
		List<int[]> buttons = new ArrayList<int[]>(); // {action, icon}
		if (!m.deleted) {
			if (group && sent) {
				buttons.add(new int[] {0, Theme.I_SMILE});
			}
			if (sent) {
				buttons.add(new int[] {1, Theme.I_REPLY_ARROW});
			}
			if (mine && sent && !hasMedia) {
				buttons.add(new int[] {2, Theme.I_PENCIL});
			}
		}
		if ((mine || moderator) && !m.deleted && !m.pending || mine && m.failed) {
			buttons.add(new int[] {3, Theme.I_TRASH});
		}
		if (buttons.isEmpty()) {
			return;
		}
		float bs = 24, pad = 3, w = buttons.size() * bs + pad * 2, x = right - w;
		c.shadow(x, y + 2, w, bs + pad * 2 - 2, 9, 10, 0x77000000);
		c.round(x, y, w, bs + 2, 9, 0xFF17181D);
		c.outline(x, y, w, bs + 2, 9, 1, Theme.HAIRLINE_STRONG);
		toolbarRect = new float[] {x, y, w, bs + 2};
		if (m.id != null && m.id.equals(reactFor)) {
			pickRow[0] = x;
			pickRow[1] = y;
			pickRow[2] = w;
			pickRowSet = true;
		}
		String[] tips = {"Add reaction", "Reply", "Edit message", "Delete message"};
		for (int i = 0; i < buttons.size(); i++) {
			int a = buttons.get(i)[0];
			float bx = x + pad + i * bs;
			boolean over = ui.hover(bx, y + 1, bs, bs);
			boolean click = ui.clicked("relay:tool:" + m.id + ":" + a, bx, y + 1, bs, bs);
			float hv = ui.anim("relay:tool:" + m.id + ":" + a + "#h", over, 16f);
			c.round(bx + 1, y + 2, bs - 2, bs - 2, 7, Theme.alpha(a == 3 ? 0xFFEF4444 : 0xFFFFFFFF, hv * (a == 3 ? 0.18f : 0.1f)));
			c.icon(buttons.get(i)[1], 13.5f, bx + bs / 2, y + 1 + bs / 2, a == 3 ? Theme.mix(Theme.TEXT_SECONDARY, 0xFFF87171, hv) : Theme.mix(Theme.TEXT_SECONDARY, Theme.TEXT_STRONG, hv));
			if (over) {
				ui.cursorHand = true;
				ui.tip(tips[a]);
			}
			if (click) {
				if (a == 0) {
					reactFor = m.id.equals(reactFor) ? null : m.id;
					if (reactFor != null) { // the picker opens in this same frame, so it needs the row now
						pickRow[0] = x;
						pickRow[1] = y;
						pickRow[2] = w;
						pickRowSet = true;
					}
				} else if (a == 1) {
					replyTo = m;
					editingId = null;
					input.focus(ui);
				} else if (a == 2) {
					editingId = m.id;
					replyTo = null;
					input.set(m.content == null ? "" : m.content);
					input.focus(ui);
				} else {
					if (m.id.equals(editingId)) {
						editingId = null;
						input.clear();
					}
					client.delete(selected, m.id);
				}
			}
		}
	}

	/** The row of emoji above a message's tools; picking one toggles your reaction. */
	private void picker(Ui ui, RelayClient client, float tx, float ty, float tw, float top, float viewH) {
		Canvas c = ui.c;
		float cell = 32, pad = 6, w = PALETTE.length * cell + pad * 2, h = cell + pad * 2;
		float x = tx + tw - w, y = ty - h - 6;
		if (y < top + 2) {
			y = ty + 30;
		}
		x = Math.max(8, x);
		pickerRect = new float[] {x, y, w, h};
		c.shadow(x, y + 4, w, h, 14, 18, 0x99000000);
		c.round(x, y, w, h, 14, 0xFF14151A);
		c.outline(x, y, w, h, 14, 1, Theme.HAIRLINE_STRONG);
		boolean outside = true;
		for (int i = 0; i < PALETTE.length; i++) {
			float bx = x + pad + i * cell;
			boolean over = ui.hover(bx, y + pad, cell, cell);
			if (over) {
				outside = false;
			}
			float hv = ui.anim("relay:pick:" + i + "#h", over, 18f);
			float size = 20 + 5 * Math.round(hv * 3) / 3f;
			c.round(bx + 1, y + pad + 1, cell - 2, cell - 2, 9, Theme.alpha(0xFFFFFFFF, 0.08f * hv));
			drawEmoji(c, PALETTE[i], bx + (cell - size) / 2, y + pad + (cell - size) / 2 - hv, size);
			if (over) {
				ui.cursorHand = true;
			}
			if (ui.clicked("relay:pick:" + i, bx, y + pad, cell, cell)) {
				client.react(selected, reactFor, PALETTE[i]);
				reactFor = null;
				return;
			}
		}
		boolean inTools = toolbarRect != null && ui.hover(toolbarRect[0], toolbarRect[1], toolbarRect[2], toolbarRect[3]);
		if (ui.pressed && outside && !ui.hover(x, y, w, h) && !inTools) {
			reactFor = null;
		}
	}

	// ── pictures ──────────────────────────────────────────────────────────

	/** Draws a picture message (or its loading box); returns its height. */
	/** Laid-out size of every picture seen (key -> {w, h}, h = -1 failed), so heights never change while scrolling. */
	private final Map<String, float[]> mediaSizes = new java.util.LinkedHashMap<String, float[]>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
			return size() > 2000;
		}
	};

	private float media(Ui ui, RelayClient client, Model.Message m, float x, float y, float maxW, float top, float viewH) {
		Canvas c = ui.c;
		// our own upload keeps its local copy (no reload when the server's copy replaces it) while the cache still has it
		String key = m.localKey != null && (m.mediaUrl == null || ChatMedia.peek(m.localKey) != null) ? m.localKey : m.mediaUrl;
		float[] known = mediaSizes.get(key);
		if (known == null && m.localKey != null && !m.localKey.equals(key)) {
			known = mediaSizes.get(m.localKey);
		}
		float dw = known != null ? known[0] : 240, dh = known != null ? (known[1] < 0 ? 54 : known[1]) : 150;
		// Only pictures on (or near) the screen are fetched: asking for every picture of a long chat each frame made
		// the media cache evict and re-download them in a loop, and the rows jumped between placeholder and real size.
		boolean near = visible(y - viewH, dh + viewH * 2, top, viewH);
		ChatMedia.Media md = near ? ChatMedia.get(key, client.api()) : ChatMedia.peek(key);
		if (md != null && md.failed) {
			mediaSizes.put(key, new float[] {240, -1});
			dw = 240;
			dh = 54;
		} else if (md != null && md.ready() && md.width > 0) {
			float k = Math.min(1f, Math.min(maxW / md.width, 300f / md.height));
			float up = md.width < 160 && md.height < 160 ? 1f : k;
			dw = md.width * up;
			dh = md.height * up;
			if (dw > maxW) {
				dh *= maxW / dw;
				dw = maxW;
			}
			mediaSizes.put(key, new float[] {dw, dh});
			if (m.mediaUrl != null && !m.mediaUrl.equals(key)) {
				mediaSizes.put(m.mediaUrl, new float[] {dw, dh});
			}
		}
		if (md == null || !visible(y, dh, top, viewH)) {
			return dh;
		}
		if (md.failed) {
			c.round(x, y, 240, 54, 10, 0xFF121318);
			c.outline(x, y, 240, 54, 10, 1, Theme.HAIRLINE);
			c.text(Fonts.MEDIUM, 11.5f, "Picture unavailable", x + 14, y + 19, Theme.TEXT_MUTED);
			if (m.mediaUrl != null && ui.interactive && ui.my >= top && ui.my <= top + viewH) {
				if (ui.hover(x, y, 240, 54)) {
					ui.cursorHand = true;
				}
				if (ui.clicked("relay:img:fail:" + (m.id != null ? m.id : key), x, y, 240, 54)) {
					Ads.open(m.mediaUrl);
				}
			}
			return 54;
		}
		Object frame = md.frame(ui.now);
		if (frame == null) {
			shimmer(ui, x, y, dw, dh, 10);
			return dh;
		}
		float a = m.pending ? 0.55f : 1f;
		c.pushClip(x, y, dw, dh);
		c.image((xyz.nativelaunch.ui.gfx.Image) frame, x, y, dw, dh, 0, 0, 1, 1, Theme.alpha(0xFFFFFFFF, a));
		c.popClip();
		c.outline(x, y, dw, dh, 10, 1, Theme.HAIRLINE);
		if (m.pending) {
			ui.dots(x + dw / 2, y + dh / 2, Theme.TEXT_STRONG);
		}
		float[] jr = jumpRect; // the "Jump to latest" pill floats over the messages: clicks on it are not for a picture
		boolean underPill = jr != null && ui.mx >= jr[0] && ui.mx < jr[0] + jr[2] && ui.my >= jr[1] && ui.my < jr[1] + jr[3];
		if (viewer == null && !gifOpen && !underPill && ui.interactive && ui.my >= top && ui.my <= top + viewH) {
			if (ui.hover(x, y, dw, dh)) {
				ui.cursorHand = true;
			}
			if (ui.clicked("relay:img:" + (m.id != null ? m.id : key), x, y, dw, dh)) {
				viewer = md;
				viewerUrl = m.mediaUrl;
			}
		}
		return dh;
	}

	private void lightbox(Ui ui, float W, float H) {
		Canvas c = ui.c;
		ui.interactive = true;
		c.fill(0, 0, W, H, 0xE6000000);
		Object frame = viewer != null ? viewer.frame(ui.now) : null;
		if (frame != null && viewer.width > 0) {
			float k = Math.min((W - 80) / viewer.width, (H - 120) / viewer.height);
			k = Math.min(k, 4f);
			float dw = viewer.width * k, dh = viewer.height * k;
			c.image((xyz.nativelaunch.ui.gfx.Image) frame, (W - dw) / 2, (H - dh) / 2 - 14, dw, dh, 0, 0, 1, 1, 0xFFFFFFFF);
		}
		c.text(Fonts.MEDIUM, 11.5f, "Click anywhere or press Esc to close", (W - c.textWidth(Fonts.MEDIUM, 11.5f, "Click anywhere or press Esc to close")) / 2, H - 28, Theme.TEXT_SECONDARY);
		if (viewerUrl != null && (viewerUrl.startsWith("http://") || viewerUrl.startsWith("https://"))) {
			float bw = 130, bh = 28, bx = (W - bw) / 2, by = H - 64;
			if (ui.button("relay:lightbox:open", bx, by, bw, bh, "Open in browser", Theme.I_GLOBE, Ui.BTN_GLASS)) {
				Ads.open(viewerUrl);
			}
		}
		if (ui.pressed && !ui.hover((W - 140) / 2, H - 70, 140, 36)) {
			viewer = null;
			viewerUrl = null;
			ui.pressed = false;
		}
	}

	// ── GIF picker ────────────────────────────────────────────────────────

	private void gifPicker(Ui ui, RelayClient client, float x, float y, float w, float h) {
		Canvas c = ui.c;
		if (ui.pressed && !ui.hover(x, y, w, h) && !ui.hover(x + w - 130, y + h + 8, 130, 60)) {
			gifOpen = false;
			return;
		}
		c.shadow(x, y + 8, w, h, 16, 28, 0xAA000000);
		c.round(x, y, w, h, 16, 0xFF101115);
		c.outline(x, y, w, h, 16, 1, Theme.HAIRLINE_STRONG);
		gifSearch.draw(ui, x + 12, y + 12, w - 24, 36, "Search GIFs", 12.5f);
		String q = gifSearch.value().trim();
		if (gifQuery == null || !q.equals(gifQuery)) {
			if (gifQuery == null) {
				gifTyped = 0;
			} else if (gifTyped == 0) {
				gifTyped = ui.now;
			}
			if (gifQuery == null || ui.now - gifTyped > 350) {
				gifQuery = q;
				gifTyped = 0;
				final int serial = ++gifSerial;
				gifLoading = true;
				gifFailed = false;
				final String query = q;
				new Thread(() -> {
					try {
						List<String[]> r = client.gifs(query);
						if (serial == gifSerial) {
							gifResults = r;
						}
					} catch (Exception e) {
						if (serial == gifSerial) {
							gifFailed = true;
							gifResults = new ArrayList<String[]>();
						}
					} finally {
						if (serial == gifSerial) {
							gifLoading = false;
						}
					}
				}, "Native-Gifs").start();
			}
		}
		float gy = y + 58, gh = h - 58 - 8;
		List<String[]> res = gifResults;
		float off = gifScroll.begin(ui, x, gy, w, gh);
		float cw = (w - 12 * 2 - 8) / 2;
		if (res == null || (gifLoading && res.isEmpty())) {
			for (int i = 0; i < 6; i++) {
				shimmer(ui, x + 12 + (i % 2) * (cw + 8), gy + (i / 2) * 108 - off, cw, 100, 10);
			}
		} else if (res.isEmpty()) {
			String t = gifFailed ? "GIF search is unavailable" : "No GIFs found";
			c.text(Fonts.MEDIUM, 12, t, x + (w - c.textWidth(Fonts.MEDIUM, 12, t)) / 2, gy + 60, Theme.TEXT_MUTED);
		} else {
			float[] colY = {gy - off, gy - off};
			int shown = 0;
			for (String[] g : res) {
				int col = colY[0] <= colY[1] ? 0 : 1;
				ChatMedia.Media md = ChatMedia.get(g[1], client.api());
				float tw = cw, th = md.ready() && md.width > 0 ? Math.max(50, Math.min(180, cw * md.height / md.width)) : 100;
				float tx = x + 12 + col * (cw + 8), ty = colY[col];
				if (ty + th >= gy && ty <= gy + gh) {
					Object f = md.frame(ui.now);
					if (f == null) {
						shimmer(ui, tx, ty, tw, th, 10);
					} else {
						c.pushClip(tx, ty, tw, th);
						c.imageCover((xyz.nativelaunch.ui.gfx.Image) f, tx, ty, tw, th, 1f, 0, 0, 0xFFFFFFFF);
						c.popClip();
						c.outline(tx, ty, tw, th, 10, 1, Theme.HAIRLINE);
						if (ui.hover(tx, ty, tw, th) && ui.my >= gy && ui.my <= gy + gh) {
							c.round(tx, ty, tw, th, 10, 0x22FFFFFF);
							ui.cursorHand = true;
						}
					}
					if (f != null && ui.my >= gy && ui.my <= gy + gh && ui.clicked("relay:gif:" + g[0], tx, ty, tw, th)) {
						client.sendGif(selected, g[0], g[2]);
						gifOpen = false;
						chatScrolls.get(selected).toBottom();
					}
				}
				colY[col] += th + 8;
				shown++;
			}
			gifScroll.end(ui, Math.max(colY[0], colY[1]) + off - gy + 4);
			c.popClip();
			return;
		}
		gifScroll.end(ui, 0);
	}

	// ── skeletons ─────────────────────────────────────────────────────────

	private static void shimmer(Ui ui, float x, float y, float w, float h, float r) {
		ui.c.skeleton(x, y, w, h, r);
	}

	private void skeletonRows(Ui ui, float x, float y, float w, int n) {
		for (int i = 0; i < n; i++) {
			float ry = y + i * 56;
			shimmer(ui, x + 9, ry + 9, 36, 36, 10);
			float lw = 70 + ((i * 37) % 60);
			shimmer(ui, x + 56, ry + 12, Math.min(lw, w - 70), 11, 5);
			shimmer(ui, x + 56, ry + 31, Math.min(lw + 50, w - 70), 9, 4);
		}
	}

	private void skeletonMessages(Ui ui, float x, float y, float w, float h) {
		float cy = y;
		for (int i = 0; cy < y + h - 40 && i < 8; i++) {
			shimmer(ui, x, cy + 1, 34, 34, 10);
			shimmer(ui, x + 46, cy, 80 + (i * 29) % 50, 11, 5);
			int lines = 1 + (i * 7) % 3;
			for (int l = 0; l < lines; l++) {
				shimmer(ui, x + 46, cy + 22 + l * 18, Math.min(w - 60, 120 + ((i + l) * 71) % 260), 10, 5);
			}
			cy += 36 + lines * 18 + 12;
		}
	}

	private List<String> wrapped(Canvas c, Model.Message m, float width, int face, float size) {
		String key = m.id;
		Object[] hit = wrapCache.get(key);
		if (hit != null && ((Float) hit[0]) == width && ((Float) hit[1]) == c.scale && hit[2].equals(m.content)) {
			@SuppressWarnings("unchecked")
			List<String> lines = (List<String>) hit[3];
			return lines;
		}
		List<String> lines = TextLayout.wrap(c, face, size, m.content == null ? "" : m.content, width);
		if (wrapCache.size() > 4000) {
			wrapCache.clear();
		}
		wrapCache.put(key, new Object[] {width, c.scale, m.content == null ? "" : m.content, lines});
		return lines;
	}

	private static boolean visible(float y, float h, float top, float viewH) {
		return y + h >= top && y <= top + viewH;
	}

	private static boolean eq(String a, String b) {
		return a != null && a.equals(b);
	}

	private static boolean sameDay(long a, long b) {
		Calendar ca = Calendar.getInstance(), cb = Calendar.getInstance();
		ca.setTimeInMillis(a);
		cb.setTimeInMillis(b);
		return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
	}

	private String dayLabel(long t) {
		long now = System.currentTimeMillis();
		if (sameDay(t, now)) {
			return "Today";
		}
		if (sameDay(t, now - 86_400_000L)) {
			return "Yesterday";
		}
		return dayFmt.format(new Date(t));
	}

	// ── settings popover ─────────────────────────────────────────────────

	private void settings(Ui ui, float px, float py, float sw) {
		Canvas c = ui.c;
		UiConfig cfg = UiRuntime.config();
		float w = 300, h = 236;
		float x = px + 16, y = py + 54;
		if (ui.pressed && !ui.hover(x, y, w, h) && !ui.hover(px + sw - 46, py + 16, 30, 30)) {
			settingsOpen = false;
			bindingKey = false;
			return;
		}
		c.shadow(x, y + 6, w, h, 14, 26, 0xAA000000);
		c.round(x, y, w, h, 14, 0xFF101115);
		c.outline(x, y, w, h, 14, 1, Theme.HAIRLINE_STRONG);
		c.text(Fonts.SEMIBOLD, 13.5f, "Chat settings", x + 16, y + 14, Theme.TEXT_STRONG);
		float ry = y + 46;
		ry = settingRow(ui, x, ry, w, "Native title screen", "Replace the vanilla main menu", "set:title", cfg.customTitle, () -> {
			cfg.customTitle = !cfg.customTitle;
			cfg.save();
		});
		ry = settingRow(ui, x, ry, w, "Message pop-ups", "Show new messages while playing", "set:toasts", cfg.notifications, () -> {
			cfg.notifications = !cfg.notifications;
			cfg.save();
		});
		// key
		c.text(Fonts.MEDIUM, 12.5f, "Open chat key", x + 16, ry + 4, Theme.TEXT);
		String label = bindingKey ? "Press a key\u2026" : UiRuntime.keyName(cfg.relayKey);
		float bw = Math.max(70, c.textWidth(Fonts.SEMIBOLD, 12, label) + 24);
		ui.interactive = true;
		if (ui.button("set:key", x + w - 16 - bw, ry - 2, bw, 30, label, 0, Ui.BTN_GLASS)) {
			bindingKey = !bindingKey;
		}
		ry += 44;
		c.text(Fonts.MEDIUM, 12.5f, "Interface size", x + 16, ry + 4, Theme.TEXT);
		String pct = Math.round(cfg.scale * 100) + "%";
		float pw = c.textWidth(Fonts.SEMIBOLD, 12, pct);
		float bx = x + w - 16 - 30;
		if (ui.button("set:bigger", bx, ry - 2, 30, 30, null, Theme.I_PLUS, Ui.BTN_GLASS)) {
			cfg.scale = Math.min(2f, Math.round((cfg.scale + 0.1f) * 10) / 10f);
			cfg.save();
		}
		c.text(Fonts.SEMIBOLD, 12, pct, bx - 12 - pw, ry + 13 - c.lineHeight(Fonts.SEMIBOLD, 12) / 2, Theme.TEXT_STRONG);
		if (ui.button("set:smaller", bx - 24 - pw - 30, ry - 2, 30, 30, "\u2212", 0, Ui.BTN_GLASS)) {
			cfg.scale = Math.max(0.6f, Math.round((cfg.scale - 0.1f) * 10) / 10f);
			cfg.save();
		}
	}

	private float settingRow(Ui ui, float x, float y, float w, String title, String sub, String id, boolean on, Runnable toggle) {
		Canvas c = ui.c;
		ui.interactive = true;
		c.text(Fonts.MEDIUM, 12.5f, title, x + 16, y - 2, Theme.TEXT);
		c.text(Fonts.REGULAR, 10.5f, sub, x + 16, y + 16, Theme.TEXT_MUTED);
		if (ui.toggle(id, x + w - 16 - 34, y + 4, on)) {
			toggle.run();
		}
		return y + 46;
	}
}
