package thederpgamer.combattweaks.listener;

import api.DebugFile;
import api.common.GameCommon;
import api.common.GameServer;
import api.listener.fastevents.ShipAIEntityAttemptToShootListener;
import api.utils.game.SegmentControllerUtils;
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
import thederpgamer.combattweaks.CombatTweaks;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ShipAIShootListener implements ShipAIEntityAttemptToShootListener {

	@Override
	public void doShooting(ShipAIEntity shipAIEntity, AIControllerStateUnit<?> aiControllerStateUnit, Timer timer) {
		RepairElementManager elementManager = SegmentControllerUtils.getElementManager(shipAIEntity.getEntity(), RepairElementManager.class);
		if(elementManager != null && elementManager.totalSize > 0) {
			if(!isValidTarget(shipAIEntity)) changeTarget(shipAIEntity);
			if(isValidTarget(shipAIEntity)) {
				for(RepairBeamCollectionManager collectionManager : elementManager.getCollectionManagers()) {
					for(RepairUnit unit : collectionManager.getElementCollections()) {
						if(unit.size() > 0 && !unit.isReloading(timer.currentTime) && unit.canUse(timer.currentTime, false)) {
							unit.fire(aiControllerStateUnit, timer);
							DebugFile.log("[INFO]: Entity "+ shipAIEntity.getEntity().getName() + " firing repair beam.", CombatTweaks.getInstance());
						}
					}
				}
			}
		}
	}

	private void changeTarget(ShipAIEntity shipAIEntity) {
		try {
			Sector sector = GameServer.getUniverse().getSector(shipAIEntity.getEntity().getSectorId());
			for(SimpleGameObject simpleGameObject : sector.getEntities()) {
				if(simpleGameObject instanceof SegmentController) {
					SegmentController segmentController = (SegmentController) simpleGameObject;
					if(GameCommon.getGameState().getFactionManager().isFriend(shipAIEntity.getEntity().getFactionId(), segmentController.getFactionId())) {
						if(segmentController.getHpController().getHp() < segmentController.getHpController().getMaxHp()) {
							((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).setTarget(segmentController);
							return;
						}
					}
				}
			}
		} catch(Exception ignored) { }
		try {
			((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).setTarget(null);
		} catch(Exception ignored) { }
	}

	private boolean isValidTarget(ShipAIEntity shipAIEntity) {
		try {
			SimpleGameObject target = ((TargetProgram<?>) ((shipAIEntity.getEntity()).getAiConfiguration().getAiEntityState().getCurrentProgram())).getTarget();
			if(target instanceof SegmentController) {
				SegmentController segmentController = (SegmentController) target;
				if(segmentController.getFactionId() > 0 && shipAIEntity.getEntity().getFactionId() > 0 && segmentController.getFactionId() != shipAIEntity.getEntity().getFactionId()) {
					if(GameCommon.getGameState().getFactionManager().isFriend(shipAIEntity.getEntity().getFactionId(), segmentController.getFactionId())) {
						return segmentController.getHpController().getHp() < segmentController.getHpController().getMaxHp();
					}
				}
			}
		} catch(Exception ignored) { }
		return false;
	}
}