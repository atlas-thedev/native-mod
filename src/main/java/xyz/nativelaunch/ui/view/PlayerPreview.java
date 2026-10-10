package xyz.nativelaunch.ui.view;

import xyz.nativelaunch.core.NativeState;
import xyz.nativelaunch.core.SkinEntry;
import xyz.nativelaunch.cosmetic.CosmeticLibrary;
import xyz.nativelaunch.cosmetic.CosmeticModel;
import xyz.nativelaunch.cosmetic.CosmeticRef;
import xyz.nativelaunch.cosmetic.CosmeticRenderer;
import xyz.nativelaunch.cosmetic.CosmeticSink;
import xyz.nativelaunch.relay.RelayClient;
import xyz.nativelaunch.ui.Avatars;
import xyz.nativelaunch.ui.Ui;
import xyz.nativelaunch.ui.UiRuntime;
import xyz.nativelaunch.ui.gfx.Boxes;
import xyz.nativelaunch.ui.gfx.Image;
import xyz.nativelaunch.ui.mod.Wardrobe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** The player's own model in 3D (skin, cape, worn cosmetics) for the wardrobe. Drag to turn it. */
public final class PlayerPreview implements CosmeticSink {
	private final Boxes b = new Boxes();
	private final CosmeticRenderer renderer = new CosmeticRenderer();
	private final List<CosmeticLibrary.Loaded> worn = new ArrayList<CosmeticLibrary.Loaded>();
	private final Map<CosmeticLibrary.Loaded, Wardrobe.Item> itemOf = new IdentityHashMap<CosmeticLibrary.Loaded, Wardrobe.Item>();
	private float yaw = -0.45f, pitch = -0.12f, vel;
	private float lastMx;
	private long lastDrag;
	private boolean slim;
	private float armSwing, time;
	private static final Image WHITE = new Image(2, 2, new int[] {-1, -1, -1, -1});

	static {
		WHITE.linear = false;
	}

	/** Draws the model into the box; ownCapeHash / skin come from the directory entry when known. */
	/** Slot group shown empty in the preview (trying on "Nothing"); "capes" hides the cape. */
	public String bare;

