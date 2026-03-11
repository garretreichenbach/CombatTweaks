package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
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
 * Manages one-shot "move to position" orders server-side.
 * Each tick, navigates the ship toward the stored destination until
 * it is within {@link #ARRIVAL_DISTANCE} world units, then removes the order.
 */
public class MoveManager {

	/** Extra clearance added on top of the target's bounding sphere radius. */
	public static final float PADDING = 150.0f;
	/** Distance at which the ship is considered to have arrived. */
	private static final float ARRIVAL_DISTANCE = 200.0f;
	/** Distance at which to start progressive braking (50% speed). */
	private static final float MEDIUM_BRAKE_DISTANCE = 400.0f;
	/** Distance at which to apply hard braking (10% speed). */
	private static final float HARD_BRAKE_DISTANCE = 100.0f;
	/** Speed scale at hard brake distance. */
	private static final float HARD_BRAKE_SCALE = 0.1f;
	/** Distance at which to abandon holding position and reacquire. */
	private static final float HOLDING_REACQUIRE_DISTANCE = 400.0f;
	private static final int TICK_INTERVAL_SECONDS = 5;
	/** Direction change threshold before resending moveTo command. */
	private static final float DIRECTION_CHANGE_THRESHOLD = 0.1f;
	private static MoveManager instance;
	/** Maps ship entity ID → destination world position. */
	private final ConcurrentHashMap<Integer, Vector3f> assignments = new ConcurrentHashMap<>();
	/** Tracks which ships have arrived and are holding position. */
	private final ConcurrentHashMap<Integer, Boolean> arrivedStates = new ConcurrentHashMap<>();
	/** Caches last movement direction sent to avoid unnecessary re-commands. */
	private final ConcurrentHashMap<Integer, Vector3f> lastDirections = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private final Vector3f tmpMoveDir = new Vector3f();

	private MoveManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-MoveManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public static MoveManager getInstance() {
		if(instance == null) {
			synchronized(MoveManager.class) {
				if(instance == null) {
					instance = new MoveManager();
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
	public static Vector3f computeDestination(SegmentController ship, SegmentController target) {
		Vector3f targetPos = target.getWorldTransform().origin;
		Vector3f shipPos = ship.getWorldTransform().origin;

		// Direction from target toward ship (approach from where the ship already is)
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

	/** Register a move order for the given ship to the given destination. Replaces any existing order. */
	public void addMove(int shipId, Vector3f destination) {
		assignments.put(shipId, new Vector3f(destination));
	}

	/** Cancel any active move order for the given ship. */
	public void removeMove(int shipId) {
		assignments.remove(shipId);
		arrivedStates.remove(shipId);
		lastDirections.remove(shipId);
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Vector3f>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Vector3f> entry = it.next();
			try {
				if(!updateMove(entry.getKey(), entry.getValue())) {
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("MoveManager tick error", e);
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the entity no longer exists.
	 * Ships stay in the manager even after arriving to maintain position if knocked away.
	 */
	private boolean updateMove(int shipId, Vector3f destination) {
		SimpleGameObject obj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		if(!(obj instanceof ManagedUsableSegmentController)) return false;

		ManagedUsableSegmentController<?> ship = (ManagedUsableSegmentController<?>) obj;
		if(ship.getWorldTransform() == null) return false;

		Vector3f shipPos = ship.getWorldTransform().origin;
		float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, destination.x, destination.y, destination.z);

		// Keep AI active
		//noinspection unchecked
		((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		// Clear any attack target so the ship doesn't get distracted
		try {
			((TargetProgram<?>) ship.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
		} catch(Exception ignored) {
		}

		boolean isArrived = arrivedStates.getOrDefault(shipId, false);

		if(isArrived) {
			// Ship has arrived; check if it drifted too far away
			if(dist > HOLDING_REACQUIRE_DISTANCE) {
				// Drifted away (e.g., rammed), reacquire movement
				arrivedStates.put(shipId, false);
			} else {
				// Hold position with gentle correction
				applyMovement(ship, destination, 0.05f); // Very low speed for position holding
				return true;
			}
		}

		// Calculate progressive braking as ship approaches
		float speedScale = 1.0f;
		if(dist < HARD_BRAKE_DISTANCE) {
			speedScale = HARD_BRAKE_SCALE; // Hard brake near target
		} else if(dist < MEDIUM_BRAKE_DISTANCE) {
			// Linear interpolation: 50% speed at MEDIUM_BRAKE_DISTANCE, 10% at HARD_BRAKE_DISTANCE
			float range = MEDIUM_BRAKE_DISTANCE - HARD_BRAKE_DISTANCE;
			float progress = (dist - HARD_BRAKE_DISTANCE) / range;
			speedScale = HARD_BRAKE_SCALE + progress * (1.0f - HARD_BRAKE_SCALE);
		}

		// Check if we've arrived (within threshold)
		if(dist <= ARRIVAL_DISTANCE) {
			arrivedStates.put(shipId, true);
			// One final gentle correction to get to exact position
			applyMovement(ship, destination, 0.05f);
			return true;
		}

		applyMovement(ship, destination, speedScale);
		return true;
	}

	/**
	 * Apply movement toward destination with the given speed scale.
	 * Only sends moveTo if direction changed significantly to avoid wiggling.
	 */
	private void applyMovement(ManagedUsableSegmentController<?> ship, Vector3f destination, float speedScale) {
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(destination);
		}

		Vector3f shipPos = ship.getWorldTransform().origin;
		tmpMoveDir.sub(destination, shipPos);
		tmpMoveDir.scale(speedScale); // Apply speed scaling for progressive braking

		// Check if direction changed significantly before sending moveTo
		Vector3f lastDir = lastDirections.get(ship.getId());
		if(lastDir != null) {
			float angleDiff = Math.abs(tmpMoveDir.angle(lastDir));
			// Only update if angle difference is > threshold (in radians, ~5.7 degrees)
			if(angleDiff < DIRECTION_CHANGE_THRESHOLD) {
				return; // Direction hasn't changed enough, skip moveTo
			}
		}

		// Direction changed significantly or first time - update the command
		lastDirections.put(ship.getId(), new Vector3f(tmpMoveDir));
		ShipAIEntity aiEntity = (ShipAIEntity) ship.getAiConfiguration().getAiEntityState();
		aiEntity.moveTo(GameServer.getServerState().getController().getTimer(), tmpMoveDir, true);
	}
}
