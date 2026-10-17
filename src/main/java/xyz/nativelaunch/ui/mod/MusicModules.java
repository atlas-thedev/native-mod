package xyz.nativelaunch.ui.mod;

import xyz.nativelaunch.music.MusicPlayer;
import xyz.nativelaunch.music.MusicPlayer.Track;
import xyz.nativelaunch.ui.Scroll;
import xyz.nativelaunch.ui.Theme;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.Widgets;
import xyz.nativelaunch.ui.gfx.Canvas;
import xyz.nativelaunch.ui.gfx.Fonts;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.voice.Voice;

import java.util.List;

/** Native Music: the player (settings + the full player on its page) and the Spotify-style "Now Playing" HUD card. */
public final class MusicModules {
	private MusicModules() {
	}

	static void register() {
		Modules.register(new Player());
		Modules.register(new NowPlaying());
	}

	static final int GREEN = 0xFF1ED760;

	/** The accent of the song playing (from its cover), or Native green. */
	static int accent() {
		return MusicPlayer.accent(MusicPlayer.current(), GREEN);
	}

	// ── the player ───────────────────────────────────────────────────────

	static final class Player extends Module {
		final Setting.Num volume = add(new Setting.Num("volume", "Volume", 70, 0, 100, 1, "%"));
		final Setting.Bool shuffle = add(new Setting.Bool("shuffle", "Shuffle", false));
		final Setting.Choice repeat = add(new Setting.Choice("repeat", "Repeat", 1, "Off", "All songs", "One song").shorts("Off", "All", "One"));
		final Setting.Bool duckCalls = add(new Setting.Bool("duckcalls", "Quieter in voice calls", true));
		final Setting.Key playKey = add(new Setting.Key("playkey", "Play / pause key", -1));
		final Setting.Key nextKey = add(new Setting.Key("nextkey", "Next song key", -1));
		final Setting.Key prevKey = add(new Setting.Key("prevkey", "Previous song key", -1));
		final Setting.Key openKey = add(new Setting.Key("openkey", "Open player key", -1));

		private final Scroll list = new Scroll();
		private final float[] bars = new float[40];
		private float seekDrag = -1;

		Player() {
			super("music", "Music Player", "Your own songs in game: drop MP3, OGG or WAV files in the music folder every instance shares, then play them here.",
					MECHANIC, Theme.I_MUSIC, true);
		}

