package videogoose.combattweaks.listener;

import api.common.GameCommon;
import api.common.GameServer;
import api.listener.fastevents.CustomAddOnUseListener;
import api.utils.game.PlayerUtils;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.SegmentBufferIteratorInterface;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SegmentData;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.manager.MineManager;
import videogoose.combattweaks.utils.AIUtils;
import videogoose.combattweaks.utils.CTLog;

import javax.vecmath.AxisAngle4f;
import javax.vecmath.Matrix3f;
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

	/**
	 * Distance from the stand-off over which the approach speed ramps down for a smooth arrival. Beyond this
	 * the ship thrusts at full engine power; within it the speed is capped/tapered. Kept fairly tight so the
	 * ship actually drives in under power instead of coasting the whole way (a too-large zone made nearly
	 * every same-sector asteroid fall entirely inside it, so the ship just drifted in at the low cap).
	 */
	private static final float APPROACH_SLOW_ZONE = 250.0f;
	/** Speed cap at the top of the slow zone (full speed beyond it), tapering to {@link #MIN_APPROACH_SPEED}. */
	private static final float MAX_APPROACH_SPEED = 150.0f;
	/** Minimum creep speed at the bottom of the slow zone, so the ship keeps closing the last gap. */
	private static final float MIN_APPROACH_SPEED = 8.0f;
	/** Per-tick velocity retention while braking in the slow zone (gentle, smooth decel — not a hard stop). */
	private static final float APPROACH_BRAKE_FACTOR = 0.85f;
	/** How often to re-find the nearest block (ms) — used only as the approach target. */
	private static final long BLOCK_REFRESH_MS = 2000;
	/**
	 * Stand-off the ship keeps between its hull (bounding sphere) and the nearest surface block while
	 * mining — small, since salvage beams are short range, but enough to never touch the rock. As blocks
	 * are mined the nearest block recedes and the ship follows inward at this stand-off, so it tunnels
	 * cleanly instead of stalling or ramming.
	 */
	private static final float STANDOFF = 40.0f;
	/** Dead-band around the stand-off so the ship doesn't constantly nudge in and out (jitter). */
	private static final float STANDOFF_SLACK = 20.0f;
	/**
	 * Much wider dead-band used <em>while mining</em>. The salvage beam (aimed by the FleetMining mixin)
	 * chews blocks from wherever the ship is parked, so the ship should hold still and let it work rather
	 * than chasing every change in "nearest block" — otherwise it skips from block to block faster than it
	 * can mine them. The ship only advances once the surface has genuinely receded past this margin (then
	 * it follows inward a chunk), and the stall detector orbits it to a fresh face when the spot is spent.
	 */
	private static final float MINING_HOLD_SLACK = 120.0f;
	/**
	 * How far past the stand-off the ship must get before we LEAVE the mining state and fly over. A wide
	 * hysteresis (much larger than the stand-off) so the nearest block changing as we mine — which makes
	 * the measured distance jump around a bit — doesn't flip us in and out of mining (the twitch). We
	 * only truly leave when the target is genuinely far (re-ordered to a distant rock, or knocked away).
	 */
	private static final float LEAVE_STANDOFF = 250.0f;
	/** Bleed velocity below this before handing off, so the ship enters the mining state nearly still. */
	private static final float ENTER_SPEED = 6.0f;
	/**
	 * If the asteroid's block count hasn't dropped for this long while we're mining, the current spot is
	 * exhausted (the reachable rock in front of the ship is gone, the rest is beyond physical beam reach).
	 * The engine's own "reposition to a new spot" trigger is disabled by our large salvage-range override
	 * (which we need so it fires at the surface of a big rock at all — the range check measures to the
	 * asteroid's center of mass), so we detect the stall and orbit to a fresh face ourselves.
	 *
	 * <p>Must be longer than the longest <em>legitimate</em> gap in block removal during normal mining —
	 * the ship rotating to face the next block after a column is cleared, a tough block taking a few
	 * seconds to chew through, or power/beam-cooldown cycling — otherwise we orbit away and back
	 * needlessly. A genuinely exhausted spot never recovers, so erring long only delays the (correct)
	 * orbit by a few seconds.</p>
	 */
	private static final long STALL_MS = 4000;
	/** Extra clearance beyond the asteroid's bounding sphere for the orbit/reposition waypoint. */
	private static final float REPOSITION_EXTRA = 60.0f;
	/** Considered "arrived" at the reposition waypoint within this distance of it (plus the hull radius). */
	private static final float REPOSITION_ARRIVE = 80.0f;
	/** Give up on a reposition waypoint after this long (e.g. blocked path) and just resume mining. */
	private static final long REPOSITION_TIMEOUT_MS = 8000;
	/** Minimum gap between "cargo full" warnings for a ship, so it isn't spammed every tick. */
	private static final long CARGO_WARN_INTERVAL_MS = 30000;

	/** Per-ship throttle for debug logging (entity id → last log time ms). */
	private final Map<Integer, Long> lastDebugLog = new ConcurrentHashMap<>();
	/** Per-ship throttle for the "cargo full" player warning (entity id → last warn time ms). */
	private final Map<Integer, Long> lastCargoWarn = new ConcurrentHashMap<>();
	/** Per-ship cached world position of the nearest asteroid block (approach target). */
	private final Map<Integer, Vector3f> blockAim = new ConcurrentHashMap<>();
	private final Map<Integer, Long> blockAimTime = new ConcurrentHashMap<>();
	/** Per-ship asteroid block count at last check, and the last time it dropped (mining progress). */
	private final Map<Integer, Integer> lastElementCount = new ConcurrentHashMap<>();
	private final Map<Integer, Long> lastProgressTime = new ConcurrentHashMap<>();
	/** Per-ship reposition waypoint (ship-frame) while orbiting to a fresh face; absent = mining normally. */
	private final Map<Integer, Vector3f> repositionTarget = new ConcurrentHashMap<>();
	private final Map<Integer, Long> repositionStart = new ConcurrentHashMap<>();
	/** Reused block finder (the AI tick is single-threaded, so one instance is fine). */
	private final NearestBlockFinder blockFinder = new NearestBlockFinder();

	@Override
	public void use(Ship entity, ShipManagerContainer managerContainer, Timer timer) {
		try {
			int id = entity.getId();

			// Attack: the engine only engages a target already within shooting range (a lone fleet ship has
			// no flagship to fly it there), so drive the ship into range ourselves, then hand off to the
			// engine's engage cycle to orient and fire.
			Integer attackTargetId = AIUtils.getAttackTarget(id);
			if(attackTargetId != null) {
				handleAttack(entity, attackTargetId, timer);
				return;
			}

			Integer asteroidId = MineManager.getInstance().getAssignedTarget(id);
			if(asteroidId == null) {
				clearShipState(id);
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
			Vector3f shipPos = entity.getWorldTransform().origin;
			float radius = entity.getBoundingSphere().radius;
			float speed = entity.getLinearVelocity(new Vector3f()).length();
			long now = System.currentTimeMillis();

			// Cargo full / no free storage — the ship has salvage beams (checked above) but can't store more,
			// so it can't mine. Park it and tell the player (throttled) instead of silently sitting there.
			if(!aiEntity.canSalvage()) {
				notifyCannotStore(entity, now);
				aiEntity.stop();
				return;
			}

			// Track mining progress: refresh the "last progress" stamp whenever the asteroid loses blocks
			// (or on first sighting). A stall (no block loss for STALL_MS while mining) means this spot is
			// done and we orbit to a fresh face.
			int elems = asteroid.getTotalElements();
			Integer prevElems = lastElementCount.get(id);
			if(prevElems == null || elems < prevElems) {
				lastProgressTime.put(id, now);
			}
			lastElementCount.put(id, elems);

			// Reposition phase: orbiting around the asteroid to a fresh face. Avoidance is ON here (docking
			// target cleared) so the ship flies around the rock rather than through it.
			Vector3f repo = repositionTarget.get(id);
			if(repo != null) {
				aiEntity.setDockingTarget(null);
				float repoDist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, repo.x, repo.y, repo.z);
				Long startT = repositionStart.get(id);
				boolean timedOut = startT != null && now - startT > REPOSITION_TIMEOUT_MS;
				if(repoDist < radius + REPOSITION_ARRIVE || timedOut) {
					repositionTarget.remove(id);
					repositionStart.remove(id);
					lastProgressTime.put(id, now); // fresh stall window for the new spot
					aiEntity.stop();
					debug(entity, "arrived at new mining section");
					return;
				}
				Vector3f toRepo = new Vector3f();
				toRepo.sub(repo, shipPos);
				aiEntity.moveTo(timer, toRepo, true);
				debug(entity, "repositioning to a fresh face");
				return;
			}

			// Disable collision avoidance toward the asteroid so the ship can close in (and during
			// mining); only the AIDocking state acts on dockingTarget, which we never enter.
			aiEntity.setDockingTarget(asteroid);

			Vector3f target = getTargetBlock(entity, asteroid, shipPos);
			if(target == null) {
				debug(entity, "no target block found this tick (asteroid sector=" + asteroid.getSectorId() + " ship sector=" + entity.getSectorId() + ")");
				return; // no block found this tick
			}
			float dist = Vector3fTools.distance(shipPos.x, shipPos.y, shipPos.z, target.x, target.y, target.z);
			// Stand-off measured from the hull (bounding sphere), so it's consistent across ship sizes.
			float standoff = radius + STANDOFF;
			float leaveDist = radius + LEAVE_STANDOFF;

			boolean mining = AIUtils.isMiningState(entity);

			CTLog.debugThrottled("mineapproach:" + id, 1000, "[MINE] ship=" + id + " dist=" + dist + " standoff=" + standoff
					+ " mining=" + mining + " speed=" + speed + " radius=" + radius
					+ " block=(" + target.x + "," + target.y + "," + target.z + ")"
					+ " shipPos=(" + shipPos.x + "," + shipPos.y + "," + shipPos.z + ")"
					+ " sameSector=" + (asteroid.getSectorId() == entity.getSectorId()));

			// Spot exhausted? If we've been mining but the asteroid hasn't lost a block for a while, the
			// reachable rock is gone — leave mining and orbit to a fresh face.
			if(mining && now - lastProgressTime.getOrDefault(id, now) > STALL_MS) {
				AIUtils.exitMiningState(entity);
				startReposition(id, entity, asteroid, shipPos, radius, now);
				debug(entity, "spot exhausted — orbiting to a new section");
				return;
			}

			// Leave mining ONLY when genuinely far (wide hysteresis). While mining, firing into empty
			// space far from the rock would drain the reactor and leave no power to thrust, so once we're
			// well clear we drop mining and fly over under power. The wide threshold means normal
			// block-to-block distance jitter while mining doesn't flip us out (the twitch).
			if(mining && dist > leaveDist) {
				AIUtils.exitMiningState(entity);
				mining = false;
			}

			// Position control — keep the ship at `standoff` from the nearest solid block, in BOTH phases.
			// FleetMining only aims+fires (never thrusts), so we own movement: it approaches from afar and,
			// while mining, follows the surface inward as blocks are removed. While FleetMining is active it
			// owns aiming, so our movement must not also orient the ship (or they fight and the beam drifts
			// off-target) — hence orient only when not mining.
			// While mining, hold position with a wide dead-band so the ship doesn't chase each shifting
			// nearest-block (which it can't keep up with); while approaching, use the tight band to arrive
			// precisely at the stand-off.
			float slack = mining ? MINING_HOLD_SLACK : STANDOFF_SLACK;
			if(dist > standoff + slack) {
				float remaining = dist - standoff;
				Vector3f toBlock = new Vector3f();
				toBlock.sub(target, shipPos);
				if(remaining > APPROACH_SLOW_ZONE) {
					// Far out — drive toward the rock at full speed.
					aiEntity.moveTo(timer, toBlock, !mining);
				} else {
					// In the slow zone — hold a speed that tapers with the remaining distance so the ship
					// decelerates smoothly to a near-stop at the stand-off. Gentle braking plus a coast band
					// (no per-tick hard stop/full-thrust) avoids the start/stop stutter.
					float cap = MIN_APPROACH_SPEED + (remaining / APPROACH_SLOW_ZONE) * (MAX_APPROACH_SPEED - MIN_APPROACH_SPEED);
					if(speed > cap * 1.1f) {
						AIUtils.brakeShip(entity, APPROACH_BRAKE_FACTOR); // bleed excess speed gently
					} else if(speed < cap * 0.9f) {
						aiEntity.moveTo(timer, toBlock, !mining); // a touch slow — ease forward
					}
					// else within the target speed band — coast.
				}
			} else if(dist < standoff - slack) {
				Vector3f away = new Vector3f();
				away.sub(shipPos, target);
				aiEntity.moveTo(timer, away, false); // too close — ease back so the hull never touches the rock
			} else {
				aiEntity.stop(); // within the hold band — sit still and let the beam mine
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
				lastProgressTime.put(entity.getId(), System.currentTimeMillis()); // fresh stall window
				debug(entity, "entering FleetMining on " + asteroid.getName());
			}
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error in mining controller", e);
		}
	}

	/**
	 * Picks a fresh face to mine and stores a reposition waypoint outside the asteroid in that direction.
	 *
	 * <p>Takes the current ship&rarr;asteroid-centre direction and rotates it by a random 60&ndash;120&deg;
	 * about a random perpendicular axis, so each reposition orbits to a genuinely different part of the
	 * surface (and won't oscillate between the same two spots). The waypoint sits just outside the
	 * asteroid's bounding sphere; once there, normal mining resumes and re-targets the now-nearest block
	 * on the new face.</p>
	 */
	private void startReposition(int id, Ship entity, FloatingRock asteroid, Vector3f shipPos, float radius, long now) {
		Vector3f center = new Vector3f(AIUtils.getTransformRelativeTo(entity, asteroid).origin);
		float astRadius = asteroid.getBoundingSphere().radius;

		Vector3f dir = new Vector3f();
		dir.sub(shipPos, center);
		if(dir.lengthSquared() < 1.0e-4f) {
			dir.set(0, 0, 1);
		} else {
			dir.normalize();
		}

		// A perpendicular axis, then spin it randomly about `dir` so the orbit direction varies each time.
		Vector3f up = Math.abs(dir.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		Vector3f axis = new Vector3f();
		axis.cross(dir, up);
		if(axis.lengthSquared() < 1.0e-4f) {
			axis.set(1, 0, 0);
		}
		axis.normalize();
		Matrix3f spin = new Matrix3f();
		spin.set(new AxisAngle4f(dir.x, dir.y, dir.z, (float) (Math.random() * Math.PI * 2.0)));
		spin.transform(axis);

		float angle = (float) Math.toRadians(30.0f + Math.random() * 30.0f);
		Matrix3f rot = new Matrix3f();
		rot.set(new AxisAngle4f(axis.x, axis.y, axis.z, angle));
		Vector3f newDir = new Vector3f(dir);
		rot.transform(newDir);
		newDir.normalize();

		Vector3f repo = new Vector3f(newDir);
		repo.scale(astRadius + radius + STANDOFF + REPOSITION_EXTRA);
		repo.add(center);

		repositionTarget.put(id, repo);
		repositionStart.put(id, now);
		blockAim.remove(id); // force a fresh nearest-block search at the new face
		blockAimTime.remove(id);
	}

	/** Drops all per-ship state when a ship is no longer mining (assignment cleared/finished). */
	private void clearShipState(int id) {
		blockAim.remove(id);
		blockAimTime.remove(id);
		lastElementCount.remove(id);
		lastProgressTime.remove(id);
		repositionTarget.remove(id);
		repositionStart.remove(id);
		lastDebugLog.remove(id);
		lastCargoWarn.remove(id);
	}

	/**
	 * Kicks off the engine's attack cycle for a commanded ship, then stays out of its way.
	 *
	 * <p>This used to manually fly the ship into weapon range, because the engine's fleet AI wouldn't fly a
	 * lone ship to a target beyond {@code getShootingRange()}. That gap is now closed by {@code
	 * ShipGameStateMixin} (the commanded target is acquired at any range), so the engine's own
	 * getting-to-target state flies the ship in and its engage cycle orbits/orients/fires. Steering on top of
	 * that was actively harmful: it fought the engine's orbit (the in/out oscillation) and — because {@code
	 * moveTo(..., orientate=true)} normalizes the direction — a degenerate or cross-sector-NaN direction set a
	 * <em>NaN orientation</em> on the ship, corrupting its transform so every later AI computation came out NaN
	 * and the ship froze until reload (the {@code RAYTRACE_TRAVERSE} NaN spam). So we no longer steer at all;
	 * we only (re)assert the target when the ship has dropped out of its attack cycle.</p>
	 */
	private void handleAttack(Ship entity, int targetId, Timer timer) {
		Object obj = GameCommon.getGameObject(targetId);
		if(!(obj instanceof SegmentController target) || target.getWorldTransform() == null) {
			return; // target gone — OrderQueueManager will retire the order
		}
		// Only (re)assert when out of the cycle. Never re-issue while it's already searching/engaging: the
		// search state clears the target on entry, so re-issuing each tick would restart the search forever.
		if(!AIUtils.isInAttackCycle(entity.getId())) {
			AIUtils.setAttackTarget(entity.getId(), targetId);
			debug(entity, "engaging attack target via engine cycle");
		}
		// else: the engine owns the engagement — approach/orient/fire is entirely its job now.
	}

	/**
	 * Tells the ship's faction (online members) that it can't mine because its cargo is full / it has no
	 * free storage, throttled to once per {@link #CARGO_WARN_INTERVAL_MS} so it doesn't spam.
	 */
	private void notifyCannotStore(Ship entity, long now) {
		try {
			Long last = lastCargoWarn.get(entity.getId());
			if(last != null && now - last < CARGO_WARN_INTERVAL_MS) {
				return;
			}
			lastCargoWarn.put(entity.getId(), now);
			int faction = entity.getFactionId();
			if(faction == 0) {
				return; // unfactioned — no obvious owner to notify
			}
			String msg = entity.getName() + " can't mine: cargo full (no free storage).";
			for(PlayerState player : GameServer.getServerState().getPlayerStatesByName().values()) {
				if(player.getFactionId() == faction) {
					PlayerUtils.sendMessage(player, msg);
				}
			}
		} catch(Exception ignored) {
		}
	}

	/**
	 * Returns the cached world position of the nearest asteroid block to the ship (the approach
	 * target), refreshing it every {@link #BLOCK_REFRESH_MS}. Returns null if none could be found.
	 */
	private Vector3f getTargetBlock(Ship ship, FloatingRock asteroid, Vector3f shipPos) {
		int shipId = ship.getId();
		long now = System.currentTimeMillis();
		Vector3f cached = blockAim.get(shipId);
		Long last = blockAimTime.get(shipId);
		if(cached != null && last != null && now - last < BLOCK_REFRESH_MS) {
			return cached;
		}
		Vector3f found = new Vector3f();
		if(findNearestBlock(ship, asteroid, shipPos, found)) {
			blockAim.put(shipId, found);
			blockAimTime.put(shipId, now);
			return found;
		}
		return cached;
	}

	/**
	 * Finds the world position of the asteroid block nearest the ship. Returns false if none found.
	 *
	 * <p>The asteroid's transform is rebased into the ship's sector frame first (see
	 * {@link AIUtils#getTransformRelativeTo}) — otherwise, when the asteroid is in a different sector,
	 * its block positions come out in a different coordinate frame than {@code shipPos} and the ship
	 * never flies over (it computes a garbage direction/distance and just sits there).</p>
	 */
	private boolean findNearestBlock(Ship ship, FloatingRock asteroid, Vector3f shipPos, Vector3f out) {
		try {
			blockFinder.reset(shipPos, AIUtils.getTransformRelativeTo(ship, asteroid));
			asteroid.getSegmentBuffer().iterateOverNonEmptyElement(blockFinder, true);
			blockFinder.finish();
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
		/** Best block position in WORLD (ship-frame) space — the approach target. */
		final Vector3f best = new Vector3f();
		/** Ship position expressed in the asteroid's local frame, so per-block comparison needs no transform. */
		private final Vector3f localShip = new Vector3f();
		private Transform worldTransform;
		boolean found;
		private float bestDistSq;

		void reset(Vector3f shipPos, Transform worldTransform) {
			this.worldTransform = worldTransform;
			// Inverse-transform the ship into the asteroid's local frame once; distances are preserved, so
			// the nearest block in local space is the nearest in world space — without a per-block transform.
			Transform inv = new Transform(worldTransform);
			inv.inverse();
			this.localShip.set(shipPos);
			inv.transform(this.localShip);
			this.found = false;
			this.bestDistSq = Float.MAX_VALUE;
		}

		@Override
		public boolean handle(Segment s, long lastChanged) {
			SegmentData data = s.getSegmentData();
			if(data == null) {
				return false;
			}
			// Scan EVERY valid block (not just the first per segment): the first block of the nearest
			// segment can be a corner tens of blocks off the real surface, so the ship would stand off from
			// the wrong point and the beams miss. Compare in local space, then transform only the winner.
			for(int i = 0; i < SegmentData.BLOCK_COUNT; i++) {
				if(ElementKeyMap.isValidType(data.getType(i))) {
					SegmentData.getPositionFromIndex(i, helper);
					piece.setByReference(s, helper);
					piece.getAbsolutePos(cand);
					cand.x -= SegmentData.SEG_HALF;
					cand.y -= SegmentData.SEG_HALF;
					cand.z -= SegmentData.SEG_HALF;
					float dx = cand.x - localShip.x;
					float dy = cand.y - localShip.y;
					float dz = cand.z - localShip.z;
					float distSq = dx * dx + dy * dy + dz * dz;
					if(distSq < bestDistSq) {
						bestDistSq = distSq;
						best.set(cand); // keep the local pos; transformed to world below
						found = true;
					}
				}
			}
			return false;
		}

		/** Convert the winning block's local position to world (ship-frame) space. Call after iterating. */
		void finish() {
			if(found) {
				worldTransform.transform(best);
			}
		}
	}
}
