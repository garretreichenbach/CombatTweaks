package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.beam.harvest.SalvageElementManager;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.player.ControllerStateInterface;
import org.schema.game.server.ai.AIControllerStateUnit;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.core.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.manager.MineManager;
import videogoose.combattweaks.utils.AIUtils;

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

	/**
	 * Keeps ships under a CombatTweaks order on the "Fleet" AI program even if {@code isInFleet()} briefly
	 * reports false.
	 *
	 * <p>{@code updateOnActive} switches any non-fleeted ship onto the autonomous "Ship" program, which
	 * tears down its FleetControllable state machine (FleetMining / engaging / repairing / defending). The
	 * trouble: editing a fleet makes the fleet sync uncache every member and immediately re-cache them, so
	 * for a tick {@code isInFleet()} is false for ALL members — including ones unrelated to the edit. That
	 * one tick is enough for the engine to nuke a commanded ship's FSM, silently killing its order until
	 * re-issued. We redirect that single membership check so commanded ships are treated as fleeted and
	 * keep their program/state through the transient. (Genuine fleet departure is still handled — the
	 * managers release the order after a grace period, after which this returns the real value again.)</p>
	 */
	@Redirect(method = "updateOnActive", at = @At(value = "INVOKE",
			target = "Lorg/schema/game/common/controller/Ship;isInFleet()Z"), remap = false)
	private boolean combatTweaks$keepCommandedOnFleetProgram(Ship entity) {
		return entity.isInFleet() || AIUtils.isUnderCommand(entity.getId());
	}

	@Inject(method = "doShooting", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$suppressWeaponsOnPeacefulOrders(AIControllerStateUnit<?> unit, Timer timer, CallbackInfo ci) {
		try {
			Ship ship = ((ShipAIEntity) (Object) this).getEntity();
			if(ship == null) {
				return;
			}
			// Turrets fire on behalf of the ship they're docked to, so check that root ship's order
			// (a turret's own id never carries the mine/move order).
			int orderId = ship.getId();
			SegmentController root = ship.railController != null ? ship.railController.getRoot() : null;
			if(root != null) {
				orderId = root.getId();
			}
			// Repair orders are handled entirely by the engine's doShooting now: it skips offensive weapons
			// against a friendly target and fires the repair beams at a damaged ally. We deliberately do NOT
			// suppress here (repair isn't in shouldSuppressWeapons), so doShooting runs in full and repairs.
			// Mining/moving ships (and their turrets) only ever fire salvage — cancel any weapon/beam/missile
			// shot. Attacking/defending ships are exempt (shouldSuppressWeapons returns false for them).
			if(AIUtils.shouldSuppressWeapons(orderId)
					&& ship.getNetworkObject().targetType.getByte() != SimpleGameObject.MINABLE) {
				ci.cancel();
			}
		} catch(Exception ignored) {
		}
	}

	/**
	 * Stops salvage beams from firing while a ship is attacking or defending.
	 *
	 * <p>{@code doShooting} always also runs the salvage element manager, so an attacking ship that has
	 * salvage beams chews on its target with them — which is wrong (the player ordered an attack, not
	 * mining) and, worse, the salvage draws so much reactor power that the ship drops below the threshold
	 * needed to fire its actual weapons, effectively disabling them. We gate that salvage call: skip it for
	 * ships (and their turrets) under a combat order; mining and idle ships are unaffected.</p>
	 */
	@Redirect(method = "doShooting", at = @At(value = "INVOKE",
			target = "Lorg/schema/game/common/controller/elements/beam/harvest/SalvageElementManager;handle(Lorg/schema/game/common/data/player/ControllerStateInterface;Lorg/schema/schine/graphicsengine/core/Timer;)V"),
			remap = false)
	private void combatTweaks$noSalvageWhileFighting(SalvageElementManager mgr, ControllerStateInterface unit, Timer timer) {
		try {
			Ship ship = ((ShipAIEntity) (Object) this).getEntity();
			int orderId = ship != null ? ship.getId() : -1;
			if(ship != null && ship.railController != null && ship.railController.getRoot() != null) {
				orderId = ship.railController.getRoot().getId();
			}
			if(AIUtils.isCombatOrder(orderId)) {
				return; // attacking/defending — fire weapons, not salvage
			}
		} catch(Exception ignored) {
		}
		mgr.handle(unit, timer); // normal salvage (mining / idle)
	}
}
