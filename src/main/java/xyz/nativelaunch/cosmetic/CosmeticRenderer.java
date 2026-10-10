package xyz.nativelaunch.cosmetic;

import java.util.List;

/** Walks the worn cosmetics of one player and feeds a {@link CosmeticSink}. Version independent. */
public final class CosmeticRenderer {
	public static final int ARMOR_HEAD = 1;
	public static final int ARMOR_CHEST = 2;
	public static final int ARMOR_LEGS = 4;
	public static final int ARMOR_FEET = 8;
	/** The left / right hand (as seen on the model) holds an item. */
	public static final int ARMOR_LEFT_HAND = 16;
	public static final int ARMOR_RIGHT_HAND = 32;

	private final float[] pose = new float[6];

	/**
	 * @param armor  ARMOR_* bits for the filled armor slots
	 * @param moving walk speed 0..1
	 */
	public void render(List<CosmeticLibrary.Loaded> worn, CosmeticSink sink, double time, float moving, int armor) {
		if (HeadCover.any(worn)) {
			armor &= ~ARMOR_HEAD; // the helmet is not drawn under a hood, so nothing has to make room for it
		}
		for (int i = 0; i < worn.size(); i++) {
			CosmeticLibrary.Loaded cosmetic = worn.get(i);
			int side = cosmetic.ref.side != 0 ? cosmetic.ref.side : cosmetic.model.defaultSide();
			for (CosmeticModel.Part root : cosmetic.model.roots) {
				if ((root.side != 0 && root.side != side) || hidden(root, armor)) {
					continue;
				}
				sink.push();
				try {
					sink.attach(root.attach);
					walk(cosmetic, root, sink, time, moving, armor);
				} finally {
					sink.pop();
				}
			}
		}
	}

	private void walk(CosmeticLibrary.Loaded cosmetic, CosmeticModel.Part part, CosmeticSink sink, double time, float moving, int armor) {
		if (hidden(part, armor)) {
			return;
		}
		sink.push();
		try {
			if (!part.armorHide && filled(part.armorSlot, armor)) {
				sink.transform(part.armorOffset[0], part.armorOffset[1], part.armorOffset[2], 0, 0, 0);
			}
			if (!CosmeticPose.pose(part, time, moving, pose)) {
				return;
			}
			sink.transform(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]);
			if (!part.cubes.isEmpty()) {
				sink.draw(cosmetic, part);
			}
			for (CosmeticModel.Part child : part.children) {
				walk(cosmetic, child, sink, time, moving, armor);
			}
		} finally {
			sink.pop();
		}
	}

	private static boolean hidden(CosmeticModel.Part part, int armor) {
		return part.armorHide && filled(part.armorSlot, armor);
	}

	static boolean filled(CosmeticModel.Slot slot, int armor) {
		switch (slot) {
			case HEAD:
				return (armor & ARMOR_HEAD) != 0;
			case CHEST:
				return (armor & ARMOR_CHEST) != 0;
			case LEGS:
				return (armor & ARMOR_LEGS) != 0;
			case FEET:
				return (armor & ARMOR_FEET) != 0;
			case LEFT_HAND:
				return (armor & ARMOR_LEFT_HAND) != 0;
			case RIGHT_HAND:
				return (armor & ARMOR_RIGHT_HAND) != 0;
			default:
				return false;
		}
	}
}
