package videogoose.combattweaks.mixins;

import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.server.ai.ShipAIEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.vecmath.Vector3f;

/**
 * Smooths out the left-right rocking oscillation caused by aggressive velocity
 * correction inside ShipAIEntity.moveTo().
 *
 * ROOT CAUSE:
 * moveTo() brakes based on the angular difference between the ship's current
 * velocity direction and the desired direction (measured via diffLength on the
 * two unit vectors):
 * <p>
 *   diffLength > 1.5  (≈ 97°)  → velocity *= 0.1   (90 % braking)
 *   diffLength > 1.0  (≈ 60°)  → velocity *= 0.4   (60 % braking)
 *   diffLength > 0.3  (≈ 17°)  → velocity *= 0.7   (30 % braking)
 * <p>
 * The 17° threshold is the primary oscillation driver.  Any minor wobble in the
 * ship's velocity vector — noise from physics integration, small thrust
 * imbalances, or just starting from rest — fires this check, bleeds off 30 % of
 * speed, then full thrust restores it, overshoots, bleeds again.  The 90 %
 * brake makes even larger deviations catastrophic.
 * <p>
 * FIX:
 * We intercept every Vector3fTools.diffLength() call inside moveTo() and
 * multiply the result by 0.6 before it is compared against the thresholds.
 * This is equivalent to raising the effective trigger angles:
 * <p>
 *   Hard  brake: fires when actual diff > 2.5  → NEVER  (diffLength max = 2.0)
 *   Med   brake: fires when actual diff > 1.67 (≈ 105°) — near-backwards flight
 *   Soft  brake: fires when actual diff > 0.50 (≈ 29°)  — genuine misalignment
 * <p>
 * Minor wobbles well under 29° now pass through without braking, eliminating
 * the oscillation.  Large course deviations are still corrected.
 */
@Mixin(value = ShipAIEntity.class, remap = false)
public class SmoothMovementMixin {

	/**
	 * Redirect ALL diffLength(Vector3f, Vector3f) invocations inside moveTo()
	 * to return a scaled-down deviation, softening all three brake thresholds
	 * without touching any constants or other logic.
	 */
	@Redirect(method = "moveTo(Lorg/schema/schine/graphicsengine/core/Timer;Ljavax/vecmath/Vector3f;Z)V", at = @At(value = "INVOKE", target = "Lorg/schema/common/util/linAlg/Vector3fTools;diffLength(Ljavax/vecmath/Vector3f;Ljavax/vecmath/Vector3f;)F"), require = 0)
	private float smoothedDiffLength(Vector3f a, Vector3f b) {
		// Scale actual deviation down so the hardcoded thresholds (1.5, 1.0, 0.3)
		// only fire at proportionally larger real angles.
		return Vector3fTools.diffLength(a, b) * 0.6f;
	}
}