		private void ensure() {
			if (!MusicPlayer.running()) {
				MusicPlayer.init(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
				MusicPlayer.start();
			}
			MusicPlayer.volume = volume.value / 100f;
			MusicPlayer.shuffle = shuffle.value;
			MusicPlayer.repeat = MusicPlayer.Repeat.values()[Math.max(0, Math.min(2, repeat.value))];
			MusicPlayer.duck = duckCalls.value && Voice.call() != null ? 0.3f : 1f;
		}

		@Override
		protected void onEnable() {
			ensure();
		}

		@Override
		protected void onDisable() {
			MusicPlayer.stop();
		}

		@Override
		public void frame(Game g) {
			ensure();
		}

		@Override
		public boolean onKey(int key, int action, Game g) {
			if (action != 1 || key < 0) {
				return false;
			}
			if (key == playKey.value) {
				MusicPlayer.toggle();
				return true;
			}
			if (key == nextKey.value) {
				MusicPlayer.next();
				return true;
			}
			if (key == prevKey.value) {
				MusicPlayer.previous();
				return true;
			}
			if (key == openKey.value && g.playing) {
				UiRuntime.openMod(null, this);
				return true;
			}
			return false;
		}

		/** The whole player, on the module's page. */
		@Override
		public boolean preview(Ui ui, Game g, float x, float y, float w, float h) {
			Canvas c = ui.c;
			if (!enabled) {
				float cy = y + h / 2 - 40;
				c.circle(x + w / 2, cy, 30, Theme.SUBTLE_HOVER);
				c.icon(Theme.I_MUSIC, 24, x + w / 2, cy, Theme.TEXT_STRONG);
				centered(c, Fonts.SEMIBOLD, 14, "Music Player is off", x, w, cy + 44, Theme.TEXT_STRONG);
				centered(c, Fonts.REGULAR, 11.5f, "Turn it on to play your songs in game.", x, w, cy + 64, Theme.TEXT_MUTED);
				if (ui.button("music:on", x + w / 2 - 60, cy + 88, 120, 32, "Turn on", Theme.I_PLAY, Ui.BTN_PRIMARY)) {
					setEnabled(true);
					Modules.changed();
				}
				return true;
			}
			ensure();
			c.fill(x, y, w, h, 0xE60A0B0F);
			float pad = 14;
			Track t = MusicPlayer.current();
			int acc = accent();
			boolean on = MusicPlayer.playing();

			// hero: cover, title, artist and the sound bars, over a glow in the cover's colour
			float heroH = Math.max(112, Math.min(170, h * 0.3f));
			float glow = ui.anim("music:glow", on ? 1f : 0.55f, 4f);
			c.gradientV(x, y, w, heroH + 40, Theme.alpha(acc, 0.42f * glow), 0x00000000);
			float cs = heroH - pad * 2;
			cover(c, t, x + pad, y + pad, cs, acc);
			float tx = x + pad + cs + 16, tw = w - (tx - x) - pad;
			c.text(Fonts.MEDIUM, 10, t == null ? "NOTHING PLAYING" : on ? "NOW PLAYING" : "PAUSED", tx, y + pad + 4, Theme.alpha(0xFFFFFFFF, 0.6f));
			String title = t == null ? "Pick a song" : t.title;
			c.text(Fonts.SEMIBOLD, 18, c.ellipsize(Fonts.SEMIBOLD, 18, title, tw), tx, y + pad + 20, Theme.TEXT_STRONG);
			String sub = t == null ? (MusicPlayer.library().isEmpty() ? "Your music folder is empty" : MusicPlayer.library().size() + " songs ready") : t.subtitle();
			c.text(Fonts.REGULAR, 12, c.ellipsize(Fonts.REGULAR, 12, sub, tw), tx, y + pad + 46, Theme.TEXT_SECONDARY);
			if (t != null && t.album != null && t.artist != null) {
				c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, t.album, tw), tx, y + pad + 64, Theme.TEXT_MUTED);
			}
			float barsTop = y + pad + 82, barsH = y + heroH - pad - barsTop;
			if (barsH > 14) {
				MusicPlayer.bars(bars);
				soundBars(c, bars, tx, barsTop, tw, barsH, acc, on ? 1f : 0.35f);
			}

			// seek bar
			float sy = y + heroH + 6;
			long pos = MusicPlayer.position(), dur = MusicPlayer.duration();
			float sx = x + pad + 40, sw = w - pad * 2 - 80;
			boolean over = ui.hover(sx - 4, sy - 6, sw + 8, 20);
			ui.clicked("music:seek", sx - 4, sy - 6, sw + 8, 20);
			boolean drag = ui.isPressing("music:seek") && dur > 0;
			if (drag) {
				seekDrag = Math.max(0, Math.min(1, (ui.mx - sx) / sw));
			} else if (seekDrag >= 0) {
				MusicPlayer.seek((long) (seekDrag * dur));
				seekDrag = -1;
			}
			float frac = seekDrag >= 0 ? seekDrag : dur > 0 ? Math.min(1, pos / (float) dur) : 0;
			float hv = ui.anim("music:seek#h", over || drag, 14f);
			c.round(sx, sy + 3, sw, 4, 2, Theme.SUBTLE_HOVER);
			c.round(sx, sy + 3, Math.max(4, sw * frac), 4, 2, hv > 0.05f ? acc : 0xFFF4F4F5);
			if (hv > 0.01f || drag) {
				c.circle(sx + sw * frac, sy + 5, 6 * Math.max(hv, drag ? 1 : 0), 0xFFFFFFFF);
			}
			String left = MusicPlayer.time(seekDrag >= 0 ? (long) (seekDrag * dur) : t == null ? -1 : pos), right = MusicPlayer.time(dur);
			c.text(Fonts.MEDIUM, 10.5f, left, sx - 10 - c.textWidth(Fonts.MEDIUM, 10.5f, left), sy, Theme.TEXT_MUTED);
			c.text(Fonts.MEDIUM, 10.5f, right, sx + sw + 10, sy, Theme.TEXT_MUTED);

