package xyz.nativelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import xyz.nativelaunch.health26.HealthCarrier;
import xyz.nativelaunch.health26.Snapshot;

/** Health indicators (26.x): lets a living entity's render state carry its indicator from extract to submit. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.entity.state.LivingEntityRenderState", remap = false)
public abstract class HealthStateMixin26 implements HealthCarrier {
	@Unique
	private Snapshot nativeHealth$snapshot;

	@Override
	public Snapshot nativeHealth$snapshot() {
		if (nativeHealth$snapshot == null) {
			nativeHealth$snapshot = new Snapshot();
		}
		return nativeHealth$snapshot;
	}
}
