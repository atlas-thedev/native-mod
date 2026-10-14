package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Ads;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.ChatMedia;
import xyz.nativelaunch.ui.ClipboardImage;
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
	private int tab; // 0 friends, 1 groups
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
	private final SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);

	public RelayView() {
		input.maxLength = 2000;
		input.bare = true;
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
		sidebar(ui, client, px, py, sw, ph, screen);
		c.fill(px + sw, py, 1, ph, Theme.HAIRLINE);
		chat(ui, client, px + sw + 1, py, pw - sw - 1, ph, screen);
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

	// ── left column ───────────────────────────────────────────────────────

	private void sidebar(Ui ui, RelayClient client, float x, float y, float w, float h, Object screen) {
		Canvas c = ui.c;
		float pad = 16;
		c.text(Fonts.BOLD, 19, "Relay", x + pad, y + 18, Theme.TEXT_STRONG);
		if (client != null) {
			RelayClient.State st = client.state;
			int dot = st == RelayClient.State.ONLINE ? Theme.ONLINE : (st == RelayClient.State.CONNECTING ? Theme.IDLE : 0xFF71717A);
			String label = st == RelayClient.State.ONLINE ? "Live" : st == RelayClient.State.CONNECTING ? "Connecting" : "Offline";
			float tx = x + pad + c.textWidth(Fonts.BOLD, 19, "Relay") + 10;
			float lw = c.textWidth(Fonts.SEMIBOLD, 10, label) + 22;
			float ly = y + 18 + c.lineHeight(Fonts.BOLD, 19) / 2 - 10;
			c.round(tx, ly, lw, 20, 10, Theme.alpha(dot, 0.14f));
			c.circle(tx + 9, ly + 10, 3, dot);
			c.text(Fonts.SEMIBOLD, 10, label, tx + 16, ly + (20 - c.lineHeight(Fonts.SEMIBOLD, 10)) / 2, Theme.mix(dot, 0xFFFFFFFF, 0.35f));
		}
		if (ui.iconButton("relay:settings", x + w - pad - 30, y + 16, 30, Theme.I_SETTINGS, "Chat settings")) {
			settingsOpen = !settingsOpen;
		}
		float ty = y + 60;
		// tabs
		float tw = (w - pad * 2) / 2;
		c.round(x + pad, ty, w - pad * 2, 34, 10, Theme.SUBTLE);
		float tabPos = ui.anim("relay:tab", tab, 16f);
		c.shadow(x + pad + 3 + tabPos * tw, ty + 4, tw - 6, 28, 8, 6, 0x66000000);
		c.round(x + pad + 3 + tabPos * tw, ty + 3, tw - 6, 28, 8, 0xFF1F2027);
		int fu = 0, gu = 0;
		if (client != null) {
			for (Model.Friend f : client.friends) {
				fu += f.unread;
			}
			for (Model.Group g : client.groups) {
				gu += g.unread;
			}
		}
		for (int i = 0; i < 2; i++) {
			String label = i == 0 ? "Friends" : "Groups";
			int n = i == 0 ? fu : gu;
			float bx = x + pad + i * tw;
			if (ui.clicked("relay:tab" + i, bx, ty, tw, 34)) {
				tab = i;
			}
			float lw = c.textWidth(Fonts.SEMIBOLD, 12.5f, label) + (n > 0 ? 24 : 0);
			int col = tab == i ? Theme.TEXT_STRONG : Theme.TEXT_MUTED;
			c.text(Fonts.SEMIBOLD, 12.5f, label, bx + (tw - lw) / 2, ty + 17 - c.lineHeight(Fonts.SEMIBOLD, 12.5f) / 2, col);
			if (n > 0) {
				TitleView.badge(c, bx + (tw + lw) / 2, ty + 17, n);
			}
		}
		ty += 46;
		search.draw(ui, x + pad, ty, w - pad * 2, 36, tab == 0 ? "Search friends" : "Search groups", 12.5f);
		ty += 46;
		float listH = y + h - ty - 8;
		if (client == null || client.state == RelayClient.State.NO_ACCOUNT) {
			TitleView.empty(c, x, ty, w, listH, Theme.I_LOCK, "Not signed in", "Start Minecraft from the Native Client to use Relay in game.");
			return;
		}
		String q = search.value().trim().toLowerCase(Locale.ROOT);
		float off = listScroll.begin(ui, x, ty, w, listH);
		float ry = ty - off;
		float rh = 54;
		int shown = 0;
		if (tab == 0) {
			if (!client.friendsLoaded) {
				skeletonRows(ui, x + 8, ty, w - 16, 7);
			}
			for (Model.Friend f : client.friends) {
				if (!q.isEmpty() && !f.display().toLowerCase(Locale.ROOT).contains(q) && !f.name.toLowerCase(Locale.ROOT).contains(q)) {
					continue;
				}
				String key = "dm:" + f.id;
				if (ry + rh >= ty && ry <= ty + listH) {
					String sub = f.line();
					rowTyping = !client.typing(key).isEmpty();
					rowPinned = f.pinned;
					rowDot = f.dot();
					int unread = key.equals(client.viewing) ? 0 : f.unread;
					if (row(ui, client, key, x + 8, ry, w - 16, rh, f.display(), f.name, f.skin, sub, unread, f.online ? 1 : 0)) {
						select(key, client);
						input.focus(ui);
					}
				}
				ry += rh + 2;
				shown++;
			}
			if (shown == 0 && client.friendsLoaded) {
				TitleView.empty(c, x, ty, w, Math.min(listH, 220), Theme.I_USER_PLUS, q.isEmpty() ? "No friends yet" : "No matches", q.isEmpty() ? "Add friends in the launcher's Relay tab." : "Try another name.");
			}
		} else {
			for (Model.Group g : client.groups) {
				if (!q.isEmpty() && (g.name == null || !g.name.toLowerCase(Locale.ROOT).contains(q))) {
					continue;
				}
				String key = "g:" + g.id;
				if (ry + rh >= ty && ry <= ty + listH) {
					String sub = g.lastText == null ? g.members + " members" : (g.lastSender == null ? "" : g.lastSender + ": ") + (g.lastText.isEmpty() ? "Sent a picture" : g.lastText);
					rowDot = 0;
					rowTyping = !client.typing(key).isEmpty();
					rowPinned = g.pinned;
					int unread = key.equals(client.viewing) ? 0 : g.unread;
					if (row(ui, client, key, x + 8, ry, w - 16, rh, g.name, g.name, null, sub, unread, -1)) {
						select(key, client);
						input.focus(ui);
					}
				}
				ry += rh + 2;
				shown++;
			}
			if (shown == 0) {
				if (!client.groupsLoaded) {
					skeletonRows(ui, x + 8, ty, w - 16, 7);
				} else {
					TitleView.empty(c, x, ty, w, Math.min(listH, 220), Theme.I_USERS, q.isEmpty() ? "No groups" : "No matches", q.isEmpty() ? "Create a group in the launcher and it shows up here." : "Try another name.");
				}
			}
		}
		listScroll.end(ui, ry + off - ty + 8);
	}

	private int rowDot;

	/** presence: 1 online, 0 offline, -1 group (no dot) */
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
		c.round(x, y, w, h, 12, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv * 0.8f), 0xFF17181E, sv));
		if (sv > 0.01f) {
			c.outline(x, y, w, h, 12, 1, Theme.alpha(Theme.HAIRLINE_STRONG, sv));
			c.round(x - 4, y + (h - 22 * sv) / 2, 3, 22 * sv, 1.5f, Theme.alpha(Theme.WHITE, sv));
		}
		float av = 36;
		float ax = x + 9, ay = y + (h - av) / 2;
		if (presence == -1) {
			c.round(ax, ay, av, av, 10, Theme.mix(Theme.nameColor(title), 0xFF000000, 0.45f));
			c.icon(Theme.I_HASH, 17, ax + av / 2, ay + av / 2, 0xFFFFFFFF);
		} else {
			c.pushAlpha(presence == 1 ? 1f : 0.55f);
			Avatars.draw(c, client.api(), avatarName, skin, ax, ay, av, 10);
			c.popAlpha();
			c.circle(ax + av - 2, ay + av - 2, 6, sel ? 0xFF16171C : 0xFF08090C);
			c.circle(ax + av - 2, ay + av - 2, 4, rowDot != 0 ? rowDot : (presence == 1 ? Theme.ONLINE : 0xFF52525B));
		}
		float tx = ax + av + 11;
		float maxW = x + w - tx - 10 - (unread > 0 ? 28 : 0);
		float titleW = maxW - (rowPinned ? 18 : 0);
		String shownTitle = c.ellipsize(Fonts.SEMIBOLD, 13, title, titleW);
		c.text(Fonts.SEMIBOLD, 13, shownTitle, tx, y + 9, presence == 0 ? Theme.TEXT_SECONDARY : Theme.TEXT_STRONG);
		if (rowPinned) {
			c.icon(Theme.I_MAP_PIN, 11, tx + c.textWidth(Fonts.SEMIBOLD, 13, shownTitle) + 11, y + 9 + c.lineHeight(Fonts.SEMIBOLD, 13) / 2, Theme.TEXT_MUTED);
		}
		if (rowTyping) {
			float tw2 = c.text(Fonts.MEDIUM, 11, "typing", tx, y + 29, Theme.ONLINE);
			ui.dots(tx + tw2 + 14, y + 29 + c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.ONLINE);
		} else {
			c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, sub, maxW), tx, y + 29, unread > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
		}
		if (unread > 0) {
			TitleView.badge(c, x + w - 10, y + h / 2, unread);
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
		if (client == null || selected == null) {
			TitleView.empty(c, x, y, w, h, Theme.I_MESSAGE, "Pick a chat", "Choose a friend or a group on the left. Press " + UiRuntime.keyName(UiRuntime.config().relayKey) + " in game to open Relay any time.");
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
