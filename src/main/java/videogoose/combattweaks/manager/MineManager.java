package videogoose.combattweaks.manager;

import api.common.GameCommon;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.data.SimpleGameObject;
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
 * Tracks mining assignments (ship -&gt; asteroid).
 *
 * <p>StarMade's fleet-mining FSM ({@code FleetFormationingMining}) only works for a multi-ship
 * fleet with a separate flagship and formation waypoints from a real fleet mining order — it
 * immediately RESTARTs for a single commanded ship. So we drive mining ourselves: the approach
 * and station-keeping are delegated to {@link MoveManager} (smooth thrust + collision avoidance),
 * and the actual salvaging is performed by {@code MiningSalvageListener} on the server AI tick,
 * which is the only thread-safe place to fire beams.</p>
 */
public class MineManager {

	/** Clearance added on top of the asteroid's bounding sphere radius for the hold position. */
	private static final float PADDING = 200.0f;
	private static final int TICK_INTERVAL_SECONDS = 2;
	private static MineManager instance;
	/** Maps ship entity ID → asteroid entity ID. */
	private final ConcurrentHashMap<Integer, Integer> assignments = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;

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
	 * Compute a hold position just outside the asteroid's bounding sphere (plus {@link #PADDING}),
	 * in the direction the ship currently is relative to the asteroid, so it approaches from its
	 * existing side rather than flying through the rock.
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

	/** Register a mining order. Delegates the approach + station-keeping to {@link MoveManager}. */
	public void addMine(int shipId, int asteroidId) {
		SimpleGameObject shipObj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		SimpleGameObject asteroidObj = (SimpleGameObject) GameCommon.getGameObject(asteroidId);
		if(!(shipObj instanceof Ship) || !(asteroidObj instanceof FloatingRock)) {
			return;
		}
		Ship ship = (Ship) shipObj;
		if(ship.getWorldTransform() == null || asteroidObj.getWorldTransform() == null) {
			return;
		}
		assignments.put(shipId, asteroidId);
		MoveManager.getInstance().addMove(shipId, computeMiningPosition(ship, (SegmentController) asteroidObj));
	}

	/** Returns the asteroid id assigned to the given ship, or null if none. */
	public Integer getAssignedTarget(int shipId) {
		return assignments.get(shipId);
	}

	/** Cancel any active mining order for the given ship and stop its approach. */
	public void removeMine(int shipId) {
		if(assignments.remove(shipId) != null) {
			MoveManager.getInstance().removeMove(shipId);
		}
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!validate(entry.getKey(), entry.getValue())) {
					MoveManager.getInstance().removeMove(entry.getKey());
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("MineManager tick error", e);
				MoveManager.getInstance().removeMove(entry.getKey());
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the assignment should be dropped: the ship or asteroid no longer exists,
	 * the ship has left its fleet, or the asteroid has been fully mined out.
	 */
	private boolean validate(int shipId, int asteroidId) {
		SimpleGameObject shipObj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		if(!(shipObj instanceof Ship)) {
			return false;
		}
		Ship ship = (Ship) shipObj;
		// Only fleeted ships obey our orders; drop the assignment if the ship left its fleet.
		if(!ship.isInFleet()) {
			return false;
		}
		// Keep AI active so the ship can move and use its salvage beams.
		//noinspection unchecked
		((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		SimpleGameObject asteroidObj = (SimpleGameObject) GameCommon.getGameObject(asteroidId);
		if(!(asteroidObj instanceof FloatingRock)) {
			return false; // mined out or unloaded
		}
		return ((FloatingRock) asteroidObj).getTotalElements() > 0;
	}
}