			// controls
			float cy = sy + 38;
			float cx = x + w / 2;
			if (control(ui, "music:shuffle", cx - 104, cy, 30, Theme.I_SHUFFLE, shuffle.value ? acc : Theme.TEXT_SECONDARY, shuffle.value ? "Shuffle on" : "Shuffle off", shuffle.value)) {
				shuffle.value = !shuffle.value;
				Modules.changed();
			}
			if (control(ui, "music:prev", cx - 56, cy, 34, Theme.I_SKIP_BACK, Theme.TEXT_STRONG, "Previous", false)) {
				MusicPlayer.previous();
			}
			// play / pause: a white disc that breathes a little while music plays
			float pr = 22, press = ui.anim("music:play#p", ui.isPressing("music:play"), 22f);
			boolean pOver = ui.hover(cx - pr, cy - pr, pr * 2, pr * 2);
			if (on) {
				float beat = 0.5f + 0.5f * (float) Math.sin(ui.now / 420.0);
				c.circle(cx, cy, pr + 3 + beat * 2.5f, Theme.alpha(acc, 0.25f));
			}
			c.circle(cx, cy, pr * (1 - 0.06f * press) + (pOver ? 1 : 0), 0xFFFFFFFF);
			c.icon(on ? Theme.I_PAUSE : Theme.I_PLAY, 18, cx + (on ? 0 : 1.5f), cy, 0xFF0A0A0C);
			if (ui.clicked("music:play", cx - pr, cy - pr, pr * 2, pr * 2)) {
				MusicPlayer.toggle();
			}
			if (pOver) {
				ui.tip(on ? "Pause" : "Play");
			}
			if (control(ui, "music:next", cx + 56, cy, 34, Theme.I_SKIP_FORWARD, Theme.TEXT_STRONG, "Next", false)) {
				MusicPlayer.next();
			}
			int rep = repeat.value;
			if (control(ui, "music:repeat", cx + 104, cy, 30, rep == 2 ? Theme.I_REPEAT_ONE : Theme.I_REPEAT, rep == 0 ? Theme.TEXT_SECONDARY : acc,
					rep == 0 ? "Repeat off" : rep == 1 ? "Repeat all songs" : "Repeat this song", rep != 0)) {
				repeat.value = (rep + 1) % 3;
				Modules.changed();
			}
			// volume, at the right when there is room next to repeat
			float vw = Math.min(120, w * 0.2f), vx = x + w - pad - vw;
			if (vx - 26 > cx + 122) {
				c.icon(volume.value <= 0 ? Theme.I_VOLUME_OFF : volume.value < 50 ? Theme.I_VOLUME_LOW : Theme.I_VOLUME, 14, vx - 16, cy, Theme.TEXT_SECONDARY);
				if (Widgets.slider(ui, "music:vol", vx, cy - 9, vw, volume)) {
					Modules.changed();
				}
			}

			// playlists
			float ly = cy + 32;
			float px = x + pad;
			List<String> lists = MusicPlayer.playlists();
			String chosen = MusicPlayer.playlist();
			if (!lists.isEmpty()) {
				px = chip(ui, "music:pl:*", px, ly, "All songs", chosen == null, acc);
				for (String p : lists) {
					if (px > x + w - pad - 40) {
						break;
					}
					px = chip(ui, "music:pl:" + p, px, ly, p, p.equals(chosen), acc);
				}
				ly += 34;
			}

