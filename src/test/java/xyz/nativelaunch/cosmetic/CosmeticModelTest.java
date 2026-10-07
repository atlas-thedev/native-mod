package xyz.nativelaunch.cosmetic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CosmeticModelTest {
	static final String CAP = "{\"format\":1,\"texture\":[64,32],\"parts\":[{\"id\":\"cap\",\"attach\":\"head\",\"armor\":{\"slot\":\"head\"},"
			+ "\"cubes\":[{\"origin\":[-4,-9,-4],\"size\":[8,1,8],\"uv\":[0,0],\"inflate\":0.25}],"
			+ "\"children\":[{\"id\":\"prop\",\"pivot\":[0,-11,0],\"layer\":\"glow\",\"anim\":[{\"type\":\"spin\",\"axis\":\"y\",\"speed\":360}],"
			+ "\"cubes\":[{\"origin\":[-4,-0.5,-0.5],\"size\":[8,1,1],\"uv\":[0,20]}]}]}]}";

	@Test
	void parsesPartsChildrenAndAnimations() {
		CosmeticModel m = CosmeticModel.parse(CAP);
		assertEquals(64, m.textureWidth);
		assertEquals(32, m.textureHeight);
		assertEquals(1, m.roots.size());
		assertEquals(2, m.flat.size());
		CosmeticModel.Part cap = m.roots.get(0);
		assertEquals(CosmeticModel.Attach.HEAD, cap.attach);
		assertEquals(CosmeticModel.Slot.HEAD, cap.armorSlot);
		assertTrue(cap.armorHide);
		assertEquals(0.25f, cap.cubes.get(0).inflate, 1e-6);
		CosmeticModel.Part prop = cap.children.get(0);
		assertSame(prop, m.flat.get(prop.index));
		assertEquals(CosmeticModel.Attach.HEAD, prop.attach, "children inherit the root's attachment");
		assertEquals(CosmeticModel.Layer.GLOW, prop.layer);
		assertEquals(CosmeticModel.Anim.Type.SPIN, prop.anims.get(0).type);
	}

	@Test
	void rejectsJunkAndOversizedModels() {
		assertThrows(RuntimeException.class, () -> CosmeticModel.parse("[]"));
		assertThrows(RuntimeException.class, () -> CosmeticModel.parse("{\"parts\":[]}"));
		assertThrows(RuntimeException.class, () -> CosmeticModel.parse("{\"format\":2,\"parts\":[{}]}"));
		StringBuilder many = new StringBuilder("{\"parts\":[");
		for (int i = 0; i < CosmeticModel.MAX_PARTS + 1; i++) many.append(i == 0 ? "" : ",").append("{}");
		assertThrows(IllegalArgumentException.class, () -> CosmeticModel.parse(many.append("]}").toString()));
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < CosmeticModel.MAX_DEPTH + 2; i++) deep.append("{\"children\":[");
		for (int i = 0; i < CosmeticModel.MAX_DEPTH + 2; i++) deep.append("]}");
		assertThrows(IllegalArgumentException.class, () -> CosmeticModel.parse("{\"parts\":[" + deep + "]}"));
	}

	@Test
	void clampsSizes() {
		CosmeticModel m = CosmeticModel.parse("{\"parts\":[{\"cubes\":[{\"origin\":[999,0,0],\"size\":[500,-3,2.6]}]}]}");
		CosmeticModel.Cube c = m.roots.get(0).cubes.get(0);
		assertEquals(64, c.x, 1e-6);
		assertEquals(64, c.w, 1e-6);
		assertEquals(0, c.h, 1e-6);
		assertEquals(2.6f, c.d, 1e-6);
	}

	@Test
	void poseAnimates() {
		CosmeticModel m = CosmeticModel.parse("{\"parts\":[{\"pivot\":[1,2,3],\"anim\":["
				+ "{\"type\":\"spin\",\"axis\":\"y\",\"speed\":90},"
				+ "{\"type\":\"swing\",\"axis\":\"z\",\"amplitude\":30,\"speed\":0.25,\"moving\":30},"
				+ "{\"type\":\"bob\",\"axis\":\"y\",\"amplitude\":2,\"speed\":0.25},"
				+ "{\"type\":\"blink\",\"amplitude\":0.5,\"speed\":1}]}]}");
		CosmeticModel.Part p = m.roots.get(0);
		float[] out = new float[6];
		assertTrue(CosmeticPose.pose(p, 1.0, 0f, out)); // quarter cycle: sin = 1
		assertEquals(Math.toRadians(90), out[4], 1e-4);
		assertEquals(Math.toRadians(30), out[5], 1e-4);
		assertEquals(4f, out[1], 1e-4);
		assertEquals(1f, out[0], 1e-6);
		CosmeticPose.pose(p, 1.0, 1f, out);
		assertEquals(Math.toRadians(60), out[5], 1e-4, "moving adds amplitude");
		assertFalse(CosmeticPose.pose(p, 1.75, 0f, out), "blink hides the part for the second half of each cycle");
	}

	@Test
	void sharedCosmeticKeepsEachPlayersSide() {
		CosmeticModel m = CosmeticModel.parse(CAP);
		CosmeticLibrary.Loaded left = CosmeticLibrary.create(new CosmeticRef("balloon", "a", "b", "balloon", 1), m, new byte[] { 1 });
		CosmeticLibrary.Loaded right = left.as(new CosmeticRef("balloon", "a", "b", "balloon", 2));
		assertEquals(1, left.ref.side);
		assertEquals(2, right.ref.side, "another player's side is kept");
		assertSame(right, left.as(new CosmeticRef("balloon", "a", "b", "balloon", 2)), "views are reused");
		right.setBaked("gpu");
		assertEquals("gpu", left.baked(), "the baked asset is shared");
		left.release();
		assertNull(right.png(), "the texture is released for everyone");
	}

	@Test
	void rendererHidesWithArmorAndWalksChildren() {
		CosmeticModel m = CosmeticModel.parse(CAP);
		CosmeticLibrary.Loaded loaded = CosmeticLibrary.create(new CosmeticRef("propeller", "a", "b"), m, new byte[0]);
		StringBuilder log = new StringBuilder();
		CosmeticSink sink = new CosmeticSink() {
			public void push() { log.append('('); }
			public void pop() { log.append(')'); }
			public void attach(CosmeticModel.Attach a) { log.append(a.name().charAt(0)); }
			public void transform(float px, float py, float pz, float pitch, float yaw, float roll) { log.append('t'); }
			public void draw(CosmeticLibrary.Loaded c, CosmeticModel.Part part) { log.append('[').append(part.id).append(']'); }
		};
		new CosmeticRenderer().render(Arrays.asList(loaded), sink, 0, 0, 0);
		assertEquals("(H(t[cap](t[prop])))", log.toString());
		log.setLength(0);
		new CosmeticRenderer().render(Arrays.asList(loaded), sink, 0, 0, CosmeticRenderer.ARMOR_HEAD);
		assertEquals("", log.toString(), "a helmet hides the hat");
	}

	@Test
	void erasCoverEveryRelease() {
		assertEquals("cos116", CosmeticsBoot.era("1.16"));
		assertEquals("cos116", CosmeticsBoot.era("1.16.5"));
		assertEquals("cos117", CosmeticsBoot.era("1.17"));
		assertEquals("cos117", CosmeticsBoot.era("1.20.6"));
		assertEquals("cos117", CosmeticsBoot.era("1.21"));
		assertEquals("cos117", CosmeticsBoot.era("1.21.1"));
		assertEquals("cos1212", CosmeticsBoot.era("1.21.2"));
		assertEquals("cos1212", CosmeticsBoot.era("1.21.8"));
		assertEquals("cos1219", CosmeticsBoot.era("1.21.9"));
		assertEquals("cos1219", CosmeticsBoot.era("1.21.11"));
		assertEquals("cos26", CosmeticsBoot.era("26.1.2"));
		assertEquals("cos26", CosmeticsBoot.era("26.3"));
		assertNull(CosmeticsBoot.era("1.15.2"));
		assertNull(CosmeticsBoot.era("25w14a"));
		assertNull(CosmeticsBoot.era(null));
	}

	@Test
	void seedIsStablePerName() {
		assertEquals(CosmeticPose.seed("Alice"), CosmeticPose.seed("alice"));
		double s = CosmeticPose.seed("Bob");
		assertTrue(s >= 0 && s < 10);
	}

	@Test
	void handItemsKeepSideAndHandSlots() {
		CosmeticModel m = CosmeticModel.parse("{\"parts\":[{\"side\":\"left\",\"attach\":\"leftarm\",\"armor\":{\"slot\":\"lefthand\"}},{\"side\":\"right\",\"attach\":\"rightarm\",\"armor\":{\"slot\":\"righthand\"}}]}");
		assertEquals(1, m.roots.get(0).side);
		assertEquals(2, m.roots.get(1).side);
		assertEquals(1, m.defaultSide());
		assertEquals(CosmeticModel.Slot.LEFT_HAND, m.roots.get(0).armorSlot);
		assertEquals(CosmeticModel.Slot.RIGHT_HAND, m.roots.get(1).armorSlot);
	}

	@Test
	void armorSlotNamesAreNormalisedLikeAttachPoints() {
		CosmeticModel m = CosmeticModel.parse("{\"parts\":[{\"armor\":{\"slot\":\"Left_Hand\"}},{\"armor\":{\"slot\":\"MAINHAND\"}},{\"armor\":{\"slot\":\"helmet\"}},{\"armor\":{\"slot\":\"boots\"}},{\"armor\":{\"slot\":\"hand\"}}]}");
		assertEquals(CosmeticModel.Slot.LEFT_HAND, m.roots.get(0).armorSlot);
		assertEquals(CosmeticModel.Slot.RIGHT_HAND, m.roots.get(1).armorSlot);
		assertEquals(CosmeticModel.Slot.HEAD, m.roots.get(2).armorSlot);
		assertEquals(CosmeticModel.Slot.FEET, m.roots.get(3).armorSlot);
		assertEquals(CosmeticModel.Slot.NONE, m.roots.get(4).armorSlot);
	}

	@Test
	void nonFiniteNumbersFallBackToDefaults() {
		CosmeticModel m = CosmeticModel.parse("{\"parts\":[{\"cubes\":[{\"size\":[NaN,2,Infinity]}]}]}");
		CosmeticModel.Cube c = m.roots.get(0).cubes.get(0);
		assertEquals(1f, c.w);
		assertEquals(2f, c.h);
		assertEquals(1f, c.d);
	}
}
