package videogoose.combattweaks.listener;

import api.common.GameCommon;
import api.common.GameServer;
import api.listener.fastevents.ShipAIEntityAttemptToShootListener;
import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.beam.repair.RepairBeamCollectionManager;
import org.schema.game.common.controller.elements.beam.repair.RepairElementManager;
import org.schema.game.common.controller.elements.beam.repair.RepairUnit;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.world.Sector;
import org.schema.game.server.ai.AIControllerStateUnit;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;
import videogoose.combattweaks.system.weapon.auradisruptor.AuraDisruptorBeamCollectionManager;
import videogoose.combattweaks.system.weapon.auradisruptor.AuraDisruptorBeamElementManager;
import videogoose.combattweaks.system.weapon.auradisruptor.AuraDisruptorBeamUnit;
import videogoose.combattweaks.utils.AIUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShipAIShootListenerImpl implements ShipAIEntityAttemptToShootListener {

	/** Per-target wall-clock (ms) of the last Armor HP restore tick, so the refill is rate-correct under varying tick timing. */
	private final Map<Integer, Long> lastArmorRepair = new ConcurrentHashMap<>();
	/** Per-target wall-clock (ms) of the last damaged-block re-scan, throttled so the structure isn't iterated every tick. */
	private final Map<Integer, Long> lastBlockScan = new ConcurrentHashMap<>();
	private static final long BLOCK_RESCAN_INTERVAL_MS = 3000;

	@Override
	public void doShooting(ShipAIEntity shipAIEntity, AIControllerStateUnit<?> aiControllerStateUnit, Timer timer) {
		if(!shipAIEntity.isOnServer()) {
			return;
		}
		try {
			handleAuraDisruptor(shipAIEntity, aiControllerStateUnit, timer);
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error firing Aura Disruptor AI", exception);
		}
		try {
			RepairElementManager elementManager = SegmentControllerUtils.getElementManager(shipAIEntity.getEntity(), RepairElementManager.class);
			if(elementManager != null && elementManager.totalSize > 0) {
				if(!isValidTarget(shipAIEntity)) changeTarget(shipAIEntity);
				if(isValidTarget(shipAIEntity)) {
					for(RepairBeamCollectionManager collectionManager : elementManager.getCollectionManagers()) {
						for(RepairUnit unit : collectionManager.getElementCollections()) {
							if(unit.size() > 0 && !unit.isReloading(timer.currentTime) && unit.canUse(timer.currentTime, false)) {
								unit.fire(aiControllerStateUnit, timer);
							}
						}
					}
					SegmentController repairTarget = getRepairTarget(shipAIEntity);
					// The engine repair beam only mends reactor/blocks; recharge the target's Armor HP pool too
					// (the main thing combat actually depletes, and what survives a restart).
					restoreArmorHP(repairTarget);
					// Re-record reduced-HP blocks while actually on station: the order-time scan often runs before
					// the target's far segments load, so it misses most damage. Throttled, main-thread (safe to
					// iterate segments here), and idempotent (healed blocks read full HP and aren't re-added).
					rescanDamagedBlocks(repairTarget);
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error in ShipAIShootListenerImpl", exception);
		}
	}

	/** Periodically re-records the target's reduced-HP blocks so the repair beam mends damage that wasn't loaded at order time. */
	private void rescanDamagedBlocks(SegmentController target) {
		if(target == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastBlockScan.get(target.getId());
		if(last != null && now - last < BLOCK_RESCAN_INTERVAL_MS) {
			return;
		}
		lastBlockScan.put(target.getId(), now);
		AIUtils.recordExistingBlockDamage(target);
	}

	/** Restores a slice of the target's Armor HP, sized by the configured per-second fraction and real elapsed time. */
	private void restoreArmorHP(SegmentController target) {
		if(target == null) {
			return;
		}
		try {
			ArmorHPCollection armor = ArmorHPCollection.getCollection(target);
			if(armor == null || armor.getMaxHP() <= 0 || armor.getCurrentHP() >= armor.getMaxHP()) {
				lastArmorRepair.remove(target.getId());
				return;
			}
			long now = System.currentTimeMillis();
			Long last = lastArmorRepair.put(target.getId(), now);
			if(last == null) {
				return; // first repair tick on this target — establish the time baseline, restore from next tick
			}
			double elapsedSec = Math.min(1.0, (now - last) / 1000.0); // clamp so a lag spike can't dump a huge heal
			double fraction = ConfigManager.getSystemConfig().armorHpRepairFractionPerSecond.getValue();
			armor.setCurrentHP(armor.getCurrentHP() + fraction * armor.getMaxHP() * elapsedSec);
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error restoring armor HP during repair", exception);
		}
	}

	private SegmentController getRepairTarget(ShipAIEntity shipAIEntity) {
		try {
			SimpleGameObject target = ((TargetProgram<?>) shipAIEntity.getEntity().getAiConfiguration().getAiEntityState().getCurrentProgram()).getTarget();
			return target instanceof SegmentController ? (SegmentController) target : null;
		} catch(Exception ignored) {
			return null;
		}
	}

	private void changeTarget(ShipAIEntity shipAIEntity) {
		if(!shipAIEntity.isOnServer()) {
			return;
		}
		try {
			Sector sector = GameServer.getUniverse().getSector(shipAIEntity.getEntity().getSectorId());
			for(SimpleGameObject simpleGameObject : sector.getEntities()) {
				if(simpleGameObject instanceof SegmentController segmentController) {
					if(canRepair(shipAIEntity.getEntity(), segmentController)) {
						((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).setTarget(segmentController);
						return;
					}
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error changing repair target", exception);
		}
		try {
			((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).setTarget(null);
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error resetting repair target", exception);
		}
	}

	private boolean isValidTarget(ShipAIEntity shipAIEntity) {
		if(!shipAIEntity.isOnServer()) {
			return false;
		}
		try {
			if(shipAIEntity == null || shipAIEntity.getCurrentProgram() == null) return false;
			SimpleGameObject target = ((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).getTarget();
			if(target instanceof SegmentController segmentController) {
				return canRepair(shipAIEntity.getEntity(), segmentController);
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error validating repair target", exception);
		}
		return false;
	}

	/** Whether {@code repairer} can repair {@code target}: a different entity, same-faction or allied, with damage to mend. */
	private boolean canRepair(SegmentController repairer, SegmentController target) {
		if(repairer == null || target == null || repairer.getId() == target.getId()) {
			return false;
		}
		boolean sameFaction = repairer.getFactionId() != 0 && repairer.getFactionId() == target.getFactionId();
		boolean ally = GameCommon.getGameState().getFactionManager().isFriend(repairer.getFactionId(), target.getFactionId());
		return (sameFaction || ally) && AIUtils.needsRepair(target);
	}

	// --- Aura Disruptor AI (merged from BetterChambers' AuraDisruptorShootListener) ---

	/** Fires any mounted Aura Disruptor at the current target if it is an enemy with an active Aura Projector; otherwise drops the target. */
	private void handleAuraDisruptor(ShipAIEntity shipAIEntity, AIControllerStateUnit<?> aiControllerStateUnit, Timer timer) {
		AuraDisruptorBeamElementManager elementManager = SegmentControllerUtils.getElementManager(shipAIEntity.getEntity(), AuraDisruptorBeamElementManager.class);
		if(elementManager == null || elementManager.totalSize <= 0) {
			return;
		}
		if(isValidDisruptorTarget(shipAIEntity)) {
			for(AuraDisruptorBeamCollectionManager collectionManager : elementManager.getCollectionManagers()) {
				for(AuraDisruptorBeamUnit unit : collectionManager.getElementCollections()) {
					if(unit.size() > 0 && !unit.isReloading(timer.currentTime) && unit.canUse(timer.currentTime, false)) {
						unit.fire(aiControllerStateUnit, timer);
					}
				}
			}
		} else {
			clearDisruptorTarget(shipAIEntity);
		}
	}

	/** Whether the AI's current target is an enemy ship carrying an active Aura Projector worth disrupting. */
	private boolean isValidDisruptorTarget(ShipAIEntity shipAIEntity) {
		try {
			SimpleGameObject target = ((TargetProgram<?>) shipAIEntity.getEntity().getAiConfiguration().getAiEntityState().getCurrentProgram()).getTarget();
			if(target instanceof ManagedUsableSegmentController<?> segmentController) {
				int myFaction = shipAIEntity.getEntity().getFactionId();
				int targetFaction = segmentController.getFactionId();
				if(myFaction > 0 && targetFaction > 0 && myFaction != targetFaction
						&& GameCommon.getGameState().getFactionManager().isEnemy(myFaction, targetFaction)) {
					AuraProjectorAddOn addOn = AuraProjectorAddOn.getActiveAura((ManagedUsableSegmentController<?>) segmentController);
					return addOn != null && addOn.isActive();
				}
			}
		} catch(Exception ignored) {
		}
		return false;
	}

	private void clearDisruptorTarget(ShipAIEntity shipAIEntity) {
		try {
			((TargetProgram<?>) shipAIEntity.getEntity().getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Failed to change target for Aura Disruptor", exception);
		}
	}
}
