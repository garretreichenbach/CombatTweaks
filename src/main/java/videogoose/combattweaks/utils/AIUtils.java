package videogoose.combattweaks.utils;

import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.program.common.TargetProgram;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

import javax.vecmath.Vector3f;

public class AIUtils {

	private static final Vector3f targetVelocityTmp = new Vector3f();

	public static void clearTarget(ManagedUsableSegmentController<?> ship) {
		try {
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
		} catch(Exception ignored) {}
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(ship.getWorldTransform().origin);
		}
	}

	public static void setAttackTarget(ManagedUsableSegmentController<?> from, SegmentController to) {
		((AIConfiguationElements<Boolean>) from.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
		((TargetProgram<?>) from.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(to);
		from.getAiConfiguration().getAiEntityState().getCurrentProgram().suspend(false);

		if(from.getNetworkObject() instanceof NetworkShip) {
			to.getPhysicsObject().getLinearVelocity(targetVelocityTmp);
			((NetworkShip) from.getNetworkObject()).targetVelocity.set(targetVelocityTmp);
			((NetworkShip) from.getNetworkObject()).targetPosition.set(to.getWorldTransform().origin);
		}

		if(from.railController.isRoot() && TacticalMapGUIDrawer.getInstance().drawMap.containsKey(from.getId())) {
			TacticalMapGUIDrawer.getInstance().drawMap.get(from.getId()).setCurrentTarget(to);
		}

		for(RailRelation child : from.railController.next) {
			if(child.docked.getSegmentController() instanceof ManagedUsableSegmentController) {
				setAttackTarget((ManagedUsableSegmentController<?>) child.docked.getSegmentController(), to);
			}
		}
	}
}