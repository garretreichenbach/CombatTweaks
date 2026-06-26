package videogoose.combattweaks.utils;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.WeaponElementManagerInterface;
import org.schema.game.common.controller.elements.beam.repair.RepairElementManager;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.world.Sector;
import org.schema.game.server.data.ServerConfig;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.GameServerState;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.game.server.ai.program.fleetcontrollable.FleetControllableProgram;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetAttackCycle;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetMining;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.ai.stateMachines.FSMException;
import org.schema.schine.ai.stateMachines.State;
import org.schema.schine.ai.stateMachines.Transition;
import api.utils.game.PlayerUtils;
import videogoose.combattweaks.manager.DefenseManager;
import videogoose.combattweaks.manager.MineManager;
import videogoose.combattweaks.manager.MoveManager;
import videogoose.combattweaks.manager.RepairManager;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.game.common.controller.SegmentBufferIteratorInterface;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SegmentData;

import javax.vecmath.Vector3f;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AIUtils {

	/** Ships currently ordered to attack → their target entity id (no manager tracks attack, unlike mine/defend/repair). */
	private static final Map<Integer, Integer> attackOrders = new ConcurrentHashMap<>();

	/**
	 * Per-ship entity it must <em>never</em> fire on — the friendly/neutral it was ordered to move-to (escort) or
	 * defend. A neutral escort/protect target isn't a faction-friend, so the engine's own friendly-fire guard
	 * ({@code doShooting}) doesn't spare it; without this an escorting ship shoots the very ship it's accompanying.
	 * Deliberately <b>not</b> cleared by {@link #clearAllOrders} (which the order queue runs on every move
	 * completion) so the protection persists after a one-shot move ends and the ship idles next to the escortee;
	 * it's cleared only by a subsequent attack/mine/repair/idle order (see {@code OrderQueueManager}).
	 */
	private static final Map<Integer, Integer> noFireTargets = new ConcurrentHashMap<>();

	/**
	 * Ships on a <em>Supporting Fire</em> order: they engage their attack target but <b>hold position</b> instead
	 * of running the engine's close/orbit/strafe dance. The flag is read by the engaging-state movement mixin to
	 * skip the orbital {@code moveTo} (firing and target-facing are left intact). Set alongside the attack target
	 * and cleared whenever the attack target is cleared (see {@link #clearTarget}).
	 */
	private static final java.util.Set<Integer> supportingFire = ConcurrentHashMap.newKeySet();

	/** Per-ship timestamp (ms) of when it was first seen without a fleet, for the drop grace period. */
	private static final Map<Integer, Long> unfleetedSince = new ConcurrentHashMap<>();
	/**
	 * How long a ship must be continuously without a fleet before we treat its order as truly gone.
	 * Editing a fleet briefly uncaches every member (the sync uncaches all then re-caches), so
	 * {@code isInFleet()} flips false for a moment on an unrelated edit; dropping orders on that single
	 * observation is what silently kills commanded ships' tasks until re-issued. A few seconds rides out
	 * the edit while still releasing ships that genuinely left the fleet.
	 */
	private static final long FLEET_LOSS_GRACE_MS = 6000;

	/**
	 * Whether a ship has <em>confirmed</em> left its fleet — i.e. it's been fleetless continuously past
	 * {@link #FLEET_LOSS_GRACE_MS}. Managers use this instead of a raw {@code !isInFleet()} so a transient
	 * un-fleeting (e.g. during a fleet edit's uncache/re-cache) doesn't drop a still-valid order.
	 */
	public static boolean confirmedLeftFleet(SegmentController ship) {
		if(ship == null) {
			return true;
		}
		if(ship.isInFleet()) {
			unfleetedSince.remove(ship.getId());
			return false;
		}
		long now = System.currentTimeMillis();
		Long since = unfleetedSince.putIfAbsent(ship.getId(), now);
		return since != null && now - since > FLEET_LOSS_GRACE_MS;
	}

	/**
	 * Whether a ship is under any CombatTweaks order (attack/defend/mine/repair). Used by the fleet
	 * mixin: an idle fleet force-breaks members that aren't idle, which would yank our commanded ships
	 * out of their combat/repair/mining states — so the fleet must leave commanded ships alone. (Move
	 * isn't included: it drives the ship via moveTo while it stays in the idle state, so the fleet
	 * never breaks it.)
	 */
	public static boolean isUnderCommand(int shipId) {
		return attackOrders.containsKey(shipId)
				|| MineManager.getInstance().getAssignedTarget(shipId) != null
				|| RepairManager.getInstance().getAssignedTarget(shipId) != null
				|| DefenseManager.getInstance().isDefending(shipId);
	}

	/**
	 * Whether weapon/beam/missile fire should be suppressed for the ship with this order id.
	 *
	 * <p>True when the ship is on a <em>peaceful</em> order (mining, moving, or defending without a live
	 * threat) and NOT actively engaging a target. Mining ships should only fire their salvage beams; ships
	 * executing a move should just move; a defender holding station should not shoot the friendly it's
	 * protecting. The instant a defender engages a threat it's added to {@code attackOrders} ({@link
	 * #isCombatOrder}), which overrides this and lets it fire. Pass the order-bearing ship id — for a turret,
	 * that's its rail root, since turrets fire on behalf of the ship they're on.</p>
	 *
	 * <p>Defending is included here (not just mining/moving): a defender <em>escorting</em> its protectee has
	 * a move order so it was already covered, but once it <em>arrives and holds</em> the escort move is
	 * dropped — leaving it un-suppressed and shooting the friendly it guards. Treating any non-engaging
	 * defender as peaceful closes that gap.</p>
	 */
	public static boolean shouldSuppressWeapons(int shipId) {
		boolean peaceful = MineManager.getInstance().getAssignedTarget(shipId) != null
				|| MoveManager.getInstance().getAssignedDestination(shipId) != null
				|| DefenseManager.getInstance().isDefending(shipId);
		return peaceful && !isCombatOrder(shipId);
	}

	/**
	 * Whether the ship is actively engaging a target (so it should fire weapons, not salvage, and must not
	 * have its fire suppressed as a "peaceful" order would).
	 *
	 * <p>This is keyed on {@link #attackOrders} only — NOT on "is defending". A defender that is merely
	 * escorting its protectee (no threat in range) has a move order but no attack target, and it must be
	 * treated as peaceful so {@code shouldSuppressWeapons} cancels its fire — otherwise it shoots while in
	 * transit toward the very ship it's protecting. The instant a threat appears, {@code DefenseManager}
	 * calls {@code setAttackTarget}, which adds the defender to {@code attackOrders}; from then it counts as
	 * in combat and fires normally. So engagement, not the standing defend assignment, gates weapons.</p>
	 */
	public static boolean isCombatOrder(int shipId) {
		return attackOrders.containsKey(shipId);
	}

	/** The entity this ship is ordered to attack, or null if it has no attack order. */
	public static Integer getAttackTarget(int shipId) {
		return attackOrders.get(shipId);
	}

	/** Mark {@code targetId} as the entity {@code shipId} must not fire on (its move-to/defend subject). */
	public static void setNoFireTarget(int shipId, int targetId) {
		noFireTargets.put(shipId, targetId);
	}

	/** Clear a ship's no-fire (escort/protect) subject — called when it's given an attack/mine/repair/idle order. */
	public static void clearNoFireTarget(int shipId) {
		noFireTargets.remove(shipId);
	}

	/** The entity this ship must not fire on (the friendly/neutral it's escorting or defending), or null. */
	public static Integer getNoFireTarget(int shipId) {
		return noFireTargets.get(shipId);
	}

	/** Mark (or clear) a ship as holding position while engaging — the Supporting Fire order. */
	public static void setSupportingFire(int shipId, boolean on) {
		if(on) {
			supportingFire.add(shipId);
		} else {
			supportingFire.remove(shipId);
		}
	}

	/** Whether the ship is on a Supporting Fire order (engage but hold position, no orbit/strafe). */
	public static boolean isSupportingFire(int shipId) {
		return supportingFire.contains(shipId);
	}

	/**
	 * The id under which an entity's CombatTweaks order is recorded. For a docked <em>turret</em> that's its
	 * rail root: a turret fires on behalf of — and its AI searches as — the ship it's docked to, but the
	 * attack order is only ever stored against the root ship's id (turrets are dispatched via
	 * {@code setTurretAttackTarget}, which never touches {@code attackOrders}). Resolving to the root here lets
	 * a turret's own target search (which calls {@code isEnemy} with the turret itself as the searcher) find
	 * the root's commanded attack target.
	 *
	 * <p>Only <b>turret-docked</b> entities are resolved — a plain rail-docked ship runs its own AI and orders
	 * and must NOT inherit its host's attack target (doing so makes it engage/fire independently with an
	 * invalid firing solution, spamming NaN raytraces). Everything else returns its own target id.</p>
	 */
	public static int orderId(SimpleTransformableSendableObject<?> entity) {
		if(entity instanceof SegmentController) {
			SegmentController sc = (SegmentController) entity;
			if(sc.getDockingController() != null && sc.getDockingController().isTurretDocking()
					&& sc.railController != null && sc.railController.getRoot() != null) {
				return sc.railController.getRoot().getId();
			}
		}
		return entity.getAsTargetId();
	}

	/**
	 * Whether the ship is currently running its attack cycle (searching for / closing on / engaging a
	 * target). We can't just check the program's target: the search state nulls the target on entry, so a
	 * target-based check reads false mid-search and makes the caller re-issue the order, restarting the
	 * search forever (the ship then just sits). The attack-cycle states all implement
	 * {@link FleetAttackCycle}, so checking the FSM state tells us the engagement is in progress.
	 */
	public static boolean isInAttackCycle(int shipId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		if(!(ship instanceof Ship s)) {
			return false;
		}
		try {
			State st = s.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().getCurrentState();
			return st instanceof FleetAttackCycle;
		} catch(Exception ignored) {
		}
		return false;
	}

	/** The simple class name of a ship's current AI FSM state (e.g. {@code FleetGettingToTarget}), for debug logging. */
	public static String currentStateName(Ship ship) {
		try {
			return ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().getCurrentState().getClass().getSimpleName();
		} catch(Exception exception) {
			return "<unknown:" + exception.getClass().getSimpleName() + ">";
		}
	}

	/** The simple class name of a ship's current AI program (e.g. {@code FleetControllableProgram}), for debug logging. */
	public static String currentProgramName(Ship ship) {
		try {
			return ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getClass().getSimpleName();
		} catch(Exception exception) {
			return "<unknown:" + exception.getClass().getSimpleName() + ">";
		}
	}

	/**
	 * Whether a ship may be given tactical-map orders (move/attack/defend/mine/repair).
	 * <p>StarMade forces an ACTIVE non-fleet ship onto the autonomous Search-and-Destroy
	 * program every tick, which fights our per-tick targetPosition writes (causing the
	 * spinning/random-firing). Only fleeted ships run the passive FleetControllable program
	 * that actually obeys external orders, so we restrict commands to fleet members.</p>
	 * <p><b>Docked turrets are exempt:</b> a turret runs its own "Turret" AI program (TurretProgram),
	 * not Search-and-Destroy, and engages a set target on its own — so it can be commanded without being
	 * in a fleet. This lets turret-mode orders work as long as the parent ship is commandable.</p>
	 */
	public static boolean canReceiveOrders(SegmentController entity) {
		return entity instanceof Ship && (entity.isDocked() || entity.isInFleet());
	}

	/**
	 * Rebases {@code target}'s transform into {@code reference}'s sector frame and returns it.
	 *
	 * <p>StarMade world-transform origins are <em>sector-local</em> — each entity's
	 * {@code getWorldTransform().origin} is relative to its own sector's center. So when a ship and its
	 * target sit in different sectors, subtracting their raw origins yields a meaningless vector: the
	 * direction points nowhere near the real target and the ship just sits still (the "won't move to a
	 * distant object" / "won't fly to a far asteroid" bug). This mirrors the engine's own targeting
	 * ({@code EngagingTargetSteady.findRotDir}): it calls {@link SimpleTransformableSendableObject#calcWorldTransformRelative}
	 * to express the target in the reference's sector frame, then reads the corrected origin. After this
	 * call the returned transform's {@code origin} is directly comparable with
	 * {@code reference.getWorldTransform().origin}. Same-sector is a cheap identity copy.</p>
	 *
	 * @return the target's transform in the reference's sector frame (its {@code clientTransform},
	 * which this call overwrites); falls back to the raw world transform if the sector can't be resolved.
	 */
	public static Transform getTransformRelativeTo(SegmentController reference, SimpleTransformableSendableObject<?> target) {
		try {
			Sector sec = ((GameServerState) reference.getState()).getUniverse().getSector(reference.getSectorId());
			if(sec != null) {
				target.calcWorldTransformRelative(reference.getSectorId(), sec.pos);
				return target.getClientTransform();
			}
		} catch(Exception ignored) {
		}
		return target.getWorldTransform();
	}

	/**
	 * Whether a repair target still has anything a repair ship can mend: reduced reactor HP, recorded
	 * destroyed/damaged blocks, OR depleted CombatTweaks Armor HP. Returns false for a destroyed/overheating
	 * target (reactor &le; 0 — nothing to save) and for a fully-intact one.
	 *
	 * <p>Armor HP is included deliberately: combat damage is mostly absorbed by the Armor HP pool (blocks stay
	 * intact, reactor full), and the engine's block-damage recorders don't survive a server restart — so a
	 * genuinely-damaged ally would otherwise report "nothing to repair" and the order would cancel immediately.
	 * Armor HP is synced/persisted, making it the reliable damage signal here.</p>
	 */
	public static boolean needsRepair(SegmentController target) {
		if(!(target instanceof Ship t)) {
			return false;
		}
		try {
			if(t.getReactorHp() <= 0) {
				return false; // destroyed / overheating — beyond repair
			}
			if(t.getReactorHp() < t.getReactorHpMax()) {
				return true;
			}
			if(t.getBlockKillRecorder().size() > 0 || t.getDamagedBlockRecorder().size() > 0) {
				return true;
			}
			ArmorHPCollection armor = ArmorHPCollection.getCollection(t);
			if(armor != null && armor.getMaxHP() > 0 && armor.getCurrentHP() < armor.getMaxHP() - 0.5) {
				return true;
			}
		} catch(Exception ignored) {
		}
		return false;
	}

	/**
	 * Re-populates a target's damaged-block recorder from its <em>persisted</em> block HP, so a repair beam
	 * can mend blocks whose in-memory damage records were lost — most importantly across a server restart,
	 * after which the recorder is empty even though the blocks are still damaged on disk. Without this the
	 * beam only heals the outer block it directly hits (recorder-independent) and never touches internal
	 * damage. Scans the loaded structure once and records every block currently below max HP; the engine's
	 * repair beam then drains the recorder as it heals. Server-only, best-effort.
	 *
	 * @return the number of damaged blocks recorded
	 */
	public static int recordExistingBlockDamage(SegmentController target) {
		if(!(target instanceof ManagedUsableSegmentController<?>) || !target.isOnServer()) {
			return 0;
		}
		try {
			DamagedBlockScanner scanner = new DamagedBlockScanner((ManagedUsableSegmentController<?>) target);
			target.getSegmentBuffer().iterateOverNonEmptyElement(scanner, true);
			return scanner.recorded;
		} catch(Exception exception) {
			CTLog.error("Failed to scan repair target for existing block damage", exception);
			return 0;
		}
	}

	/** Iterates a structure's blocks and records every one below max HP into its damaged-block recorder. */
	private static final class DamagedBlockScanner implements SegmentBufferIteratorInterface {
		private final SegmentPiece piece = new SegmentPiece();
		private final Vector3b helper = new Vector3b();
		private final ManagedUsableSegmentController<?> target;
		int recorded;

		DamagedBlockScanner(ManagedUsableSegmentController<?> target) {
			this.target = target;
		}

		@Override
		public boolean handle(Segment s, long lastChanged) {
			SegmentData data = s.getSegmentData();
			if(data == null) {
				return false;
			}
			for(int i = 0; i < SegmentData.BLOCK_COUNT; i++) {
				if(ElementKeyMap.isValidType(data.getType(i))) {
					SegmentData.getPositionFromIndex(i, helper);
					piece.setByReference(s, helper);
					if(piece.getHitpointsByte() < piece.getInfo().getMaxHitPointsByte()) {
						target.recordDamagedBlock(piece.getAbsoluteIndex());
						recorded++;
					}
				}
			}
			return false;
		}
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
	 * Whether the ship has any offensive weapon (damage beam, cannon, or missile) that actually deals damage.
	 * {@code getWeapons()} contains only those three weapon types — salvage and repair/astrotech beams are
	 * not weapons — and a system with no blocks reports a damage index of 0, so a repair-only or unarmed ship
	 * returns false. Used to keep weaponless ships from accepting attack orders.
	 */
	public static boolean hasWeapons(SegmentController entity) {
		if(!(entity instanceof Ship)) {
			return false;
		}
		try {
			for(WeaponElementManagerInterface w : ((Ship) entity).getManagerContainer().getWeapons()) {
				if(w.calculateWeaponDamageIndex() > 0) {
					return true;
				}
			}
		} catch(Exception ignored) {
		}
		return false;
	}

	/** Whether the ship has any repair (astrotech) beam blocks — required to accept a repair order. */
	public static boolean hasRepairBeams(SegmentController entity) {
		if(!(entity instanceof Ship)) {
			return false;
		}
		try {
			RepairElementManager m = SegmentControllerUtils.getElementManager((Ship) entity, RepairElementManager.class);
			return m != null && m.totalSize > 0;
		} catch(Exception ignored) {
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
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.RESTART);
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
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.RESTART);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_GET_TO_MINING_POS);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_MINE);
			} catch(FSMException exception) {
				exception.printStackTrace();
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	/**
	 * Leaves the FleetMining state (back to idle) so the ship stops firing — used when the target is too
	 * far to mine, so the ship flies over under full power instead of burning the beam (and reactor) into
	 * empty space. The mining assignment stays; the controller re-enters once it's close again.
	 */
	public static void exitMiningState(Ship ship) {
		try {
			ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.RESTART);
		} catch(Exception ignored) {
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
			// Current state doesn't accept FLEET_REPAIR — reset to idle first, then retry. Use RESTART (valid
			// from FleetBreaking and nearly every state), NOT FLEET_BREAKING: a ship already in FleetBreaking
			// rejects FLEET_BREAKING, and FleetBreaking has no FLEET_REPAIR exit anyway. idle accepts FLEET_REPAIR.
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.RESTART);
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

	/**
	 * Rebases a world point given in {@code sourceSectorId}'s local frame into the ship's current sector frame.
	 * StarMade world-transform origins are sector-local, so the offset between two sectors' frames is their
	 * sector-coordinate delta times the sector size. Lets a move-to-position placed in the player's sector be
	 * flown to correctly even when the commanded ship sits in a different sector (resolved at execution time, so
	 * a queued move stays correct if the ship crosses a boundary before the order runs). Returns the point
	 * unchanged for a same-sector move or if anything can't be resolved.
	 */
	public static Vector3f toShipSectorFrame(int shipId, Vector3f point, int sourceSectorId) {
		try {
			SegmentController ship = EntityUtils.getEntityById(shipId);
			if(ship == null || sourceSectorId < 0 || ship.getSectorId() == sourceSectorId) {
				return point;
			}
			Sector shipSec = ((GameServerState) ship.getState()).getUniverse().getSector(ship.getSectorId());
			Sector srcSec = ((GameServerState) ship.getState()).getUniverse().getSector(sourceSectorId);
			if(shipSec == null || srcSec == null) {
				return point;
			}
			int size = (Integer) ServerConfig.SECTOR_SIZE.getCurrentState();
			Vector3i sp = shipSec.pos;
			Vector3i src = srcSec.pos;
			return new Vector3f(point.x + (src.x - sp.x) * size, point.y + (src.y - sp.y) * size, point.z + (src.z - sp.z) * size);
		} catch(Exception ignored) {
			return point;
		}
	}

	public static void setMoveToTarget(int shipId, int targetId) {
		SegmentController ship = EntityUtils.getEntityById(shipId);
		SegmentController target = EntityUtils.getEntityById(targetId);
		boolean ok = ship instanceof Ship && target != null && ship.getWorldTransform() != null && target.getWorldTransform() != null;
		CTLog.debug("[MOVE] setMoveToTarget ship=" + shipId + " target=" + targetId + " accepted=" + ok
				+ (ship == null ? " (ship null)" : "") + (target == null ? " (target null)" : ""));
		if(ok) {
			Vector3f dest = MoveManager.computeDestination(ship, target);
			CTLog.debug("[MOVE] computed dest=(" + dest.x + "," + dest.y + "," + dest.z + ") for ship=" + shipId);
			clearTarget((Ship) ship);
			// Track the target entity so the destination is recomputed each tick (follows a moving target
			// and stays sector-correct as the ship crosses sector boundaries).
			MoveManager.getInstance().addMove(shipId, targetId, dest);
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
		// Zero the AI aim point, NOT the ship's own origin: targetPosition is the weapon aim/fire trigger
		// (the client shoots whenever targetPosition.lengthSquared() > 0), and a sector-local origin is itself
		// non-zero, so setting it to the origin would leave the ship "aiming" and firing at itself. (0,0,0)
		// clears the firing solution outright.
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(0, 0, 0);
		}
		attackOrders.remove(ship.getId()); // no longer attacking under our command
		supportingFire.remove(ship.getId()); // and no longer holding position to support
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
	 * Gently bleeds a ship's linear velocity by {@code factor} (e.g. 0.85 keeps 85% each tick). Unlike
	 * {@link ShipAIEntity#stop()} (which slams velocity to 30% per tick — a visible jerk), applying a mild
	 * factor every tick gives a smooth deceleration, used for coasting up to a mining stand-off without the
	 * start/stop stutter.
	 */
	public static void brakeShip(SegmentController ship, float factor) {
		if(ship == null) {
			return;
		}
		try {
			Object physics = ship.getPhysicsDataContainer().getObject();
			if(physics instanceof RigidBody body) {
				Vector3f v = body.getLinearVelocity(new Vector3f());
				v.scale(factor);
				body.setLinearVelocity(v);
			}
		} catch(Exception ignored) {
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
		attackOrders.put(ship.getId(), to.getId()); // mark commanded (with target) so the fleet won't break it out, and the map can draw the attack line
		CTLog.debug("[ATTACK] engageWithFleetShip ship=" + ship.getId() + " (" + ship.getName() + ") target=" + to.getId()
				+ " (" + to.getName() + ") program=" + currentProgramName(ship) + " state=" + currentStateName(ship)
				+ " active=" + ship.getAiConfiguration().getAiEntityState().isActive() + " inFleet=" + ship.isInFleet()
				+ " docked=" + ship.isDocked());
		try {
			((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);
			FleetControllableProgram program = ensureFleetProgram(ship);
			if(program.getTarget() == to) {
				CTLog.debug("[ATTACK] ship=" + ship.getId() + " already engaging target=" + to.getId() + " (state=" + currentStateName(ship) + ") — not re-transitioning");
				return; // already attacking this target — don't re-transition and interrupt the engagement
			}
			program.setTarget(to);
			program.setSpecificTargetId(to.getAsTargetId());
			program.suspend(false);
			program.getMachine().getFsm().stateTransition(Transition.SEARCH_FOR_TARGET);
			CTLog.debug("[ATTACK] ship=" + ship.getId() + " SEARCH_FOR_TARGET ok — now state=" + currentStateName(ship));
		} catch(FSMException e) {
			// Not currently in a state that allows SEARCH_FOR_TARGET — reset to idle first, then retry.
			CTLog.debug("[ATTACK] ship=" + ship.getId() + " SEARCH_FOR_TARGET rejected from state=" + currentStateName(ship) + " — breaking to idle then retrying");
			try {
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.RESTART);
				ship.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.SEARCH_FOR_TARGET);
				CTLog.debug("[ATTACK] ship=" + ship.getId() + " retry SEARCH_FOR_TARGET ok — now state=" + currentStateName(ship));
			} catch(FSMException exception) {
				CTLog.error("[ATTACK] ship=" + ship.getId() + " retry SEARCH_FOR_TARGET failed from state=" + currentStateName(ship), exception);
			}
		} catch(Exception exception) {
			CTLog.error("[ATTACK] ship=" + ship.getId() + " engage failed", exception);
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