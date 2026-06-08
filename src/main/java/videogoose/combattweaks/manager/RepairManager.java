package videogoose.combattweaks.manager;

import api.common.GameCommon;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.SimpleGameObject;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.utils.AIUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks repair assignments (ship -> target).
 *
 * <p>Repair is driven entirely by the engine's fleet repair FSM (FLEET_REPAIR -> FleetRepairing),
 * which — unlike the fleet mining FSM — works for a single ship (no flagship/formation dependency)
 * and handles approach, orbit and firing the repair beams itself. So this manager only registers the
 * assignment and (re)issues the FSM transition; it does NOT hand-drive movement, which previously
 * fought the FSM and made ships gyrate.</p>
 */
public class RepairManager {

	private static final int TICK_INTERVAL_SECONDS = 2;
	private static RepairManager instance;
	/** Maps ship entity ID → target entity ID. */
	private final ConcurrentHashMap<Integer, Integer> assignments = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;

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

	/** Register a repair order for the given ship targeting the given object. Replaces any existing order. */
	public void addRepair(int shipId, int targetId) {
		assignments.put(shipId, targetId);
		// Engage the repair FSM right away instead of waiting up to a full tick (which left the ship idle and
		// looking like the order did nothing). The tick still re-affirms it and drops it when appropriate.
		AIUtils.setRepairTarget(shipId, targetId);
	}

	/** Returns the repair target id assigned to the given ship, or null if none. */
	public Integer getAssignedTarget(int shipId) {
		return assignments.get(shipId);
	}

	/** Cancel any active repair order for the given ship and send it back to idle. */
	public void removeRepair(int shipId) {
		if(assignments.remove(shipId) != null) {
			AIUtils.clearTarget(shipId); // drop the FSM target so the ship leaves the repair state
		}
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!updateRepair(entry.getKey(), entry.getValue())) {
					AIUtils.clearTarget(entry.getKey());
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("RepairManager tick error", e);
				AIUtils.clearTarget(entry.getKey());
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the assignment should be dropped: the ship or target no longer exists, the
	 * ship has left its fleet, or the target is fully repaired / destroyed.
	 */
	private boolean updateRepair(int shipId, int targetId) {
		SimpleGameObject shipObj = (SimpleGameObject) GameCommon.getGameObject(shipId);
		if(!(shipObj instanceof Ship)) {
			return false;
		}
		Ship ship = (Ship) shipObj;
		if(ship.getWorldTransform() == null) {
			return false;
		}
		// Only fleeted ships obey our orders; drop only after a confirmed (non-transient) fleet loss, so a
		// fleet edit's brief uncache/re-cache doesn't silently kill the repair order.
		if(AIUtils.confirmedLeftFleet(ship)) {
			return false;
		}

		SimpleGameObject targetObj = (SimpleGameObject) GameCommon.getGameObject(targetId);
		if(!(targetObj instanceof SegmentController)) {
			return false;
		}
		SegmentController target = (SegmentController) targetObj;
		if(target.getWorldTransform() == null) {
			return false;
		}

		// Stop when there's nothing left to mend — the ship then returns to idle. Drop the order when the
		// target is destroyed/overheating, OR fully repaired: full reactor HP AND no destroyed blocks
		// (block-kill recorder) AND no damaged blocks (damaged-block recorder). Until then it holds, like
		// Defend.
		if(target instanceof Ship) {
			try {
				Ship t = (Ship) target;
				if(t.getReactorHp() <= 0) {
					return false; // destroyed / overheating
				}
				if(t.getReactorHp() >= t.getReactorHpMax()
						&& t.getBlockKillRecorder().size() == 0
						&& t.getDamagedBlockRecorder().size() == 0) {
					return false; // fully repaired — revert to idle
				}
			} catch(Exception ignored) {
				// Target doesn't expose its HP/recorders — let the FSM decide when to stop.
			}
		}

		// Hand off to the engine's repair FSM (idempotent — skips if already repairing this target).
		AIUtils.setRepairTarget(ship, target);
		return true;
	}
}