	public void draw(Ui ui, String id, float x, float y, float w, float h, Wardrobe.Item hover) {
		boolean over = ui.hover(x, y, w, h);
		ui.clicked(id, x, y, w, h);
		if (ui.isPressing(id)) {
			if (ui.pressed) {
				lastMx = ui.mx;
			}
			float dx = ui.mx - lastMx;
			lastMx = ui.mx;
			yaw += dx * 0.012f;
			vel = dx * 0.012f / Math.max(0.004f, ui.dt);
			lastDrag = ui.now;
		} else {
			yaw += vel * ui.dt;
			vel *= (float) Math.exp(-5 * ui.dt);
		}
		if (over) {
			ui.cursorHand = true;
		}
		time += ui.dt;
		float idle = ui.now - lastDrag > 2500 ? (float) Math.sin(time * 0.7) * 0.0025f : 0;
		yaw += idle;

		// floor glow
		float cx = x + w / 2;
		float scale = Math.min(h / 50f, w / 30f);
		float feet = y + h / 2 + 19 * scale;
		ui.c.shadow(cx - 9 * scale, feet - 1.5f * scale, 18 * scale, 3 * scale, 1.5f * scale, 10, 0x66000000);

		// skin
		String name = myName();
		SkinEntry e = name == null ? null : NativeState.get().directory().entry(name);
		Image skin = null;
		if (e != null && e.skinHash != null) {
			skin = Wardrobe.hash(textureBase(), e.skinHash);
			slim = e.slim;
		} else {
			RelayClient r = RelayClient.get();
			String ref = r == null ? null : r.meSkin;
			if (ref != null && ref.startsWith("mj:")) {
				final String uuid = ref.substring(3);
				skin = Wardrobe.image(ref, () -> xyz.nativelaunch.core.Http.getBytes(Avatars.mojangSkinUrl(uuid), 2 * 1024 * 1024), true, 0);
			} else if (ref != null && ref.matches("[a-f0-9]{64}")) {
				skin = Wardrobe.hash(textureBase(), ref);
			}
		}
		if (skin == Wardrobe.NONE) {
			skin = null;
		}

		// cape: the store cape worn (or the one being hovered), else the directory one
		Image cape = null;
		Wardrobe.Item capeItem = hover != null && hover.isCape() ? hover : Wardrobe.item(Wardrobe.equipped);
		if ("capes".equals(bare)) {
			capeItem = null;
		} else if (capeItem != null) {
			cape = Wardrobe.url(capeItem.stillUrl, true, 0);
		} else if (e != null && e.capeHash != null && Wardrobe.equipped == null && Wardrobe.state != Wardrobe.State.READY) {
			cape = Wardrobe.hash(textureBase(), e.capeHash);
		}
		// a back item (wings, blades, backpacks) takes the cape's place, like in game
		boolean backWorn = !"back".equals(bare) && (Wardrobe.wearing.containsKey("back") || hover != null && !hover.isCape() && "back".equals(hover.group()));
		if (cape == Wardrobe.NONE || backWorn && !(hover != null && hover.isCape())) {
			cape = null;
		}

		// cosmetics
		worn.clear();
		itemOf.clear();
		for (Map.Entry<String, String> wear : Wardrobe.wearing.entrySet()) {
			if (wear.getKey().equals(bare)) {
				continue;
			}
			Wardrobe.Item it = hover != null && !hover.isCape() && hover.group().equals(wear.getKey()) ? hover : Wardrobe.item(wear.getValue());
			add(it);
		}
		if (hover != null && !hover.isCape() && !Wardrobe.wearing.containsKey(hover.group())) {
			add(hover);
		}

		b.begin(cx, y + h / 2 + 3 * scale, scale, yaw, pitch);
		b.translate(0, -8, 0);
		armSwing = (float) Math.sin(time * 1.6) * 0.04f;
		boolean legacy = skin != null && skin.height * 2 == skin.width;
		int tex = legacy ? 32 : 64;
		int tint = skin == null ? 0xFF8A8F98 : 0xFFFFFFFF;
		Image s = skin == null ? WHITE : skin;
		int tw = skin == null ? 2 : 64, th = skin == null ? 2 : tex;
		// head
		bone(CosmeticModel.Attach.HEAD);
		part(s, tw, th, -4, -8, -4, 8, 8, 8, 0, 0, false, tint, skin == null ? 0xFFA3A8B1 : tint);
		if (skin != null && !xyz.nativelaunch.cosmetic.HeadCover.any(worn)) { // a hood replaces the hat layer, like in game
			b.cube(s, tw, th, -4, -8, -4, 8, 8, 8, 32, 0, 0.5f, false, tint);
		}
		b.pop();
		// body
		bone(CosmeticModel.Attach.BODY);
		part(s, tw, th, -4, 0, -2, 8, 12, 4, 16, 16, false, tint, tint);
		if (skin != null && !legacy) {
			b.cube(s, tw, th, -4, 0, -2, 8, 12, 4, 16, 32, 0.25f, false, tint);
		}
		if (cape != null) {
			b.push();
			b.translate(0, 0, 2);
			b.rotate(0.1f + (float) Math.sin(time * 1.2) * 0.03f, 0, 0);
			b.rotate(0, (float) Math.PI, 0);
			b.cube(cape, 64, 32, -5, 0, -1, 10, 16, 1, 0, 0, 0, false, 0xFFFFFFFF);
			b.pop();
		}
		b.pop();
		float aw = slim ? 3 : 4;
		// right arm
		bone(CosmeticModel.Attach.RIGHT_ARM);
		part(s, tw, th, -aw + 1, -2, -2, aw, 12, 4, 40, 16, false, tint, tint);
		if (skin != null && !legacy) {
			b.cube(s, tw, th, -aw + 1, -2, -2, aw, 12, 4, 40, 32, 0.25f, false, tint);
		}
		b.pop();
		// left arm
		bone(CosmeticModel.Attach.LEFT_ARM);
		if (legacy) {
			part(s, tw, th, -1, -2, -2, aw, 12, 4, 40, 16, true, tint, tint);
		} else {
			part(s, tw, th, -1, -2, -2, aw, 12, 4, 32, 48, false, tint, tint);
			if (skin != null) {
				b.cube(s, tw, th, -1, -2, -2, aw, 12, 4, 48, 48, 0.25f, false, tint);
			}
		}
		b.pop();
		// legs
		bone(CosmeticModel.Attach.RIGHT_LEG);
		part(s, tw, th, -2, 0, -2, 4, 12, 4, 0, 16, false, tint, tint);
		if (skin != null && !legacy) {
			b.cube(s, tw, th, -2, 0, -2, 4, 12, 4, 0, 32, 0.25f, false, tint);
		}
		b.pop();
		bone(CosmeticModel.Attach.LEFT_LEG);
		if (legacy) {
			part(s, tw, th, -2, 0, -2, 4, 12, 4, 0, 16, true, tint, tint);
		} else {
			part(s, tw, th, -2, 0, -2, 4, 12, 4, 16, 48, false, tint, tint);
			if (skin != null) {
				b.cube(s, tw, th, -2, 0, -2, 4, 12, 4, 0, 48, 0.25f, false, tint);
			}
		}
		b.pop();
		if (!worn.isEmpty()) {
			try {
				renderer.render(worn, this, time, 0f, 0);
			} catch (Throwable t) {
				worn.clear(); // a broken model must not take the menu down
			}
		}
		b.end(ui.c);
	}

