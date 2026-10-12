package xyz.nativelaunch.health26;

/** Implemented (by mixin) on LivingEntityRenderState: carries what the indicator needs from extract to submit. */
public interface HealthCarrier {
	Snapshot nativeHealth$snapshot();
}