			// songs
			float footH = 40;
			float listY = ly, listH = y + h - footH - listY;
			List<Track> songs = MusicPlayer.visible();
			if (songs.isEmpty()) {
				empty(ui, x, listY, w, listH);
			} else {
				songList(ui, songs, t, on, acc, x, listY, w, listH);
			}

			// footer
			float fy = y + h - footH + 4;
			c.fill(x, fy - 4, w, 1, Theme.HAIRLINE);
			if (ui.button("music:folder", x + pad, fy + 2, 150, 28, "Open music folder", Theme.I_FOLDER_OPEN, Ui.BTN_GHOST)) {
				MusicPlayer.openFolder();
			}
			if (ui.iconButton("music:refresh", x + pad + 158, fy + 2, 28, Theme.I_REFRESH_CW, "Look for new songs")) {
				MusicPlayer.rescanSoon();
			}
			String err = MusicPlayer.error();
			String info = err != null ? err : MusicPlayer.library().size() + " songs · MP3, OGG, WAV";
			c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, info, w - pad * 2 - 200), x + pad + 196, fy + 16 - c.lineHeight(Fonts.REGULAR, 11) / 2,
					err != null ? Theme.DANGER : Theme.TEXT_MUTED);
			return true;
		}

		private void songList(Ui ui, List<Track> songs, Track cur, boolean on, int acc, float x, float y, float w, float h) {
			Canvas c = ui.c;
			float rowH = 44;
			float off = list.begin(ui, x, y, w, h);
			float ry = y - off;
			for (int i = 0; i < songs.size(); i++, ry += rowH) {
				if (ry + rowH < y || ry > y + h) {
					continue; // off screen: nothing to draw (long folders stay fast)
				}
				Track s = songs.get(i);
				boolean playingThis = s == cur;
				boolean over = ui.hover(x + 6, ry, w - 12, rowH);
				float hv = ui.anim("music:row:" + s.rel, over, 16f);
				if (playingThis || hv > 0.01f) {
					c.round(x + 6, ry + 2, w - 12, rowH - 4, 8, playingThis ? Theme.alpha(acc, 0.12f + 0.06f * hv) : Theme.alpha(0xFFFFFFFF, 0.05f * hv));
				}
				float ix = x + 18, mid = ry + rowH / 2;
				// the song number, an equaliser while it plays, a play arrow on hover
				if (playingThis && on) {
					for (int b = 0; b < 3; b++) {
						float bh = 4 + 8 * (0.5f + 0.5f * (float) Math.sin(ui.now / (150.0 + b * 40) + b * 1.7));
						c.round(ix - 6 + b * 5, mid + 6 - bh, 3, bh, 1, acc);
					}
				} else if (over) {
					c.icon(Theme.I_PLAY, 12, ix, mid, Theme.TEXT_STRONG);
				} else {
					String n = String.valueOf(i + 1);
					c.text(Fonts.MEDIUM, 11, n, ix - c.textWidth(Fonts.MEDIUM, 11, n) / 2, mid - c.lineHeight(Fonts.MEDIUM, 11) / 2, playingThis ? acc : Theme.TEXT_MUTED);
				}
				cover(c, s, x + 38, ry + 7, rowH - 14, acc);
				float tx = x + 38 + rowH - 14 + 10;
				String d = MusicPlayer.time(s.durationMs);
				float dw = s.durationMs > 0 ? c.textWidth(Fonts.MEDIUM, 11, d) : 0;
				float tw = w - (tx - x) - 24 - dw;
				c.text(Fonts.SEMIBOLD, 12.5f, c.ellipsize(Fonts.SEMIBOLD, 12.5f, s.title, tw), tx, ry + 8, playingThis ? acc : s.error != null ? Theme.TEXT_MUTED : Theme.TEXT_STRONG);
				String sub = s.error != null ? s.error : s.subtitle();
				c.text(Fonts.REGULAR, 11, c.ellipsize(Fonts.REGULAR, 11, sub, tw), tx, ry + 25, s.error != null ? Theme.DANGER : Theme.TEXT_MUTED);
				if (dw > 0) {
					c.text(Fonts.MEDIUM, 11, d, x + w - 18 - dw, mid - c.lineHeight(Fonts.MEDIUM, 11) / 2, Theme.TEXT_MUTED);
				}
				if (ui.clicked("music:row:" + s.rel, x + 6, ry, w - 12, rowH)) {
					if (playingThis) {
						MusicPlayer.toggle();
					} else {
						MusicPlayer.play(s);
					}
				}
			}
			list.end(ui, songs.size() * rowH + 8);
		}

		private void empty(Ui ui, float x, float y, float w, float h) {
			Canvas c = ui.c;
			float cy = y + Math.max(40, h / 2 - 50);
			if (MusicPlayer.scanning() && MusicPlayer.library().isEmpty()) {
				ui.dots(x + w / 2, cy, Theme.TEXT_MUTED);
				return;
			}
			float bob = (float) Math.sin(ui.now / 600.0) * 2;
			c.circle(x + w / 2, cy + bob, 28, Theme.alpha(GREEN, 0.14f));
			c.icon(Theme.I_LIST_MUSIC, 22, x + w / 2, cy + bob, GREEN);
			centered(c, Fonts.SEMIBOLD, 14, MusicPlayer.playlist() == null ? "Add your songs" : "This playlist is empty", x, w, cy + 40, Theme.TEXT_STRONG);
			centered(c, Fonts.REGULAR, 11.5f, "Put MP3, OGG or WAV files in your music folder.", x, w, cy + 60, Theme.TEXT_MUTED);
			centered(c, Fonts.REGULAR, 11.5f, "Every instance shares it. Sub-folders become playlists.", x, w, cy + 76, Theme.TEXT_MUTED);
			java.nio.file.Path f = MusicPlayer.folder();
			if (f != null) {
				centered(c, Fonts.REGULAR, 10, c.ellipsize(Fonts.REGULAR, 10, f.toString(), w - 40), x, w, cy + 96, Theme.alpha(0xFFFFFFFF, 0.35f));
			}
			if (ui.button("music:folder:big", x + w / 2 - 85, cy + 116, 170, 34, "Open music folder", Theme.I_FOLDER_OPEN, Ui.BTN_PRIMARY)) {
				MusicPlayer.openFolder();
			}
		}

		private static float chip(Ui ui, String id, float x, float y, String label, boolean on, int acc) {
			Canvas c = ui.c;
			String text = c.ellipsize(Fonts.SEMIBOLD, 11.5f, label, 150);
			float w = c.textWidth(Fonts.SEMIBOLD, 11.5f, text) + 24, h = 26;
			boolean over = ui.hover(x, y, w, h);
			c.round(x, y, w, h, h / 2, on ? 0xFFF4F4F5 : over ? Theme.SUBTLE_HOVER : Theme.SUBTLE);
			c.text(Fonts.SEMIBOLD, 11.5f, text, x + 12, y + h / 2 - c.lineHeight(Fonts.SEMIBOLD, 11.5f) / 2, on ? 0xFF0A0A0C : Theme.TEXT_SECONDARY);
			if (ui.clicked(id, x, y, w, h)) {
				MusicPlayer.setPlaylist(id.equals("music:pl:*") ? null : label);
			}
			return x + w + 8;
		}

		private static boolean control(Ui ui, String id, float cx, float cy, float size, int icon, int color, String tip, boolean dot) {
			Canvas c = ui.c;
			boolean over = ui.hover(cx - size / 2, cy - size / 2, size, size);
			float hv = ui.anim(id + "#h", over, 16f);
			if (hv > 0.01f) {
				c.circle(cx, cy, size / 2, Theme.alpha(0xFFFFFFFF, 0.08f * hv));
			}
			c.icon(icon, size * 0.5f, cx, cy, over ? Theme.mix(color, 0xFFFFFFFF, 0.25f) : color);
			if (dot) {
				c.circle(cx, cy + size / 2 - 1, 1.8f, color);
			}
			if (over) {
				ui.tip(tip);
			}
			return ui.clicked(id, cx - size / 2, cy - size / 2, size, size);
		}
	}

	// ── the HUD card ─────────────────────────────────────────────────────

	static final class NowPlaying extends HudModule {
		final Setting.Bool bars = add(new Setting.Bool("bars", "Sound bars", true));
		final Setting.Bool showCover = add(new Setting.Bool("cover", "Album cover", true));
		final Setting.Bool showTime = add(new Setting.Bool("time", "Song progress", true));
		final Setting.Bool glow = add(new Setting.Bool("glow", "Colour from the cover", true));
		final Setting.Bool hidePaused = add(new Setting.Bool("hidepaused", "Hide while paused", false));
		private final float[] levels = new float[28];
		private final float[] size = new float[2];

		NowPlaying() {
			super("now-playing", "Now Playing", "What's playing from your Music Player: cover, song, progress and sound bars, Spotify style.", Theme.I_AUDIO_LINES,
					true, 1f, 0.02f);
			drop(style, labels);
		}

		@Override
		public boolean visible(Game g) {
			Module player = Modules.get("music");
			Track t = MusicPlayer.current();
			return player != null && player.enabled && t != null && (MusicPlayer.playing() || !hidePaused.value);
		}

		@Override
		public float[] measure(Canvas c, Game g) {
			float s = scale.value;
			size[0] = 236 * s;
			size[1] = (showTime.value ? 74 : 60) * s + (bars.value ? 18 * s : 0);
			return size;
		}

		@Override
		public void paint(Ui ui, float x, float y, Game g) {
			Canvas c = ui.c;
			float s = scale.value;
			float[] sz = measure(c, g);
			float w = sz[0], h = sz[1];
			Track t = MusicPlayer.current();
			boolean demo = t == null; // the HUD editor / module page before anything played
			boolean on = demo || MusicPlayer.playing();
			int acc = demo ? GREEN : accent();
			float a = opacity.value / 100f;
			c.shadow(x, y + 3 * s, w, h, 12 * s, 14 * s, Theme.alpha(0xFF000000, 0.35f * Math.max(0.4f, a)));
			// the card takes a deep shade of the cover's colour (like Spotify's player bar)
			int base = glow.value ? Theme.mix(acc, 0xFF08090C, 0.74f) : 0xFF0B0C10;
			c.round(x, y, w, h, 12 * s, Theme.alpha(base, Math.max(0.6f, a)));
			c.outline(x, y, w, h, 12 * s, 1, Theme.alpha(0xFFFFFFFF, 0.08f));

			float pad = 10 * s, cs = 40 * s;
			float tx = x + pad;
			if (showCover.value) {
				cover(c, t, x + pad, y + pad, cs, acc);
				tx += cs + 10 * s;
			}
			float tw = x + w - pad - tx - 18 * s;
			String title = demo ? "Your song" : t.title, sub = demo ? "Music Player" : t.subtitle();
			c.text(Fonts.SEMIBOLD, 12.5f * s, c.ellipsize(Fonts.SEMIBOLD, 12.5f * s, title, tw), tx, y + pad + 2 * s, 0xFFFFFFFF);
			c.text(Fonts.REGULAR, 10.5f * s, c.ellipsize(Fonts.REGULAR, 10.5f * s, sub, tw), tx, y + pad + 20 * s, Theme.alpha(0xFFFFFFFF, 0.65f));
			// a small equaliser (playing) or pause sign in the corner
			float ex = x + w - pad - 9 * s, ey = y + pad + 10 * s;
			if (on) {
				for (int b = 0; b < 3; b++) {
					float bh = (3 + 7 * (0.5f + 0.5f * (float) Math.sin(ui.now / (140.0 + b * 45) + b * 1.9))) * s;
					c.round(ex - 5 * s + b * 4 * s, ey + 5 * s - bh, 2.4f * s, bh, 1 * s, acc);
				}
			} else {
				c.icon(Theme.I_PAUSE, 11 * s, ex, ey, Theme.alpha(0xFFFFFFFF, 0.6f));
			}
			float by = y + pad + cs + 6 * s;
			if (showTime.value) {
				long pos = demo ? 83_000 : MusicPlayer.position(), dur = demo ? 207_000 : MusicPlayer.duration();
				float frac = dur > 0 ? Math.min(1, pos / (float) dur) : 0;
				String l = MusicPlayer.time(pos), r = MusicPlayer.time(dur);
				float lw = c.textWidth(Fonts.MEDIUM, 9 * s, l), rw = c.textWidth(Fonts.MEDIUM, 9 * s, r);
				float bx = x + pad + lw + 7 * s, bw = w - pad * 2 - lw - rw - 14 * s;
				c.text(Fonts.MEDIUM, 9 * s, l, x + pad, by - 1 * s, Theme.alpha(0xFFFFFFFF, 0.55f));
				c.text(Fonts.MEDIUM, 9 * s, r, x + w - pad - rw, by - 1 * s, Theme.alpha(0xFFFFFFFF, 0.55f));
				c.round(bx, by + 4 * s, bw, 3 * s, 1.5f * s, Theme.alpha(0xFFFFFFFF, 0.16f));
				c.round(bx, by + 4 * s, Math.max(3 * s, bw * frac), 3 * s, 1.5f * s, 0xFFFFFFFF);
				by += 14 * s;
			}
			if (bars.value) {
				if (demo) {
					for (int i = 0; i < levels.length; i++) {
						levels[i] = 0.25f + 0.55f * (float) Math.abs(Math.sin(ui.now / 380.0 + i * 0.55) * Math.sin(ui.now / 910.0 + i * 0.23));
					}
				} else {
					MusicPlayer.bars(levels);
				}
				soundBars(c, levels, x + pad, by, w - pad * 2, 14 * s, acc, on ? 1f : 0.35f);
			}
		}
	}

	// ── shared drawing ───────────────────────────────────────────────────

	/** The cover, or a disc on a gradient in the accent colour while there is none. */
	static void cover(Canvas c, Track t, float x, float y, float size, int acc) {
		Image img = MusicPlayer.cover(t);
		if (img != null) {
			c.stamp(img, img, x, y, size, size, true, 0xFFFFFFFF);
			return;
		}
		float r = size * 0.12f;
		c.round(x, y, size, size, r, Theme.mix(acc, 0xFF0B0C10, 0.55f));
		c.pushClip(x, y, size, size);
		c.gradientV(x, y, size, size, Theme.alpha(0xFFFFFFFF, 0.10f), 0x00000000);
		c.popClip();
		c.icon(t == null ? Theme.I_MUSIC : Theme.I_DISC, size * 0.45f, x + size / 2, y + size / 2, Theme.alpha(0xFFFFFFFF, 0.85f));
	}

	/** Rounded bars from the bottom, brightest in the middle of the range (mirrors a real equaliser). */
	static void soundBars(Canvas c, float[] levels, float x, float y, float w, float h, int acc, float alpha) {
		int n = levels.length;
		float gap = Math.max(1.5f, w / n * 0.28f), bw = (w - gap * (n - 1)) / n;
		for (int i = 0; i < n; i++) {
			float v = Math.max(0.06f, Math.min(1f, levels[i]));
			float bh = Math.max(bw, h * v);
			int col = Theme.alpha(Theme.mix(acc, 0xFFFFFFFF, v * 0.35f), alpha * (0.55f + 0.45f * v));
			c.round(x + i * (bw + gap), y + h - bh, bw, bh, Math.min(bw / 2, 2), col);
		}
	}

	private static void centered(Canvas c, int face, float size, String text, float x, float w, float y, int color) {
		c.text(face, size, text, x + (w - c.textWidth(face, size, text)) / 2, y, color);
	}
}
