package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.server.ai.AIControllerStateUnit;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.core.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.manager.MineManager;

/**
 * Mining-ship AI tweaks: don't ram the asteroid, and only ever fire salvage while on a mine order.
 *
 * <p>{@code getSalvageRange()}: the engine's FleetMining state asks this (the AI-reduced engagement
 * range) whether the target block is in range; if not, it flies the ship straight at the block to close
 * the gap — and because we've disabled avoidance toward the asteroid so it can approach at all, nothing
 * stops it and it rams. For ships we're actively mining with, we report a very large salvage range so
 * FleetMining considers itself in range from the safe stand-off distance our controller parks it at,
 * and simply holds and fires. The actual salvage beam's own (real) range still decides whether a shot
 * connects, so this only changes the AI's positioning logic, not how far the beam reaches.</p>
 *
 * <p>{@code doShooting()}: a mine-assigned ship should only ever fire its salvage beams (at a MINABLE
 * target). The engine fires weapons/beams/missiles whenever the current target type is anything other
 * than MINABLE — so in the gaps between mining (repositioning, re-aiming, idling) a miner that also has
 * weapons would shoot the asteroid (or whatever it's pointed at) with real guns. We cancel any shot
 * that isn't a salvage (MINABLE) shot for ships under a mine order.</p>
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

	@Inject(method = "doShooting", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$onlySalvageWhileMining(AIControllerStateUnit<?> unit, Timer timer, CallbackInfo ci) {
		try {
			Ship ship = ((ShipAIEntity) (Object) this).getEntity();
			if(ship != null
					&& MineManager.getInstance().getAssignedTarget(ship.getId()) != null
					&& ship.getNetworkObject().targetType.getByte() != SimpleGameObject.MINABLE) {
				ci.cancel(); // mining ships fire salvage only; suppress weapon/beam/missile shots
			}
		} catch(Exception ignored) {
		}
	}
}
