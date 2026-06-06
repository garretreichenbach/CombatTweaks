package videogoose.combattweaks.listener;

import api.common.GameCommon;
import api.listener.fastevents.CustomAddOnUseListener;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.SegmentBufferIteratorInterface;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SegmentData;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.manager.MineManager;
import videogoose.combattweaks.utils.AIUtils;

import javax.vecmath.Vector3f;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Solo-mining controller, run on the server AI tick for every non-docked AI ship.
 *
 * <p>Vanilla fleet mining excludes the flagship and needs a multi-ship formation, so a lone ship
 * can't reach it normally. We do two things here: (1) navigate the ship up to its asteroid (the
 * engine's mining states don't fly there), and (2) once in salvage range hand off to the engine's
 * own {@code FleetMining} state via {@link AIUtils#enterMiningState} — that state has no flagship
 * dependency and does the real block-finding, aiming and salvage firing exactly like NPC miners.
 * While the engine is mining we stay out of its way; if it drops back to idle (out of range, cargo
 * full, rock depleted) we resume navigating / re-enter.</p>
 */
public class MiningSalvageListener implements CustomAddOnUseListener {

	/** Within this distance of the enter range, throttle approach speed so the ship coasts to a stop. */
	private static final float APPROACH_SLOW_ZONE = 600.0f;
	/** Max approach speed once inside the slow zone, so the ship doesn't overshoot. */
	private static final float MAX_APPROACH_SPEED = 60.0f;
	/** How often to re-find the nearest block (ms) — used only as the approach target. */
	private static final long BLOCK_REFRESH_MS = 300;
	/** Clamp for the distance at which we hand off to the engine mining state. */
	private static final float MIN_ENTER_DIST = 60.0f;
	private static final float MAX_ENTER_DIST = 300.0f;

	/** Per-ship throttle for debug logging (entity id → last log time ms). */
	private final Map<Integer, Long> lastDebugLog = new ConcurrentHashMap<>();
	/** Per-ship cached world position of the nearest asteroid block (approach target). */
	private final Map<Integer, Vector3f> blockAim = new ConcurrentHashMap<>();
	private final Map<Integer, Long> blockAimTime = new ConcurrentHashMap<>();
	/** Reused block finder (the AI tick is single-threaded, so one instance is fine). */
	private final NearestBlockFinder blockFinder = new NearestBlockFinder();

	@Override
	public void use(Ship entity, ShipManagerContainer managerContainer, Timer timer) {
		try {
			Integer asteroidId = MineManager.getInstance().getAssignedTarget(entity.getId());
			if(asteroidId == null) {
				return;
			}

			Object asteroidObj = GameCommon.getGameObject(asteroidId);
			if(!(asteroidObj instanceof FloatingRock)) {
				return;
			}
			FloatingRock asteroid = (FloatingRock) asteroidObj;
			if(asteroid.getWorldTransform() == null || asteroid.getTotalElements() <= 0) {
				return;
			}

			// Must have salvage beams to mine with.
			if(managerContainer.getSalvage().getElementManager().totalSize <= 0) {
				return;
			}

			ShipAIEntity aiEntity = (ShipAIEntity) entity.getAiConfiguration().getAiEntityState();
			// Disable collision avoidance toward the asteroid so the ship can close in (and during
			// mining); only the AIDocking state acts on dockingTarget, which we never enter.
			aiEntity.setDockingTarget(asteroid);

			// Already mining via the engine's FleetMining state? It handles holding, block-finding and
			// firing — leave it alone.
			if(AIUtils.isMiningState(entity)) {
				debug(entity, "mining (engine FleetMining state)");
				return;
			}

			Vector3f shipPos = entity.getWorldTransform().origin;
			Vector3f target = getTargetBlock(entity.getId(), asteroid, shipPos);
			if(target == null) {
				return; // no block found this tick
			}
			float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, target.x, target.y, target.z);

			float salvageRange = aiEntity.getSalvageRange();
			if(salvageRange <= 0) {
				salvageRange = 200.0f; // not computed yet — use a sane default for the approach
			}
			// Hand off to the engine mining state once we're comfortably inside salvage range of a block.
			float enterDist = Math.min(Math.max(salvageRange * 0.7f, MIN_ENTER_DIST), MAX_ENTER_DIST);

			if(dist > enterDist) {
				// Approach the nearest block. Cap speed near the enter range so the ship coasts in
				// instead of overshooting and bouncing.
				float speed = entity.getLinearVelocity(new Vector3f()).length();
				if(dist < enterDist + APPROACH_SLOW_ZONE && speed > MAX_APPROACH_SPEED) {
					aiEntity.stop();
				} else {
					Vector3f toBlock = new Vector3f();
					toBlock.sub(target, shipPos);
					aiEntity.moveTo(timer, toBlock, true);
				}
				return;
			}

			// In range. canSalvage() is only true when the salvage beams are linked to storage with
			// free space; without it the engine state would just restart, so wait here instead.
			if(!aiEntity.canSalvage()) {
				aiEntity.stop();
				debug(entity, "in range but canSalvage()==false (link salvage beams to a cargo/storage with free space)");
				return;
			}

			// Hand off to the engine's FleetMining state — it now does the actual salvaging.
			AIUtils.enterMiningState(entity, asteroid);
			debug(entity, "entering FleetMining on " + asteroid.getName());
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error in mining controller", e);
		}
	}

	/**
	 * Returns the cached world position of the nearest asteroid block to the ship (the approach
	 * target), refreshing it every {@link #BLOCK_REFRESH_MS}. Returns null if none could be found.
	 */
	private Vector3f getTargetBlock(int shipId, FloatingRock asteroid, Vector3f shipPos) {
		long now = System.currentTimeMillis();
		Vector3f cached = blockAim.get(shipId);
		Long last = blockAimTime.get(shipId);
		if(cached != null && last != null && now - last < BLOCK_REFRESH_MS) {
			return cached;
		}
		Vector3f found = new Vector3f();
		if(findNearestBlock(asteroid, shipPos, found)) {
			blockAim.put(shipId, found);
			blockAimTime.put(shipId, now);
			return found;
		}
		return cached;
	}

	/** Finds the world position of the asteroid block nearest the ship. Returns false if none found. */
	private boolean findNearestBlock(FloatingRock asteroid, Vector3f shipPos, Vector3f out) {
		try {
			blockFinder.reset(shipPos, asteroid.getWorldTransform());
			asteroid.getSegmentBuffer().iterateOverNonEmptyElement(blockFinder, true);
			if(blockFinder.found) {
				out.set(blockFinder.best);
				return true;
			}
		} catch(Exception ignored) {
		}
		return false;
	}

	/** Throttled debug log (once per ~3s per ship) gated on the mod's debug_mode config. */
	private void debug(Ship entity, String message) {
		try {
			if(!ConfigManager.getMainConfig().debugMode.value) {
				return;
			}
			long now = System.currentTimeMillis();
			Long last = lastDebugLog.get(entity.getId());
			if(last != null && now - last < 3000) {
				return;
			}
			lastDebugLog.put(entity.getId(), now);
			CombatTweaks.getInstance().logInfo("[Mining] " + entity.getName() + ": " + message);
		} catch(Exception ignored) {
		}
	}

	/**
	 * Iterates an asteroid's non-empty segments, taking the first valid block of each, and keeps the
	 * one whose world position is nearest the ship.
	 */
	private static final class NearestBlockFinder implements SegmentBufferIteratorInterface {
		private final SegmentPiece piece = new SegmentPiece();
		private final Vector3b helper = new Vector3b();
		private final Vector3f cand = new Vector3f();
		final Vector3f best = new Vector3f();
		private Vector3f shipPos;
		private Transform worldTransform;
		boolean found;
		private float bestDistSq;

		void reset(Vector3f shipPos, Transform worldTransform) {
			this.shipPos = shipPos;
			this.worldTransform = worldTransform;
			this.found = false;
			this.bestDistSq = Float.MAX_VALUE;
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
					piece.getAbsolutePos(cand);
					cand.x -= SegmentData.SEG_HALF;
					cand.y -= SegmentData.SEG_HALF;
					cand.z -= SegmentData.SEG_HALF;
					worldTransform.transform(cand);
					float dx = cand.x - shipPos.x;
					float dy = cand.y - shipPos.y;
					float dz = cand.z - shipPos.z;
					float distSq = dx * dx + dy * dy + dz * dz;
					if(distSq < bestDistSq) {
						bestDistSq = distSq;
						best.set(cand);
						found = true;
					}
					break; // first block per segment is enough to rank segments by nearness
				}
			}
			return false;
		}
	}
}
