package videogoose.combattweaks.utils;

import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.program.common.TargetProgram;

import javax.vecmath.Vector3f;

public class AIUtils {

	private static final Vector3f targetVelocityTmp = new Vector3f();

	public static void clearTarget(ManagedUsableSegmentController<?> ship) {
		try {
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setSpecificTargetId(-1);
		} catch(Exception ignored) {}
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(ship.getWorldTransform().origin);
		}
	}

	public static void clearTarget(int shipId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		if(ship instanceof Ship) {
			clearTarget((ManagedUsableSegmentController<?>) ship);
		}
	}

	public static void setAttackTarget(SegmentController from, SegmentController to) {
		if(from instanceof Ship) {
			Ship ship = (Ship) from;
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(to);
			ship.getAiConfiguration().getAiEntityState().getCurrentProgram().suspend(false);

			if(from.getNetworkObject() instanceof NetworkShip) {
				to.getPhysicsObject().getLinearVelocity(targetVelocityTmp);
				((NetworkShip) from.getNetworkObject()).targetVelocity.set(targetVelocityTmp);
				((NetworkShip) from.getNetworkObject()).targetPosition.set(to.getWorldTransform().origin);
			}
		} else {
			for(RailRelation child : from.railController.next) {
				if(child.docked.getSegmentController() instanceof Ship) {
					setAttackTarget(child.docked.getSegmentController(), to);
				}
			}
		}
		for(RailRelation child : from.railController.next) {
			if(child.docked.getSegmentController() instanceof Ship) {
				setAttackTarget(child.docked.getSegmentController(), to);
			}
		}
	}

	public static void setAttackTarget(int fromId, int toId) {
		SegmentController from = EntityUtils.getEntityById(fromId);
		SegmentController to = EntityUtils.getEntityById(toId);
		setAttackTarget(from, to);
	}
}