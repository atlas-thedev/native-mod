package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
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
	// pasted picture waiting to be sent
	private byte[] staged;
	private String stagedMime, stagedKey, stagedNote;
	private final AtomicBoolean pasting = new AtomicBoolean();
	private volatile byte[] pasted;
	private ChatMedia.Media viewer;
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
		if (viewer != null) {
			viewer = null;
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
		float pw = Math.min(1040, W - 48), ph = Math.min(660, H - 48);
		float px = (W - pw) / 2, py = (H - ph) / 2 + (1 - in) * 18;
		c.pushAlpha(in);
		c.shadow(px, py + 10, pw, ph, 18, 40, 0x99000000);
		c.round(px, py, pw, ph, 18, 0xF708090C);
		c.outline(px, py, pw, ph, 18, 1, Theme.HAIRLINE);

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
		ui.interactive = !settingsOpen;
		float sw = Math.min(300, pw * 0.34f);
		sidebar(ui, client, px, py, sw, ph, screen);
		c.fill(px + sw, py, 1, ph, Theme.HAIRLINE);
		chat(ui, client, px + sw + 1, py, pw - sw - 1, ph, screen);
		ui.interactive = true;
		if (settingsOpen) {
			settings(ui, px, py, sw);
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
			float tx = x + pad + c.textWidth(Fonts.BOLD, 19, "Relay") + 10;
			c.circle(tx + 3, y + 18 + c.lineHeight(Fonts.BOLD, 19) / 2, 3.5f, dot);
		}
		if (ui.iconButton("relay:settings", x + w - pad - 30, y + 16, 30, Theme.I_SETTINGS, "Chat settings")) {
			settingsOpen = !settingsOpen;
		}
		float ty = y + 60;
		// tabs
		float tw = (w - pad * 2) / 2;
		c.round(x + pad, ty, w - pad * 2, 34, 10, Theme.SUBTLE);
		float tabPos = ui.anim("relay:tab", tab, 16f);
		c.round(x + pad + 3 + tabPos * tw, ty + 3, tw - 6, 28, 8, 0xFF1C1D22);
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
					rowDot = f.dot();
					if (row(ui, client, key, x + 8, ry, w - 16, rh, f.display(), f.name, f.skin, sub, f.unread, f.online ? 1 : 0)) {
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
					if (row(ui, client, key, x + 8, ry, w - 16, rh, g.name, g.name, null, sub, g.unread, -1)) {
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
		float hv = ui.anim("row:" + key, over, 14f);
		float sv = ui.anim("rowsel:" + key, sel, 14f);
		c.round(x, y, w, h, 11, Theme.mix(Theme.alpha(Theme.SUBTLE_HOVER, hv), 0xFF16171C, sv));
		if (sv > 0.01f) {
			c.outline(x, y, w, h, 11, 1, Theme.alpha(Theme.HAIRLINE_STRONG, sv));
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
		c.text(Fonts.SEMIBOLD, 13, c.ellipsize(Fonts.SEMIBOLD, 13, title, maxW), tx, y + 9, presence == 0 ? Theme.TEXT_SECONDARY : Theme.TEXT_STRONG);
		c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, sub, maxW), tx, y + 29, unread > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
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
		float my0 = y + 65, mh = h - 65 - inputH - 34;
		Scroll sc = chatScrolls.get(selected);
		if (sc == null) {
			sc = new Scroll();
			sc.stickBottom = true;
			chatScrolls.put(selected, sc);
		}
		List<Model.Message> messages = conv.messages;
		float off = sc.begin(ui, x, my0, w, mh);
		float pad = 20;
		float textX = x + pad + 46;
		float textW = w - pad * 2 - 46 - 10;
		float cy = my0 + 14 - off;
		if (conv.hasMore) {
			if (conv.loading) {
				ui.dots(x + w / 2, cy + 8, Theme.TEXT_MUTED);
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
		for (Model.Message m : messages) {
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
					c.text(Fonts.REGULAR, 10.5f, timeFmt.format(new Date(m.createdAt)), textX + nw + 8, cy + 2.5f, Theme.TEXT_MUTED);
				}
				cy += 20;
			}
			boolean hasMedia = m.mediaUrl != null || m.localKey != null;
			boolean autoText = hasMedia && (m.content == null || m.content.trim().isEmpty() || m.content.startsWith("["));
			int color = m.deleted ? Theme.TEXT_MUTED : (m.pending ? Theme.alpha(Theme.TEXT, 0.5f) : Theme.TEXT);
			if (!autoText) {
				List<String> lines = wrapped(c, m, textW, Fonts.REGULAR, 13);
				for (String line : lines) {
					if (visible(cy, lh, my0, mh)) {
						c.text(Fonts.REGULAR, 13, line, textX, cy, color);
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
			cy += 2;
			if (ui.interactive && viewer == null && !gifOpen && ui.my >= my0 && ui.my <= my0 + mh && ui.hover(x, rowTop, w - 10, cy - rowTop)) {
				c.fill(x, rowTop - 1, w - 10, cy - rowTop + 1, 0x0AFFFFFF);
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
		if (!ui.focusClaimed && ui.focus == null && !settingsOpen && search.value().isEmpty() && !gifOpen) {
			input.focus(ui);
		}
		String before2 = input.value();
		float btn = 46;
		boolean send = input.draw(ui, x + pad, iy, w - pad * 2 - btn * 2 - 16, inputH, staged != null ? "Add a caption (optional)" : "Message " + (group ? "#" + name : "@" + name), 13.5f);
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
		// GIF button
		float gx = x + w - pad - btn * 2 - 8;
		boolean gOver = ui.hover(gx, iy, btn, inputH);
		float gHv = ui.anim("relay:gifbtn", gOver || gifOpen, 14f);
		boolean gClick = ui.clicked("relay:gifbtn", gx, iy, btn, inputH);
		c.round(gx, iy, btn, inputH, 12, Theme.mix(Theme.SUBTLE, 0xFF26272D, gHv));
		float gw = c.textWidth(Fonts.BOLD, 12, "GIF");
		c.text(Fonts.BOLD, 12, "GIF", gx + (btn - gw) / 2, iy + (inputH - c.lineHeight(Fonts.BOLD, 12)) / 2, gifOpen ? Theme.TEXT_STRONG : Theme.TEXT_SECONDARY);
		if (gClick) {
			gifOpen = !gifOpen;
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
		float sx = x + w - pad - btn;
		boolean over = ui.hover(sx, iy, btn, inputH);
		float hv = ui.anim("relay:send", over && canSend, 14f);
		boolean click = ui.clicked("relay:sendbtn", sx, iy, btn, inputH);
		c.round(sx, iy, btn, inputH, 12, canSend ? Theme.mix(0xFFE4E4E7, 0xFFFFFFFF, hv) : Theme.SUBTLE);
		c.icon(Theme.I_SEND, 18, sx + btn / 2, iy + inputH / 2, canSend ? Theme.SOLID_FG : Theme.TEXT_MUTED);
		if ((send || click) && canSend) {
			if (staged != null) {
				client.sendImage(selected, staged, stagedMime);
				staged = null;
			}
			if (!input.value().trim().isEmpty()) {
				client.send(selected, input.value());
			}
			input.clear();
			drafts.remove(selected);
			sc.toBottom();
			input.focus(ui);
		}
		if (gifOpen) {
			gifPicker(ui, client, x + w - pad - 372, iy - 360 - 8, 372, 360);
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
		String key = m.localKey != null ? m.localKey : m.mediaUrl;
		float[] known = mediaSizes.get(key);
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
		}
		if (md == null || !visible(y, dh, top, viewH)) {
			return dh;
		}
		if (md.failed) {
			c.round(x, y, 240, 54, 10, 0xFF121318);
			c.outline(x, y, 240, 54, 10, 1, Theme.HAIRLINE);
			c.text(Fonts.MEDIUM, 11.5f, "Picture unavailable", x + 14, y + 19, Theme.TEXT_MUTED);
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
			if (ui.clicked("relay:img:" + m.id, x, y, dw, dh)) {
				viewer = md;
			}
		}
		return dh;
	}

	private void lightbox(Ui ui, float W, float H) {
		Canvas c = ui.c;
		ui.interactive = true;
		c.fill(0, 0, W, H, 0xE6000000);
		Object frame = viewer.frame(ui.now);
		if (frame != null && viewer.width > 0) {
			float k = Math.min((W - 80) / viewer.width, (H - 100) / viewer.height);
			k = Math.min(k, 4f);
			float dw = viewer.width * k, dh = viewer.height * k;
			c.image((xyz.nativelaunch.ui.gfx.Image) frame, (W - dw) / 2, (H - dh) / 2, dw, dh, 0, 0, 1, 1, 0xFFFFFFFF);
		}
		c.text(Fonts.MEDIUM, 11.5f, "Click anywhere or press Esc to close", (W - c.textWidth(Fonts.MEDIUM, 11.5f, "Click anywhere or press Esc to close")) / 2, H - 34, Theme.TEXT_SECONDARY);
		if (ui.pressed) {
			viewer = null;
			ui.pressed = false;
		}
	}

	// ── GIF picker ────────────────────────────────────────────────────────

	private void gifPicker(Ui ui, RelayClient client, float x, float y, float w, float h) {
		Canvas c = ui.c;
		if (ui.pressed && !ui.hover(x, y, w, h) && !ui.hover(x + w - 56, y + h + 8, 60, 60)) {
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
		float t = (float) ((Math.sin(ui.now / 420.0) + 1) / 2);
		ui.c.round(x, y, w, h, r, Theme.mix(0xFF16171C, 0xFF1F2026, t));
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
