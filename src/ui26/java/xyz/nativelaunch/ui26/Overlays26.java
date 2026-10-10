package xyz.nativelaunch.ui26;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import xyz.nativelaunch.ui.mod.Overlays;
import xyz.nativelaunch.ui.mod.Rich;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Scoreboard sidebar + boss bar readers for 26.x (official names; identical across 26.1 - 26.3). */
final class Overlays26 {
	private static final int RED_NUMBER = 0xFFFF5555;
	private static boolean sidebarWarned;
	private static Field events;

	private Overlays26() {
	}

	private static float width(Component c) {
		Font f = Minecraft.getInstance().font;
		return f == null || c == null ? 0 : f.width(c);
	}

	static void rich(FormattedText text, Rich out, int base) {
		out.clear();
		if (text == null) {
			return;
		}
		text.visit((Style style, String s) -> {
			if (!s.isEmpty()) {
				TextColor c = style.getColor();
				out.add(s, c == null ? base : 0xFF000000 | c.getValue());
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	static boolean sidebar(MojangMc mc, Overlays.Sidebar out) {
		out.has = false;
		out.count = 0;
		try {
			Minecraft c = Minecraft.getInstance();
			if (c.level == null) {
				return true;
			}
			Scoreboard board = c.level.getScoreboard();
			Objective objective = board == null ? null : board.getDisplayObjective(DisplaySlot.SIDEBAR);
			if (objective == null) {
				return true;
			}
			Component title = objective.getDisplayName();
			rich(title, out.title, 0xFFFFFFFF);
			float widest = width(title), colon = width(Component.literal(": "));
			List<PlayerScoreEntry> rows = new ArrayList<PlayerScoreEntry>();
			Collection<PlayerScoreEntry> all = board.listPlayerScores(objective);
			for (PlayerScoreEntry e : all) {
				if (!e.isHidden() && e.owner() != null && !e.owner().startsWith("#")) {
					rows.add(e);
				}
			}
			rows.sort((a, b) -> {
				int d = Integer.compare(b.value(), a.value());
				return d != 0 ? d : String.CASE_INSENSITIVE_ORDER.compare(a.owner(), b.owner());
			});
			NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
			int n = Math.min(15, rows.size());
			for (int i = 0; i < n; i++) {
				PlayerScoreEntry e = rows.get(i);
				PlayerTeam team = board.getPlayersTeam(e.owner());
				Component shown = PlayerTeam.formatNameForTeam(team, e.ownerName());
				rich(shown, out.names[i], 0xFFFFFFFF);
				Component num = e.formatValue(format);
				rich(num, out.scores[i], RED_NUMBER);
				float sw = width(num);
				widest = Math.max(widest, width(shown) + (sw > 0 ? colon + sw : 0));
			}
			out.count = n;
			out.vanillaW = widest;
			out.has = true;
			return true;
		} catch (Throwable t) {
			out.has = false;
			out.count = 0;
			if (!sidebarWarned) {
				sidebarWarned = true;
				System.err.println("[NativeSidebar] can't read the scoreboard: " + t);
			}
			return false;
		}
	}

	static boolean bossBars(MojangMc mc, Overlays.Bars out) {
		out.count = 0;
		try {
			Object hud = mc.hudObject();
			if (hud == null) {
				return true;
			}
			Method get = hud.getClass().getMethod("getBossOverlay");
			Object overlay = get.invoke(hud);
			if (overlay == null) {
				return true;
			}
			if (events == null) {
				events = overlay.getClass().getDeclaredField("events");
				events.setAccessible(true);
			}
			Object map = events.get(overlay);
			if (!(map instanceof Map)) {
				return false;
			}
			for (Object o : ((Map<?, ?>) map).values()) {
				if (out.count >= out.names.length) {
					break;
				}
				BossEvent bar = (BossEvent) o;
				int i = out.count;
				Component name = bar.getName();
				rich(name, out.names[i], 0xFFFFFFFF);
				out.nameW[i] = width(name);
				out.progress[i] = bar.getProgress();
				out.color[i] = bar.getColor() == null ? 0 : bar.getColor().ordinal();
				out.ids[i] = bar;
				out.count++;
			}
			return true;
		} catch (Throwable t) {
			out.count = 0;
			return false;
		}
	}
}