	private void part(Image s, int tw, int th, float x, float y, float z, float w, float h, float d, float u, float v, boolean mirror, int tint,
			int fallbackTint) {
		if (s == WHITE) {
			b.cube(s, 2, 2, x, y, z, w, h, d, 0, 0, 0, false, fallbackTint);
		} else {
			b.cube(s, tw, th, x, y, z, w, h, d, u, v, 0, mirror, tint);
		}
	}

	private void add(Wardrobe.Item it) {
		if (it == null || it.isCape() || it.modelHash() == null) {
			return;
		}
		String th = it.textureHash();
		if (th == null) {
			return;
		}
		Integer side = Wardrobe.sides.get(it.group());
		CosmeticRef ref = new CosmeticRef(it.id, it.modelHash(), th, it.group(), side == null ? 0 : side);
		CosmeticLibrary.Loaded l = CosmeticLibrary.get(ref, Wardrobe.base());
		if (l != null) {
			l = l.as(ref);
			worn.add(l);
			itemOf.put(l, it);
		}
	}

	private static String myName() {
		RelayClient r = RelayClient.get();
		if (r != null && r.meName != null) {
			return r.meName;
		}
		try {
			return UiRuntime.mc().username();
		} catch (Throwable t) {
			return null;
		}
	}

	private static String textureBase() {
		String base = NativeState.get().directory().textureBase();
		return base == null || base.isEmpty() ? Wardrobe.base() : base;
	}

	/** push + move into a vanilla bone's space (pivot and idle pose). */
	private void bone(CosmeticModel.Attach a) {
		b.push();
		boneTransform(a);
	}

	private void boneTransform(CosmeticModel.Attach a) {
		switch (a) {
			case RIGHT_ARM:
				b.translate(-5, slim ? 2.5f : 2, 0);
				b.rotate(armSwing, 0, 0.06f + armSwing);
				break;
			case LEFT_ARM:
				b.translate(5, slim ? 2.5f : 2, 0);
				b.rotate(-armSwing, 0, -0.06f - armSwing);
				break;
			case RIGHT_LEG:
				b.translate(-1.9f, 12, 0);
				break;
			case LEFT_LEG:
				b.translate(1.9f, 12, 0);
				break;
			default:
				break;
		}
	}

	// ── CosmeticSink on Boxes ───────────────────────────────────────────

	@Override
	public void push() {
		b.push();
	}

	@Override
	public void pop() {
		b.pop();
	}

	@Override
	public void attach(CosmeticModel.Attach attach) {
		boneTransform(attach);
	}

	@Override
	public void transform(float px, float py, float pz, float pitch, float yaw, float roll) {
		b.translate(px, py, pz);
		b.rotate(pitch, yaw, roll);
	}

	@Override
	public void draw(CosmeticLibrary.Loaded cosmetic, CosmeticModel.Part part) {
		Wardrobe.Item it = itemOf.get(cosmetic);
		Image tex = it == null ? null : Wardrobe.cosmeticTexture(it);
		if (tex == null || tex == Wardrobe.NONE) {
			return;
		}
		CosmeticModel m = cosmetic.model;
		b.twoSided = true; // like vanilla's no-cull cosmetic layers
		try {
			for (CosmeticModel.Cube c : part.cubes) {
				b.cube(tex, m.textureWidth, m.textureHeight, c.x, c.y, c.z, c.w, c.h, c.d, c.u, c.v, c.inflate, c.mirror, 0xFFFFFFFF);
			}
		} finally {
			b.twoSided = false;
		}
	}

	public void resetView() {
		yaw = -0.45f;
		vel = 0;
	}
}
