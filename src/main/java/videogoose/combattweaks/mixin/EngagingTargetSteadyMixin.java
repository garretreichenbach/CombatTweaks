package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.Ship;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.states.EngagingTargetSteady;
import org.schema.schine.graphicsengine.core.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import videogoose.combattweaks.utils.AIUtils;

import javax.vecmath.Vector3f;

/**
 * Supporting Fire: hold position while engaging instead of running the engine's orbit/strafe.
 *
 * <p>{@code EngagingTargetSteady.updateAI} issues the orbital movement with a {@code moveTo} and then immediately
 * orients the ship at the target with {@code orientate}. For ships flagged via {@link AIUtils#isSupportingFire},
 * we redirect just that {@code moveTo} to a no-op — the ship stops orbiting/strafing but still rotates to face the
 * target (orientate is untouched) and keeps firing. The preceding {@code GettingToTarget} state is left alone, so
 * a ship still closes to weapon range first and then holds there to fire from long range.</p>
 */
@Mixin(value = EngagingTargetSteady.class, remap = false)
public abstract class EngagingTargetSteadyMixin {

	@Redirect(method = "updateAI", at = @At(value = "INVOKE",
			target = "Lorg/schema/game/server/ai/ShipAIEntity;moveTo(Lorg/schema/schine/graphicsengine/core/Timer;Ljavax/vecmath/Vector3f;Z)V"), remap = false)
	private void combatTweaks$holdPositionWhileSupportingFire(ShipAIEntity s, Timer timer, Vector3f moveDir, boolean orientate) {
		try {
			Ship entity = s.getEntity();
			if(entity != null && AIUtils.isSupportingFire(entity.getId())) {
				return; // supporting fire — hold position, don't orbit/strafe (firing + facing still happen)
			}
		} catch(Exception ignored) {
		}
		s.moveTo(timer, moveDir, orientate);
	}
}
