package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.combattweaks.CombatTweaks;

import javax.vecmath.Vector3f;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages repair operations for individual ships.
 * Moves ships to repair targets, then maintains repair behavior.
 */
public class RepairManager {

	/** Extra clearance to avoid collision with repair target. */
	private static final float PADDING = 200.0f;
	/** Distance at which the ship is considered in repair range. */
	private static final float REPAIR_RANGE = 300.0f;
	/** Distance at which to abandon repair if drifted away. */
	private static final float REPAIR_REACQUIRE_DISTANCE = 500.0f;
	private static final int TICK_INTERVAL_SECONDS = 5;
	/** Direction change threshold before resending moveTo command. */
	private static final float DIRECTION_CHANGE_THRESHOLD = 0.1f;
	private static RepairManager instance;
	/** Maps ship entity ID → target entity ID. */
	private final ConcurrentHashMap<Integer, Integer> assignments = new ConcurrentHashMap<>();
	/** Tracks which ships are already in repair range and actively repairing. */
	private final ConcurrentHashMap<Integer, Boolean> repairingStates = new ConcurrentHashMap<>();
	/** Caches last movement direction sent to avoid unnecessary re-commands. */
	private final ConcurrentHashMap<Integer, Vector3f> lastDirections = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private final Vector3f tmpMoveDir = new Vector3f();

	private RepairManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-RepairManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public static RepairManager getInstance() {
		if(instance == null) {
			synchronized(RepairManager.class) {
				if(instance == null) {
					instance = new RepairManager();
				}
			}
		}
		return instance;
	}

