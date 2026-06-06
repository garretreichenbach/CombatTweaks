package videogoose.combattweaks.utils;

import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.game.server.ai.program.fleetcontrollable.FleetControllableProgram;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.ai.stateMachines.FSMException;
import org.schema.schine.ai.stateMachines.Transition;
import api.utils.game.PlayerUtils;
import videogoose.combattweaks.manager.MoveManager;

import javax.vecmath.Vector3f;

public class AIUtils {

	private static final Vector3f targetVelocityTmp = new Vector3f();

	/**
	 * Whether a ship may be given tactical-map orders (move/attack/defend/mine/repair).
	 * <p>StarMade forces an ACTIVE non-fleet ship onto the autonomous Search-and-Destroy
	 * program every tick, which fights our per-tick targetPosition writes (causing the
	 * spinning/random-firing). Only fleeted ships run the passive FleetControllable program
	 * that actually obeys external orders, so we restrict commands to fleet members.</p>
	 */
	public static boolean canReceiveOrders(SegmentController entity) {
		return entity instanceof Ship && entity.isInFleet();
	}

	/** Whether the entity has any salvage (mining) beam blocks — required to accept a mine order. */
	public static boolean hasSalvageBeams(SegmentController entity) {
		if(!(entity instanceof Ship)) {
			return false;
		}
		try {
			return ((Ship) entity).getManagerContainer().getSalvage().getElementManager().totalSize > 0;
		} catch(Exception exception) {
			return false;
		}
	}

	/**
	 * Guard for order packet handlers: returns true if the entity can be ordered, otherwise
	 * notifies the issuing player (when known) and returns false.
	 */
	public static boolean canReceiveOrders(int entityId, PlayerState issuer) {
		SegmentController entity = EntityUtils.getEntityById(entityId);
		if(canReceiveOrders(entity)) {
			return true;
		}
		if(issuer != null) {
			String name = entity != null ? entity.getName() : "Ship";
			PlayerUtils.sendMessage(issuer, name + " must be in a fleet to receive orders.");
		}
		return false;
	}

	/**
	 * Ensures the ship is running FleetControllableProgram so fleet-state transitions work.
	 * If the ship already has one, it's reused; otherwise a new one is created and set.
	 */
	@SuppressWarnings("unchecked")
	private static FleetControllableProgram ensureFleetProgram(Ship ship) {
		ShipAIEntity aiEntity = ship.getAiConfiguration().getAiEntityState();
		if(aiEntity.getCurrentProgram() instanceof FleetControllableProgram) {
			return (FleetControllableProgram) aiEntity.getCurrentProgram();
		}
		FleetControllableProgram program = new FleetControllableProgram(aiEntity, false);
		aiEntity.setCurrentProgram(program);
		return program;
	}

	public static void setMineTarget(Ship ship, SegmentController asteroid) {
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			program.setTarget(asteroid);
			program.setSpecificTargetId(asteroid.getAsTargetId());
			program.suspend(false);
			program.getMachine().getFsm().stateTransition(Transition.FLEET_GET_TO_MINING_POS);
		} catch(FSMException e) {
			// Transition not available from current state — try resetting to idle first
			e.printStackTrace();
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_BREAKING);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_GET_TO_MINING_POS);
			} catch(FSMException exception) {
				exception.printStackTrace();
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	public static void setMineTarget(int shipId, int asteroidId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		SegmentController asteroid = EntityUtils.getEntityById(asteroidId);
		if(ship instanceof Ship && asteroid instanceof FloatingRock) {
			setMineTarget((Ship) ship, asteroid);
		}
	}

	public static void setRepairTarget(Ship ship, SegmentController target) {
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			program.setTarget(target);
			program.setSpecificTargetId(target.getAsTargetId());
			program.suspend(false);
			program.getMachine().getFsm().stateTransition(Transition.FLEET_REPAIR);
		} catch(FSMException e) {
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_BREAKING);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_REPAIR);
			} catch(FSMException exception) {
				exception.printStackTrace();
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	public static void setRepairTarget(int shipId, int targetId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		SegmentController target = EntityUtils.getEntityById(targetId);
		if(ship instanceof Ship && target != null) {
			setRepairTarget((Ship) ship, target);
		}
	}

	public static void setMoveToTarget(int shipId, int targetId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		SegmentController target = EntityUtils.getEntityById(targetId);
		if(ship instanceof Ship && target != null && ship.getWorldTransform() != null && target.getWorldTransform() != null) {
			Vector3f dest = MoveManager.computeDestination(ship, target);
			clearTarget((Ship) ship);
			MoveManager.getInstance().addMove(shipId, dest);
		}
	}

	public static void clearTarget(ManagedUsableSegmentController<?> ship) {
		try {
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setSpecificTargetId(-1);
		} catch(Exception exception) {
			exception.printStackTrace();
		}
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