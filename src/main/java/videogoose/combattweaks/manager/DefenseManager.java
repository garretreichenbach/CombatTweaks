package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.ai.AIConfiguationElements;
import org.schema.game.common.controller.ai.Types;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
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

	/** Cancel the defense order for the given defender. */
	public void removeDefense(int defenderId) {
		assignments.remove(defenderId);
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

		SegmentController threat = findNearestThreat(protectedEntity, defender.getFactionId());
		if(threat != null) {
			// Attack the threat using the existing, proven mechanism
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
	 * When no threats are present, move the defender to stay within {@link #ESCORT_DISTANCE}
	 * of the protected entity. We clear the AI's attack target and set a position target
	 * toward the protected entity so the ship follows without engaging it.
	 */
	private void positionNearProtected(ManagedUsableSegmentController<?> defender, SegmentController protectedEntity) {
		Vector3f defPos = defender.getWorldTransform().origin;
		Vector3f protPos = protectedEntity.getWorldTransform().origin;
		float dist = Vector3fTools.distance(defPos.x, defPos.y, defPos.z, protPos.x, protPos.y, protPos.z);
		if(dist <= ESCORT_DISTANCE) return;

		// Keep AI active for movement
		((AIConfiguationElements<Boolean>) defender.getAiConfiguration().get(Types.ACTIVE)).setCurrentState(true, true);

		// Clear attack target to prevent friendly fire
		try {
			((TargetProgram<?>) defender.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(null);
		} catch(Exception exception) {
			exception.printStackTrace();
		}

		// Point the ship toward the protected entity via the network target position
		if(defender.getNetworkObject() instanceof NetworkShip) {
			protectedEntity.getPhysicsObject().getLinearVelocity(tmpVec);
			((NetworkShip) defender.getNetworkObject()).targetVelocity.set(tmpVec);
			((NetworkShip) defender.getNetworkObject()).targetPosition.set(protPos);
			ShipAIEntity aiEntity = (ShipAIEntity) defender.getAiConfiguration().getAiEntityState();
			aiEntity.moveTo(GameServer.getServerState().getController().getTimer(), protPos, true);
		}
	}
}
