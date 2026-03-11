package videogoose.combattweaks.mixins;

import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.schine.graphicsengine.core.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.vecmath.Vector3f;

/**
 * Disables orbital movement when a ship is mining or repairing.
 * Prevents the AI from orbiting around asteroids/targets during salvage/repair operations.
 */
@Mixin(value = ShipAIEntity.class, remap = false)
public class DisableOrbitWhileMiningSalvagingMixin {

	/**
	 * Intercept moveTo() to detect and eliminate orbital movement for mining/repair targets.
	 * If the current AI program is targeting an asteroid (mining) or friendly ship (repair),
	 * we zero out lateral movement to prevent orbiting.
	 */
	@Inject(method = "moveTo", at = @At("HEAD"), require = 1)
	private void onMoveTo(Timer timer, Vector3f toDir, boolean orientate, CallbackInfo ci) {
		try {
			// Get current program of this ship's AI
			ShipAIEntity aiEntity = (ShipAIEntity) (Object) this;
			Object currentProgram = aiEntity.getCurrentProgram();

			if(currentProgram instanceof TargetProgram) {
				TargetProgram<?> targetProg = (TargetProgram<?>) currentProgram;
				SimpleGameObject target = targetProg.getTarget();

				if(target != null) {
					boolean isMiningTarget = target instanceof FloatingRock;
					boolean isRepairTarget = target instanceof Ship;

					if(isMiningTarget || isRepairTarget) {
						// Zero out lateral movement (X, Y), keep only forward/backward (Z)
						// This prevents orbital strafing while maintaining approach/retreat movement
						float zComponent = toDir.z;
						toDir.set(0, 0, zComponent);
					}
				}
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}
}
