package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.utils.AIUtils;

import javax.vecmath.Vector3f;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages mining operations for individual ships.
 * Moves ships to mining targets, then maintains mining behavior.
 */
public class MineManager {

	/** Extra clearance to avoid collision with asteroid. */
	private static final float PADDING = 200.0f;
	/** Distance at which the ship is considered in mining range (matches game's salvage beam range). */
	private static final float MINING_RANGE = 720.0f;
	/** Hysteresis buffer to prevent toggling between mining and moving. */
	private static final float MINING_RANGE_HYSTERESIS = 100.0f;
	/** Distance at which to abandon mining if drifted away. */
	private static final float MINING_REACQUIRE_DISTANCE = 900.0f;
	private static final int TICK_INTERVAL_SECONDS = 5;
	/** Direction change threshold before resending moveTo command. */
	private static final float DIRECTION_CHANGE_THRESHOLD = 0.1f;
	private static MineManager instance;
	/** Maps ship entity ID → asteroid entity ID. */
	private final ConcurrentHashMap<Integer, Integer> assignments = new ConcurrentHashMap<>();
	/** Tracks which ships are already in mining range and actively mining. */
	private final ConcurrentHashMap<Integer, Boolean> miningStates = new ConcurrentHashMap<>();
	/** Tracks which ships have had their mining target set (to avoid repeated state transitions). */
	private final ConcurrentHashMap<Integer, Boolean> mineTargetSet = new ConcurrentHashMap<>();
	/** Caches last movement direction sent to avoid unnecessary re-commands. */
	private final ConcurrentHashMap<Integer, Vector3f> lastDirections = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private final Vector3f tmpMoveDir = new Vector3f();

	private MineManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-MineManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public static MineManager getInstance() {
		if(instance == null) {
			synchronized(MineManager.class) {
				if(instance == null) {
					instance = new MineManager();
				}
			}
		}
		return instance;
	}

