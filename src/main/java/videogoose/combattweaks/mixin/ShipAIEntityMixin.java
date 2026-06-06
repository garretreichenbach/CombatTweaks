package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.Ship;
import org.schema.game.server.ai.ShipAIEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.manager.MineManager;

/**
 * Stops solo-mining ships from ramming the asteroid.
 *
 * <p>The engine's FleetMining state asks {@code getSalvageRange()} (the AI-reduced engagement range)
 * whether the target block is in range; if not, it flies the ship straight at the block to close the
 * gap — and because we've disabled avoidance toward the asteroid so it can approach at all, nothing
 * stops it and it rams. For ships we're actively mining with, we report a very large salvage range so
 * FleetMining considers itself in range from the safe stand-off distance our controller parks it at,
 * and simply holds and fires. The actual salvage beam's own (real) range still decides whether a shot
 * connects, so this only changes the AI's positioning logic, not how far the beam reaches.</p>
 */
@Mixin(value = ShipAIEntity.class, remap = false)
public abstract class ShipAIEntityMixin {

	@Inject(method = "getSalvageRange", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$holdRangeForMining(CallbackInfoReturnable<Float> cir) {
		try {
			Ship ship = ((ShipAIEntity) (Object) this).getEntity();
			if(ship != null && MineManager.getInstance().getAssignedTarget(ship.getId()) != null) {
				cir.setReturnValue(100000.0f);
			}
		} catch(Exception ignored) {
		}
	}
}
