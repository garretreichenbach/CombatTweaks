package videogoose.combattweaks.manager;

import api.common.GameCommon;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.data.SimpleGameObject;
import videogoose.combattweaks.CombatTweaks;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks mining assignments (ship -> asteroid).
 *
 * <p>Vanilla has no lone-ship fleet-mining path (fleet mining excludes the flagship and needs a
 * multi-ship formation), so a single ship is driven entirely by {@code MiningSalvageListener} on the
 * server AI tick — it approaches, holds station and fires the salvage beams there, where moveTo/stop
 * drive physics directly and the idle state can't fight them. This manager just holds the assignments,
 * keeps the AI active so that per-tick controller runs, and drops assignments that are no longer valid.</p>
 */
public class MineManager {

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

	/** Register a mining order (ship → asteroid). The per-tick controller does the rest. */
	public void addMine(int shipId, int asteroidId) {
		assignments.put(shipId, asteroidId);
	}

	/** Returns the asteroid id assigned to the given ship, or null if none. */
	public Integer getAssignedTarget(int shipId) {
		return assignments.get(shipId);
	}

	/** Cancel any active mining order for the given ship. */
	public void removeMine(int shipId) {
		assignments.remove(shipId);
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!validate(entry.getKey(), entry.getValue())) {
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("MineManager tick error", e);
				it.remove();
			}
		}
	}

	/**
	 * Returns false when the assignment should be dropped: the ship or asteroid no longer exists,
	 * the ship has left its fleet, or the asteroid has been fully mined out. Also keeps the ship's
	 * AI active so its per-tick mining controller keeps running.
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
		// Keep AI active so the ship updates (and our salvage controller runs) each server tick.
		//noinspection unchecked
		((AIConfiguationElements<Boolean>) ship.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		SimpleGameObject asteroidObj = (SimpleGameObject) GameCommon.getGameObject(asteroidId);
		if(!(asteroidObj instanceof FloatingRock)) {
			return false; // mined out or unloaded
		}
		return ((FloatingRock) asteroidObj).getTotalElements() > 0;
	}
}