	/**
	 * Compute a safe destination offset from {@code asteroid} for {@code ship} —
	 * outside the asteroid's bounding sphere (plus {@link #PADDING}) in the direction
	 * the ship is currently located relative to the asteroid.
	 */
	public static Vector3f computeMiningPosition(SegmentController ship, SegmentController asteroid) {
		Vector3f asteroidPos = asteroid.getWorldTransform().origin;
		Vector3f shipPos = ship.getWorldTransform().origin;

		// Direction from asteroid toward ship
		Vector3f dir = new Vector3f();
		dir.sub(shipPos, asteroidPos);
		float len = dir.length();
		if(len < 0.01f) {
			dir.set(0, 0, 1); // fallback if perfectly coincident
		} else {
			dir.scale(1.0f / len);
		}

		// Conservative bounding sphere radius from bounding box half-diagonal
		float bbRadius = 0;
		BoundingBox bb = asteroid.getBoundingBox();
		if(bb != null) {
			float dx = (bb.max.x - bb.min.x) * 0.5f;
			float dy = (bb.max.y - bb.min.y) * 0.5f;
			float dz = (bb.max.z - bb.min.z) * 0.5f;
			bbRadius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		float offset = bbRadius + PADDING;
		Vector3f dest = new Vector3f(asteroidPos);
		dir.scale(offset);
		dest.add(dir);
		return dest;
	}

	/** Register a mining order for the given ship targeting the given asteroid. Replaces any existing order. */
	public void addMine(int shipId, int asteroidId) {
		assignments.put(shipId, asteroidId);
		MoveManager.getInstance().removeMove(shipId);
		miningStates.remove(shipId); // Reset mining state when reassigned
		mineTargetSet.remove(shipId); // Reset target-set flag when reassigned
	}

	/**
	 * Returns the asteroid id assigned to the given ship, or null if none.
	 */
	public Integer getAssignedTarget(int shipId) {
		return assignments.get(shipId);
	}

	/** Cancel any active mining order for the given ship. */
	public void removeMine(int shipId) {
		assignments.remove(shipId);
		miningStates.remove(shipId);
		mineTargetSet.remove(shipId);
		lastDirections.remove(shipId);
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!updateMine(entry.getKey(), entry.getValue())) {
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("MineManager tick error", e);
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the entity no longer exists or mining is complete.
	 * Ships stay in the manager to maintain mining if disrupted.
	 */
	private boolean updateMine(int shipId, int asteroidId) {
		SimpleGameObject shipObj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		if(!(shipObj instanceof Ship)) return false;

		Ship ship = (Ship) shipObj;
		if(ship.getWorldTransform() == null) return false;

		SimpleGameObject asteroidObj = (SimpleGameObject) GameCommon.getGameObject(asteroidId);
		if(!(asteroidObj instanceof FloatingRock)) return false;

		FloatingRock asteroid = (FloatingRock) asteroidObj;
		if(asteroid.getWorldTransform() == null) return false;

		// Note: FloatingRock doesn't have a standard health property
		// Mining will continue until the asteroid is removed from the world

		// Keep AI active
		//noinspection unchecked
		((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		Vector3f shipPos = ship.getWorldTransform().origin;
		Vector3f asteroidPos = asteroid.getWorldTransform().origin;
		float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, asteroidPos.x, asteroidPos.y, asteroidPos.z);

		boolean isMining = miningStates.getOrDefault(shipId, false);

		if(isMining) {
			// Ship is actively mining; check if it drifted too far away
			if(dist > MINING_REACQUIRE_DISTANCE) {
				// Drifted away (e.g., asteroid moved or ship was pushed), reacquire
				miningStates.put(shipId, false);
			} else {
				// Maintain mining target
				applyMiningBehavior(ship, asteroid);
				return true;
			}
		}

		// Moving toward mining position
		if(dist <= MINING_RANGE) {
			// In mining range; switch to mining state
			miningStates.put(shipId, true);
			applyMiningBehavior(ship, asteroid);
			return true;
		}

		// Calculate progressive braking as ship approaches
		float speedScale = 1.0f;
		float hardBrakeDistance = MINING_RANGE;
		float mediumBrakeDistance = MINING_RANGE * 2.0f;

		if(dist < mediumBrakeDistance) {
			// Linear interpolation: 50% speed at mediumBrakeDistance, 10% at hardBrakeDistance
			float range = mediumBrakeDistance - hardBrakeDistance;
			float progress = (dist - hardBrakeDistance) / range;
			speedScale = 0.1f + progress * 0.9f;
		}

		// Move toward mining position
		Vector3f miningPos = computeMiningPosition(ship, asteroid);
		applyMovement(ship, miningPos, speedScale);
		return true;
	}

	/**
	 * Apply movement toward mining position with the given speed scale.
	 * Only sends moveTo if direction changed significantly to avoid wiggling.
	 */
	private void applyMovement(Ship ship, Vector3f destination, float speedScale) {
		if(ship.getNetworkObject() instanceof NetworkShip) {
			ship.getNetworkObject().targetVelocity.set(0, 0, 0);
			ship.getNetworkObject().targetPosition.set(destination);
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
	 * Apply mining behavior: use AIUtils to properly set up the mining state machine.
	 * Only sets the target once per mining assignment to avoid repeated state transition errors.
	 * Maintains a subtle orientation vector toward the asteroid to keep the ship facing it.
	 */
	private void applyMiningBehavior(Ship ship, FloatingRock asteroid) {
		int shipId = ship.getId();
		if(!mineTargetSet.getOrDefault(shipId, false)) {
			// Clear movement commands when entering mining mode
			if(ship.getNetworkObject() instanceof NetworkShip) {
				ship.getNetworkObject().targetVelocity.set(0, 0, 0);
				ship.getNetworkObject().targetPosition.set(ship.getWorldTransform().origin);
			}
			lastDirections.remove(shipId);
			AIUtils.setMineTarget(ship, asteroid);
			mineTargetSet.put(shipId, true);
		} else {
			// While mining, maintain orientation towards asteroid with a very small movement vector
			// This ensures the ship's weapons point at the target without actually moving
			Vector3f shipPos = ship.getWorldTransform().origin;
			Vector3f asteroidPos = asteroid.getWorldTransform().origin;
			tmpMoveDir.sub(asteroidPos, shipPos);
			float dist = tmpMoveDir.length();
			if(dist > 0.1f) {
				tmpMoveDir.scale(0.001f / dist); // Tiny orientation vector, won't actually move ship
				ShipAIEntity aiEntity = ship.getAiConfiguration().getAiEntityState();
				aiEntity.moveTo(GameServer.getServerState().getController().getTimer(), tmpMoveDir, true);
			}
		}
	}
}