	/**
	 * Compute a safe destination offset from {@code target} for {@code ship} —
	 * outside the target's bounding sphere (plus {@link #PADDING}) in the direction
	 * the ship is currently located relative to the target.
	 */
	public static Vector3f computeRepairPosition(SegmentController ship, SegmentController target) {
		Vector3f targetPos = target.getWorldTransform().origin;
		Vector3f shipPos = ship.getWorldTransform().origin;

		// Direction from target toward ship
		Vector3f dir = new Vector3f();
		dir.sub(shipPos, targetPos);
		float len = dir.length();
		if(len < 0.01f) {
			dir.set(0, 0, 1); // fallback if perfectly coincident
		} else {
			dir.scale(1.0f / len);
		}

		// Conservative bounding sphere radius from bounding box half-diagonal
		float bbRadius = 0;
		BoundingBox bb = target.getBoundingBox();
		if(bb != null) {
			float dx = (bb.max.x - bb.min.x) * 0.5f;
			float dy = (bb.max.y - bb.min.y) * 0.5f;
			float dz = (bb.max.z - bb.min.z) * 0.5f;
			bbRadius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		float offset = bbRadius + PADDING;
		Vector3f dest = new Vector3f(targetPos);
		dir.scale(offset);
		dest.add(dir);
		return dest;
	}

	/** Register a repair order for the given ship targeting the given object. Replaces any existing order. */
	public void addRepair(int shipId, int targetId) {
		assignments.put(shipId, targetId);
		repairingStates.remove(shipId); // Reset repairing state when reassigned
	}

	/** Cancel any active repair order for the given ship. */
	public void removeRepair(int shipId) {
		assignments.remove(shipId);
		repairingStates.remove(shipId);
		lastDirections.remove(shipId);
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!updateRepair(entry.getKey(), entry.getValue())) {
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("RepairManager tick error", e);
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the entity no longer exists or repair is complete.
	 * Ships stay in the manager to maintain repair if disrupted.
	 */
	private boolean updateRepair(int shipId, int targetId) {
		SimpleGameObject shipObj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		if(!(shipObj instanceof Ship)) return false;

		Ship ship = (Ship) shipObj;
		if(ship.getWorldTransform() == null) return false;

		SimpleGameObject targetObj = (SimpleGameObject) GameCommon.getGameObject(targetId);
		if(targetObj == null) return false;

		SegmentController target = (SegmentController) targetObj;
		if(target.getWorldTransform() == null) return false;

		// Check if target is fully repaired (reactor hp at max or destroyed)
		if(target instanceof Ship) {
			Ship targetShip = (Ship) target;
			try {
				long reactorHp = targetShip.getReactorHp();
				long maxHp = targetShip.getReactorHpMax();
				if(reactorHp >= maxHp || reactorHp <= 0) return false;
			} catch(Exception ignored) {
				// Target doesn't have reactor methods
			}
		}

		// Keep AI active
		//noinspection unchecked
		((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		Vector3f shipPos = ship.getWorldTransform().origin;
		Vector3f targetPos = target.getWorldTransform().origin;
		float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, targetPos.x, targetPos.y, targetPos.z);

		boolean isRepairing = repairingStates.getOrDefault(shipId, false);

		if(isRepairing) {
			// Ship is actively repairing; check if it drifted too far away
			if(dist > REPAIR_REACQUIRE_DISTANCE) {
				// Drifted away (e.g., target moved or ship was pushed), reacquire
				repairingStates.put(shipId, false);
			} else {
				// Maintain repair target with low-velocity approach to adjust position
				applyRepairBehavior(ship, target, 0.05f);
				return true;
			}
		}

		// Moving toward repair position
		if(dist <= REPAIR_RANGE) {
			// In repair range; switch to repairing state
			repairingStates.put(shipId, true);
			applyRepairBehavior(ship, target, 0.05f);
			return true;
		}

		// Calculate progressive braking as ship approaches
		float speedScale = 1.0f;
		float hardBrakeDistance = REPAIR_RANGE;
		float mediumBrakeDistance = REPAIR_RANGE * 2.0f;

		if(dist < hardBrakeDistance) {
			speedScale = 0.1f; // Hard brake near target
		} else if(dist < mediumBrakeDistance) {
			// Linear interpolation: 50% speed at mediumBrakeDistance, 10% at hardBrakeDistance
			float range = mediumBrakeDistance - hardBrakeDistance;
			float progress = (dist - hardBrakeDistance) / range;
			speedScale = 0.1f + progress * 0.9f;
		}

		// Move toward repair position
		Vector3f repairPos = computeRepairPosition(ship, target);
		applyMovement(ship, repairPos, speedScale);
		return true;
	}

	/**
	 * Apply movement toward repair position with the given speed scale.
	 * Only sends moveTo if direction changed significantly to avoid wiggling.
	 */
	private void applyMovement(Ship ship, Vector3f destination, float speedScale) {
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(destination);
		}

		Vector3f shipPos = ship.getWorldTransform().origin;
		tmpMoveDir.sub(destination, shipPos);
		tmpMoveDir.scale(speedScale);

		// Check if direction changed significantly before sending moveTo
		Vector3f lastDir = lastDirections.get(ship.getId());
		if(lastDir != null) {
			float angleDiff = Math.abs(tmpMoveDir.angle(lastDir));
			if(angleDiff < DIRECTION_CHANGE_THRESHOLD) {
				return; // Direction hasn't changed enough, skip moveTo
			}
		}

		lastDirections.put(ship.getId(), new Vector3f(tmpMoveDir));
		ShipAIEntity aiEntity = ship.getAiConfiguration().getAiEntityState();
		aiEntity.moveTo(GameServer.getServerState().getController().getTimer(), tmpMoveDir, true);
	}

	/**
	 * Apply repair behavior: target the object and maintain position with gentle correction.
	 * Repair beams will only fire if target is friendly (same faction) and has reactor damage.
	 */
	private void applyRepairBehavior(Ship ship, SegmentController target, float speedScale) {
		// Set the target as the current repair target
		try {
			TargetProgram<?> program = (TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram();
			program.setTarget(target);
			program.setSpecificTargetId(target.getId());
		} catch(Exception ignored) {
		}

		// Note: Ships should NOT move while repairing to avoid complications with physics
		// and target tracking. Repair beams have enough range to work from a stationary
		// position once in repair range.
	}
}
