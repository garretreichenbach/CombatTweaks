package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
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
 * Manages active defense orders server-side.
 * Every {@link #TICK_INTERVAL_SECONDS} seconds each registered defending ship either
 * engages the nearest threat near its protect target, or navigates back toward it
 * if the area is clear.
 */
public class DefenseManager {

	/** Radius (world units) within which enemies are considered threats to the protected entity. */
	private static final float THREAT_RANGE = 2000.0f;
	/** Distance within which a defender is considered "close enough" and won't reposition. */
	private static final float ESCORT_DISTANCE = 500.0f;
	private static final int TICK_INTERVAL_SECONDS = 5;

	private static DefenseManager instance;

	/** Maps defender entity ID → protected entity ID. */
	private final ConcurrentHashMap<Integer, Integer> assignments = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;
	// Reused per-tick to avoid allocation
	private final Vector3f tmpVec = new Vector3f();
	private final Vector3i tmpSector = new Vector3i();

	private DefenseManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-DefenseManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_SECONDS, TICK_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}

	public static DefenseManager getInstance() {
		if(instance == null) {
			synchronized(DefenseManager.class) {
				if(instance == null) {
					instance = new DefenseManager();
				}
			}
		}
		return instance;
	}

	/** Register a defender → protected-entity pair. Replaces any existing order for that defender. */
	public void addDefense(int defenderId, int protectedId) {
		assignments.put(defenderId, protectedId);
	}

	/** Whether the given ship currently has a defense order. */
	public boolean isDefending(int defenderId) {
		return assignments.containsKey(defenderId);
	}

	/** Cancel the defense order for the given defender. */
	public void removeDefense(int defenderId) {
		if(assignments.remove(defenderId) != null) {
			MoveManager.getInstance().removeMove(defenderId);
		}
	}

	// -------------------------------------------------------------------------

	private void tick() {
		Iterator<Map.Entry<Integer, Integer>> it = assignments.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<Integer, Integer> entry = it.next();
			try {
				if(!updateDefense(entry.getKey(), entry.getValue())) {
					it.remove();
				}
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("DefenseManager tick error", e);
			}
		}
	}

	/**
	 * Returns false if either entity has ceased to exist (assignment should be dropped).
	 */
	private boolean updateDefense(int defenderId, int protectedId) {
		SimpleGameObject defenderObj = (SimpleGameObject) GameCommon.getGameObject(defenderId);
		SimpleGameObject protectedObj = (SimpleGameObject) GameCommon.getGameObject(protectedId);

		if(!(defenderObj instanceof ManagedUsableSegmentController)) return false;
		if(!(protectedObj instanceof SegmentController)) return false;

		ManagedUsableSegmentController<?> defender = (ManagedUsableSegmentController<?>) defenderObj;
		SegmentController protectedEntity = (SegmentController) protectedObj;

		// Only fleeted ships obey our orders; drop the assignment if the defender left its fleet.
		if(!defender.isInFleet()) return false;

		SegmentController threat = findNearestThreat(protectedEntity, defender.getFactionId());
		if(threat != null) {
			// Engage: hand off to the engine's combat FSM. Stop any escort movement so the two
			// controllers don't fight over the ship.
			MoveManager.getInstance().removeMove(defender.getId());
			AIUtils.setAttackTarget(defender, threat);
		} else {
			positionNearProtected(defender, protectedEntity);
		}
		return true;
	}

	/**
	 * Scans entities in the protected entity's sector for the nearest enemy
	 * within {@link #THREAT_RANGE}. Returns null if no threats found.
	 */
	private SegmentController findNearestThreat(SegmentController protectedEntity, int defenderFactionId) {
		Vector3f protectedPos = protectedEntity.getWorldTransform().origin;
		SegmentController nearest = null;
		float nearestDistSq = THREAT_RANGE * THREAT_RANGE;

		try {
			for(SimpleTransformableSendableObject<?> obj : GameServer.getServerState().getUniverse().getSector(protectedEntity.getSector(tmpSector)).getEntities()) {
				if(!(obj instanceof SegmentController)) continue;
				SegmentController candidate = (SegmentController) obj;
				if(candidate.isDocked()) continue;
				if(candidate.getId() == protectedEntity.getId()) continue;

				FactionRelation.RType relation = GameCommon.getGameState().getFactionManager().getRelation(candidate.getFactionId(), defenderFactionId);
				if(relation != FactionRelation.RType.ENEMY) continue;

				tmpVec.set(candidate.getWorldTransform().origin);
				float distSq = Vector3fTools.distanceSquared(protectedPos, tmpVec);
				if(distSq < nearestDistSq) {
					nearestDistSq = distSq;
					nearest = candidate;
				}
			}
		} catch(Exception e) {
			// Sector may not be loaded yet; skip this tick
		}
		return nearest;
	}

	/**
	 * When no threats are present, keep the defender within {@link #ESCORT_DISTANCE} of the protected
	 * entity. Movement is delegated to {@link MoveManager} (which thrusts at ~20 Hz with collision
	 * avoidance and holds position on arrival) — driving moveTo() from this 5s tick barely moved the
	 * ship at all.
	 */
	private void positionNearProtected(ManagedUsableSegmentController<?> defender, SegmentController protectedEntity) {
		// Not engaging anything while escorting.
		AIUtils.clearTarget(defender.getId());

		Vector3f defPos = defender.getWorldTransform().origin;
		Vector3f protPos = protectedEntity.getWorldTransform().origin;
		float dist = Vector3fTools.distance(defPos.x, defPos.y, defPos.z, protPos.x, protPos.y, protPos.z);
		if(dist <= ESCORT_DISTANCE) {
			// Close enough — stop escorting and hold.
			MoveManager.getInstance().removeMove(defender.getId());
		} else {
			MoveManager.getInstance().addMove(defender.getId(), MoveManager.computeDestination(defender, protectedEntity));
		}
	}
}
