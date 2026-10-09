package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.relay.Model;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Avatars;
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
	private Object lastScreen;
	private boolean settingsOpen, bindingKey;
	private int lastCount = -1;
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
				ui.dots(x + w / 2, ty + 40, Theme.TEXT_SECONDARY);
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
					String sub = g.lastText == null ? g.members + " members" : (g.lastSender == null ? "" : g.lastSender + ": ") + g.lastText;
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
					ui.dots(x + w / 2, ty + 40, Theme.TEXT_SECONDARY);
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
			} else if (sc.offset < 30) {
				client.loadOlder(selected);
			}
			cy += 26;
		}
		float before = cy;
		if (!conv.loaded && conv.loading) {
			ui.dots(x + w / 2, my0 + mh / 2, Theme.TEXT_SECONDARY);
		} else if (conv.loaded && messages.isEmpty()) {
			TitleView.empty(c, x, my0, w, mh, Theme.I_SMILE, "Say hi to " + name, "This is the beginning of your conversation.");
		} else if (conv.error != null && messages.isEmpty()) {
			TitleView.empty(c, x, my0, w, mh, Theme.I_WIFI_OFF, "Couldn't load messages", conv.error);
		}
		Model.Message prev = null;
		float lh = 19;
		for (Model.Message m : messages) {
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
					}
					Avatars.draw(c, client.api(), sf != null ? sf.name : who, skin, x + pad, cy + 1, 34, 10);
					boolean mine = eq(m.senderId, client.meId);
					float nw = c.text(Fonts.SEMIBOLD, 13, who, textX, cy, mine ? Theme.TEXT_STRONG : Theme.mix(Theme.nameColor(who), 0xFFFFFFFF, 0.55f));
					c.text(Fonts.REGULAR, 10.5f, timeFmt.format(new Date(m.createdAt)), textX + nw + 8, cy + 2.5f, Theme.TEXT_MUTED);
				}
				cy += 20;
			}
			List<String> lines = wrapped(c, m, textW, Fonts.REGULAR, 13);
			int color = m.deleted ? Theme.TEXT_MUTED : (m.pending ? Theme.alpha(Theme.TEXT, 0.5f) : Theme.TEXT);
			for (String line : lines) {
				if (visible(cy, lh, my0, mh)) {
					c.text(Fonts.REGULAR, 13, line, textX, cy, color);
				}
				cy += lh;
			}
			if (m.failed) {
				c.text(Fonts.MEDIUM, 10.5f, "Not sent \u2014 check your connection", textX, cy, 0xFFF87171);
				cy += 16;
			}
			cy += 2;
			prev = m;
		}
		float content = cy - before + (before - (my0 - off)) + 10;
		sc.end(ui, content);
		if (messages.size() != lastCount) {
			if (lastCount == -1) {
				sc.snapBottom();
			} else if (!messages.isEmpty() && (eq(messages.get(messages.size() - 1).senderId, client.meId) || sc.atBottom())) {
				sc.toBottom();
			}
			lastCount = messages.size();
		}

		// typing + input
		List<String> typers = client.typing(selected);
		float iy = y + h - inputH - 16;
		if (!typers.isEmpty()) {
			String t = typers.size() == 1 ? typers.get(0) + " is typing" : typers.size() + " people are typing";
			ui.dots(x + pad + 12, iy - 12, Theme.TEXT_SECONDARY);
			c.text(Fonts.MEDIUM, 11, t, x + pad + 30, iy - 12 - c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.TEXT_SECONDARY);
		}
		if (!ui.focusClaimed && ui.focus == null && !settingsOpen && search.value().isEmpty()) {
			input.focus(ui);
		}
		String before2 = input.value();
		boolean send = input.draw(ui, x + pad, iy, w - pad * 2 - 54, inputH, "Message " + (group ? "#" + name : "@" + name), 13.5f);
		if (!input.value().equals(before2) && !input.value().isEmpty()) {
			client.typingPing(selected);
		}
		boolean canSend = !input.value().trim().isEmpty();
		float sx = x + w - pad - 46;
		boolean over = ui.hover(sx, iy, 46, inputH);
		float hv = ui.anim("relay:send", over && canSend, 14f);
		boolean click = ui.clicked("relay:sendbtn", sx, iy, 46, inputH);
		c.round(sx, iy, 46, inputH, 12, canSend ? Theme.mix(0xFFE4E4E7, 0xFFFFFFFF, hv) : Theme.SUBTLE);
		c.icon(Theme.I_SEND, 18, sx + 23, iy + inputH / 2, canSend ? Theme.SOLID_FG : Theme.TEXT_MUTED);
		if ((send || click) && canSend) {
			client.send(selected, input.value());
			input.clear();
			drafts.remove(selected);
			sc.toBottom();
			input.focus(ui);
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
