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
 * it is within {@link #ARRIVAL_DISTANCE} world units, then transitions
 * to hold-position mode (calling stop() to counteract drift).
 *
 * DESIGN NOTES — why the tick interval matters:
 * ShipAIEntity.moveTo() accumulates delta time via a timeTracker field and
 * runs one physics iteration per ~30ms of accumulated time.  When called from
 * a background thread, timer.getDelta() reflects the game's last tick (~30ms),
 * so each moveTo() call applies exactly one physics step.  The manager must
 * therefore call moveTo() at a rate close to the physics tick rate to maintain
 * continuous thrust.  Calling it once every 5 s left ships with essentially
 * zero thrust — one step every 5 seconds.
 */
public class MoveManager {

	/** Extra clearance added on top of the target's bounding sphere radius. */
	public static final float PADDING = 150.0f;
	/** Distance at which the ship is considered to have arrived. */
	private static final float ARRIVAL_DISTANCE = 200.0f;
	/** Distance at which an arrived ship is considered to have drifted and must reacquire. */
	private static final float HOLDING_REACQUIRE_DISTANCE = 400.0f;
	/**
	 * How often to tick movement updates, in milliseconds.
	 * Must be kept close to the physics tick interval (~30 ms) for smooth
	 * continuous thrust.  50 ms gives ~20 Hz — a reasonable trade-off between
	 * CPU overhead and movement responsiveness.
	 */
	private static final int TICK_INTERVAL_MS = 50;
	private static MoveManager instance;
	/** Maps ship entity ID → destination world position. */
	private final ConcurrentHashMap<Integer, Vector3f> assignments = new ConcurrentHashMap<>();
	/** Tracks which ships have arrived and are holding position. */
	private final ConcurrentHashMap<Integer, Boolean> arrivedStates = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	private final Vector3f tmpMoveDir = new Vector3f();
	/**
	 * Maximum iterations for the conflict-resolution loop.
	 * Each pass can resolve one overlapping entity; 8 is sufficient for
	 * densely packed areas without risking an infinite loop.
	 */
	private static final int MAX_RESOLVE_ITERS = 8;

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

	private MoveManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-MoveManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Compute a safe destination near {@code target} for {@code ship}.
	 *
	 * Step 1 — Initial candidate: place the point just outside the target's
	 * bounding sphere (radius + {@link #PADDING}) in the direction the ship is
	 * currently located relative to the target, so the ship approaches from its
	 * existing side rather than flying through the target.
	 *
	 * Step 2 — Conflict resolution: iterate over every other loaded entity in
	 * the same sector and push the candidate away from any whose exclusion
	 * sphere (bounding radius + {@link #PADDING}) it overlaps.  Repeats until
	 * the point is clear or {@link #MAX_RESOLVE_ITERS} is exhausted, whichever
	 * comes first.
	 */
	public static Vector3f computeDestination(SegmentController ship, SegmentController target) {
		Vector3f targetPos = target.getWorldTransform().origin;
		Vector3f shipPos = ship.getWorldTransform().origin;

		// Direction from target toward ship
		Vector3f dir = new Vector3f();
		dir.sub(shipPos, targetPos);
		float len = dir.length();
		if(len < 0.01f) {
			dir.set(0, 0, 1); // fallback: coincident positions
		} else {
			dir.scale(1.0f / len);
		}

		float offset = getBoundingSphereRadius(target) + PADDING;
		Vector3f dest = new Vector3f(dir);
		dest.scale(offset);
		dest.add(targetPos);

		resolveNearbyConflicts(dest, ship, target);
		return dest;
	}

	/** Bounding sphere radius as the half-diagonal of the axis-aligned bounding box. */
	private static float getBoundingSphereRadius(SegmentController entity) {
		BoundingBox bb = entity.getBoundingBox();
		if(bb == null) return 0.0f;
		float dx = (bb.max.x - bb.min.x) * 0.5f;
		float dy = (bb.max.y - bb.min.y) * 0.5f;
		float dz = (bb.max.z - bb.min.z) * 0.5f;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * Iteratively pushes {@code dest} away from any entity (other than
	 * {@code ship} and {@code target}) in the same sector whose exclusion
	 * sphere ({@code boundingRadius + PADDING}) the point currently overlaps.
	 * Operates in-place on {@code dest}.
	 */
	private static void resolveNearbyConflicts(Vector3f dest, SegmentController ship, SegmentController target) {
		int targetSector = target.getSectorId();
		for(int iter = 0; iter < MAX_RESOLVE_ITERS; iter++) {
			boolean anyConflict = false;
			for(SegmentController other : GameServer.getServerState().getSegmentControllersByName().values()) {
				if(other == ship || other == target) continue;
				if(other.getWorldTransform() == null) continue;
				if(other.getSectorId() != targetSector) continue;

				float otherRadius = getBoundingSphereRadius(other);
				if(otherRadius < 1.0f) continue; // ignore negligibly small entities

				Vector3f otherPos = other.getWorldTransform().origin;
				float dx = dest.x - otherPos.x;
				float dy = dest.y - otherPos.y;
				float dz = dest.z - otherPos.z;
				float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
				float minDist = otherRadius + PADDING;

				if(dist < minDist) {
					// Destination overlaps this entity's exclusion sphere — push away
					float push = minDist - dist + 5.0f; // +5 unit margin to avoid re-triggering
					if(dist < 0.01f) {
						dest.x += push; // coincident fallback: push along +X
					} else {
						float inv = push / dist;
						dest.x += dx * inv;
						dest.y += dy * inv;
						dest.z += dz * inv;
					}
					anyConflict = true;
				}
			}
			if(!anyConflict) break; // converged — no conflicts remain
		}
	}

	/** Register a move order for the given ship to the given destination. Replaces any existing order. */
	public void addMove(int shipId, Vector3f destination) {
		assignments.put(shipId, new Vector3f(destination));
	}

	/**
	 * Returns the move destination currently assigned to the ship, or null if none.
	 * A copy is returned so callers cannot mutate manager state.
	 */
	public Vector3f getAssignedDestination(int shipId) {
		Vector3f destination = assignments.get(shipId);
		return destination != null ? new Vector3f(destination) : null;
	}

	/** Cancel any active move order for the given ship. */
	public void removeMove(int shipId) {
		assignments.remove(shipId);
		arrivedStates.remove(shipId);
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
	 * Ships remain in the manager after arriving to maintain position if knocked away.
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

		ShipAIEntity aiEntity = (ShipAIEntity) ship.getAiConfiguration().getAiEntityState();
		boolean isArrived = arrivedStates.getOrDefault(shipId, false);

		if(isArrived) {
			if(dist > HOLDING_REACQUIRE_DISTANCE) {
				// Drifted too far — reacquire movement
				arrivedStates.put(shipId, false);
			} else {
				// Hold position: call stop() each tick to bleed off any residual velocity
				aiEntity.stop();
				return true;
			}
		}

		if(dist <= ARRIVAL_DISTANCE) {
			arrivedStates.put(shipId, true);
			aiEntity.stop();
			return true;
		}

		applyMovement(ship, aiEntity, destination);
		return true;
	}

	/**
	 * Apply continuous movement toward destination.
	 *
	 * We pass the raw ship→destination vector directly to moveTo() without any
	 * magnitude scaling.  moveTo() normalises the direction internally and uses
	 * the vector's length as the apparent distance to target.  Scaling the vector
	 * does NOT slow the ship — moveTo()'s own braking only activates at dist < 2
	 * world units, which is far below real distances — so any pre-scaling was a
	 * no-op that only obscured the intent of the code.
	 *
	 * Direction-caching (the old DIRECTION_CHANGE_THRESHOLD guard) has also been
	 * removed.  moveTo() must be called every tick for continuous thrust; skipping
	 * calls based on direction was the primary reason ships moved so slowly.
	 */
	private void applyMovement(ManagedUsableSegmentController<?> ship, ShipAIEntity aiEntity, Vector3f destination) {
		if(ship.getNetworkObject() instanceof NetworkShip) {
			((NetworkShip) ship.getNetworkObject()).targetVelocity.set(0, 0, 0);
			((NetworkShip) ship.getNetworkObject()).targetPosition.set(destination);
		}

		Vector3f shipPos = ship.getWorldTransform().origin;
		tmpMoveDir.sub(destination, shipPos);
		aiEntity.moveTo(GameServer.getServerState().getController().getTimer(), tmpMoveDir, true);
	}
}
