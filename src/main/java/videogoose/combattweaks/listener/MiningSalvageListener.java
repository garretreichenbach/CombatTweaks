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
	/**
	 * Stand-off the ship keeps between its hull (bounding sphere) and the nearest surface block while
	 * mining — small, since salvage beams are short range, but enough to never touch the rock. As blocks
	 * are mined the nearest block recedes and the ship follows inward at this stand-off, so it tunnels
	 * cleanly instead of stalling or ramming.
	 */
	private static final float STANDOFF = 35.0f;
	/** Dead-band around the stand-off so the ship doesn't constantly nudge in and out (jitter). */
	private static final float STANDOFF_SLACK = 15.0f;
	/** Bleed velocity below this before handing off, so the ship enters the mining state nearly still. */
	private static final float ENTER_SPEED = 6.0f;

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
			if(!(asteroidObj instanceof FloatingRock asteroid)) {
				return;
			}
			if(asteroid.getWorldTransform() == null || asteroid.getTotalElements() <= 0) {
				return;
			}

			// Must have salvage beams to mine with.
			if(managerContainer.getSalvage().getElementManager().totalSize <= 0) {
				return;
			}

			ShipAIEntity aiEntity = entity.getAiConfiguration().getAiEntityState();
			// Disable collision avoidance toward the asteroid so the ship can close in (and during
			// mining); only the AIDocking state acts on dockingTarget, which we never enter.
			aiEntity.setDockingTarget(asteroid);

			Vector3f shipPos = entity.getWorldTransform().origin;
			Vector3f target = getTargetBlock(entity.getId(), asteroid, shipPos);
			if(target == null) {
				return; // no block found this tick
			}
			float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, target.x, target.y, target.z);
			// Stand-off measured from the hull (bounding sphere), so it's consistent across ship sizes.
			float standoff = entity.getBoundingSphere().radius + STANDOFF;
			float speed = entity.getLinearVelocity(new Vector3f()).length();

			// While FleetMining is active it owns the aiming (it orients the ship at the exact block it's
			// salvaging). Our movement must NOT also orient the ship or the two fight and the beam points
			// off-target — so only orient toward travel while still approaching.
			boolean mining = AIUtils.isMiningState(entity);

			// Position control — keep the ship `standoff` from the nearest solid block, in BOTH phases.
			// FleetMining only aims+fires (never thrusts), so we own movement throughout: as blocks are
			// mined the nearest block recedes and the ship follows it inward, staying off the surface, so
			// it tunnels cleanly without ramming and without stalling once the surface layer is gone.
			if(dist > standoff + STANDOFF_SLACK) {
				// Approach. Cap speed near the stand-off so it coasts in instead of overshooting.
				if(dist < standoff + APPROACH_SLOW_ZONE && speed > MAX_APPROACH_SPEED) {
					aiEntity.stop();
				} else {
					Vector3f toBlock = new Vector3f();
					toBlock.sub(target, shipPos);
					aiEntity.moveTo(timer, toBlock, !mining);
				}
			} else if(dist < standoff - STANDOFF_SLACK) {
				// Too close — ease back so the hull never reaches the surface.
				Vector3f away = new Vector3f();
				away.sub(shipPos, target);
				aiEntity.moveTo(timer, away, false);
			} else {
				aiEntity.stop(); // at the stand-off — hold against drift/gravity
			}

			// Already mining? FleetMining aims + fires; we handled positioning above, so leave it.
			if(mining) {
				debug(entity, "mining (engine FleetMining state)");
				return;
			}

			// Not mining yet — once we're at the stand-off and nearly stopped, hand off to FleetMining.
			if(dist <= standoff + STANDOFF_SLACK && speed <= ENTER_SPEED) {
				if(!aiEntity.canSalvage()) {
					debug(entity, "at rock but canSalvage()==false (link salvage beams to a cargo/storage with free space)");
					return;
				}
				AIUtils.enterMiningState(entity, asteroid);
				debug(entity, "entering FleetMining on " + asteroid.getName());
			}
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
			if(!ConfigManager.getMainConfig().debugMode.getValue()) {
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
