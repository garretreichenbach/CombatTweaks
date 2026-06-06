package videogoose.combattweaks.utils;

import com.bulletphysics.dynamics.RigidBody;
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
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetMining;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.ai.stateMachines.FSMException;
import org.schema.schine.ai.stateMachines.Transition;
import api.utils.game.PlayerUtils;
import videogoose.combattweaks.manager.DefenseManager;
import videogoose.combattweaks.manager.MineManager;
import videogoose.combattweaks.manager.MoveManager;
import videogoose.combattweaks.manager.RepairManager;

import javax.vecmath.Vector3f;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AIUtils {

	/** Ships currently ordered to attack (no manager tracks attack, unlike mine/defend/repair). */
	private static final Set<Integer> attackOrders = ConcurrentHashMap.newKeySet();

	/**
	 * Whether a ship is under any CombatTweaks order (attack/defend/mine/repair). Used by the fleet
	 * mixin: an idle fleet force-breaks members that aren't idle, which would yank our commanded ships
	 * out of their combat/repair/mining states — so the fleet must leave commanded ships alone. (Move
	 * isn't included: it drives the ship via moveTo while it stays in the idle state, so the fleet
	 * never breaks it.)
	 */
	public static boolean isUnderCommand(int shipId) {
		return attackOrders.contains(shipId)
				|| MineManager.getInstance().getAssignedTarget(shipId) != null
				|| RepairManager.getInstance().getAssignedTarget(shipId) != null
				|| DefenseManager.getInstance().isDefending(shipId);
	}

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

	/** True if the ship's AI is currently in the engine's FleetMining state (actively salvaging). */
	public static boolean isMiningState(Ship ship) {
		try {
			return ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().getCurrentState() instanceof FleetMining;
		} catch(Exception exception) {
			return false;
		}
	}

	/**
	 * Drives the ship straight into the engine's FleetMining state with the asteroid as the target.
	 *
	 * <p>The transitions go idle → FLEET_GET_TO_MINING_POS (formationMining) → FLEET_MINE (mining)
	 * back-to-back in one call, so the formation state's onUpdate (which restarts a lone ship that is
	 * its own flagship) never runs — but FleetMining itself, which has no flagship dependency, then does
	 * the real block-finding, aiming and salvage firing exactly like NPC miners. The ship must already
	 * be within salvage range of the rock (FleetMining doesn't fly there); the caller navigates it
	 * close first.</p>
	 */
	public static void enterMiningState(Ship ship, SegmentController asteroid) {
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			program.setTarget(asteroid);
			program.setSpecificTargetId(asteroid.getAsTargetId());
			program.suspend(false);
			program.getMachine().getFsm().stateTransition(Transition.FLEET_GET_TO_MINING_POS);
			program.getMachine().getFsm().stateTransition(Transition.FLEET_MINE);
		} catch(FSMException e) {
			// Not in a state that allows the transition — reset to idle first, then retry the pair.
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_BREAKING);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_GET_TO_MINING_POS);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_MINE);
			} catch(FSMException exception) {
				exception.printStackTrace();
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	public static void setRepairTarget(Ship ship, SegmentController target) {
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			if(program.getTarget() == target) {
				return; // already repairing this target — don't re-transition and interrupt it
			}
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
		// Clear any mining "docking target" avoidance bypass so the ship resumes avoiding the asteroid.
		try {
			Object ai = ship.getAiConfiguration().getAiEntityState();
			if(ai instanceof ShipAIEntity) {
				((ShipAIEntity) ai).setDockingTarget(null);
			}
		} catch(Exception ignored) {
		}
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(ship.getWorldTransform().origin);
		}
		attackOrders.remove(ship.getId()); // no longer attacking under our command
	}

	public static void clearTarget(int shipId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		if(ship instanceof Ship) {
			clearTarget((ManagedUsableSegmentController<?>) ship);
		}
	}

	/**
	 * Immediately brings a ship to rest (zeroes linear and angular velocity). In space a ship with no
	 * order just coasts on its residual velocity, so issuing "idle" needs an explicit halt or the ship
	 * keeps drifting/spinning.
	 */
	public static void haltShip(int shipId) {
		SegmentController sc = EntityUtils.getEntityById(shipId);
		if(sc == null) {
			return;
		}
		try {
			Object physics = sc.getPhysicsDataContainer().getObject();
			if(physics instanceof RigidBody body) {
				body.setLinearVelocity(new Vector3f(0, 0, 0));
				body.setAngularVelocity(new Vector3f(0, 0, 0));
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	/**
	 * Clears every standing order for a ship — move, defend, mine, repair and any AI attack target.
	 * Orders are mutually exclusive, so this must be called before assigning a new one; otherwise a
	 * previous order's data lingers (e.g. a ship keeps firing salvage from a stale mine assignment
	 * after being told to move).
	 */
	public static void clearAllOrders(int shipId) {
		DefenseManager.getInstance().removeDefense(shipId);
		MineManager.getInstance().removeMine(shipId);
		RepairManager.getInstance().removeRepair(shipId);
		MoveManager.getInstance().removeMove(shipId);
		clearTarget(shipId);
	}

	public static void setAttackTarget(SegmentController from, SegmentController to) {
		if(from == null || to == null) {
			return;
		}
		if(from instanceof Ship ship) {
			if(ship.railController.isDocked()) {
				// Docked turret: its own turret AI engages once a specific target is set.
				setTurretAttackTarget(ship, to);
			} else {
				engageWithFleetShip(ship, to);
			}
		}
		// Cascade the order to docked turrets so they engage the same target.
		if(from.railController != null && from.railController.next != null) {
			for(RailRelation child : from.railController.next) {
				if(child.docked != null && child.docked.getSegmentController() instanceof Ship && !child.docked.getSegmentController().equals(from)) {
					setAttackTarget(child.docked.getSegmentController(), to);
				}
			}
		}
	}

	/**
	 * Make a fleeted (non-turret) ship actually engage a specific target. Just setting the program's
	 * target leaves a FleetControllable ship parked in its passive idle state; it must be driven
	 * through the SEARCH_FOR_TARGET transition, and the target must be set as the SPECIFIC target id
	 * (the search state nulls a plain target and would otherwise re-acquire a random enemy).
	 */
	private static void engageWithFleetShip(Ship ship, SegmentController to) {
		attackOrders.add(ship.getId()); // mark commanded so the idle fleet won't break it out of combat
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			if(program.getTarget() == to) {
				return; // already attacking this target — don't re-transition and interrupt the engagement
			}
			program.setTarget(to);
			program.setSpecificTargetId(to.getAsTargetId());
			program.suspend(false);
			program.getMachine().getFsm().stateTransition(Transition.SEARCH_FOR_TARGET);
		} catch(FSMException e) {
			// Not currently in a state that allows SEARCH_FOR_TARGET — reset to idle first, then retry.
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_BREAKING);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.SEARCH_FOR_TARGET);
			} catch(FSMException exception) {
				exception.printStackTrace();
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	/** Point a docked turret's AI at a specific target; the turret's own AI handles the firing. */
	private static void setTurretAttackTarget(Ship turret, SegmentController to) {
		try {
			((AIConfiguationElements<Boolean>) turret.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			if(turret.getAiConfiguration().getAiEntityState().getCurrentProgram() instanceof TargetProgram<?> tp) {
				tp.setTarget(to);
				tp.setSpecificTargetId(to.getAsTargetId());
				tp.suspend(false);
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	public static void setAttackTarget(int fromId, int toId) {
		SegmentController from = EntityUtils.getEntityById(fromId);
		SegmentController to = EntityUtils.getEntityById(toId);
		setAttackTarget(from, to);
	}
}