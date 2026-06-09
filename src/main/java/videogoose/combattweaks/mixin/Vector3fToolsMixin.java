package videogoose.combattweaks.mixin;

import org.schema.common.util.linAlg.Vector3fTools;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.vecmath.Vector3f;

/**
 * Fixes an engine NaN in AI lead-prediction when firing at a <em>stationary</em> target.
 *
 * <p>{@code predictPoint} computes a heading lead from the target's velocity:</p>
 * <pre>
 *   targetHeading.set(targetVelocity);
 *   targetHeading.normalize();              // ← NaN when targetVelocity is (0,0,0)
 *   targetHeading.scale(angularVel * t);
 * </pre>
 * <p>For a stationary target the velocity is the zero vector, and {@code Vector3f.normalize()} divides by a
 * zero length, producing {@code (NaN,NaN,NaN)}. That NaN flows into the returned bullet path, so the weapon
 * fires a projectile with a NaN direction — the endless {@code RAYTRACE_TRAVERSE}/{@code ProjectileController}
 * NaN spam. The earlier {@code relativeVelocity.lengthSquared()==0} guard misses it because the <em>shooter</em>
 * is moving (additive projectiles), so the relative velocity is non-zero.</p>
 *
 * <p>Vanilla never triggers this because the AI never engages a stationary unmanned derelict — which is exactly
 * the case CombatTweaks enables (commanded attack on a neutral). We redirect that single {@code normalize()} to
 * skip it when the vector is zero-length: a stationary target needs no heading lead, so leaving the term at zero
 * is correct, and the quadratic intercept (which accounts for the shooter's own motion) is unaffected.</p>
 */
@Mixin(value = Vector3fTools.class, remap = false)
public abstract class Vector3fToolsMixin {

	@Redirect(method = "predictPoint(Ljavax/vecmath/Vector3f;Ljavax/vecmath/Vector3f;Ljavax/vecmath/Vector3f;FLjavax/vecmath/Vector3f;Ljavax/vecmath/Vector3f;)Ljavax/vecmath/Vector3f;",
			at = @At(value = "INVOKE", target = "Ljavax/vecmath/Vector3f;normalize()V"),
			remap = false)
	private static void combatTweaks$safeHeadingNormalize(Vector3f heading) {
		if(heading.lengthSquared() > 1.0e-12f) {
			heading.normalize();
		}
		// else: stationary target — leave the heading at zero rather than normalizing to NaN.
	}
}
