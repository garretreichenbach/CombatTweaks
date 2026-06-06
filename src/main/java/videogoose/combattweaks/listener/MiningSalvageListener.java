package videogoose.combattweaks.listener;

import api.common.GameCommon;
import api.listener.fastevents.CustomAddOnUseListener;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.FocusableUsableModule;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.controller.elements.beam.harvest.SalvageBeamCollectionManager;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.network.objects.NetworkShip;
import org.schema.game.server.ai.AIShipControllerStateUnit;
import org.schema.game.server.ai.ShipAIEntity;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.manager.MineManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires a mining ship's salvage beams at its assigned asteroid, once per server AI tick.
 *
 * <p>StarMade's fleet-mining FSM can't drive an individually-commanded ship (it needs a multi-ship
 * fleet with a flagship), so we salvage manually: {@link MineManager} keeps the ship within salvage
 * range of the asteroid, and this listener — invoked by the engine on the server AI thread for every
 * non-docked AI ship — points the ship's AI target at the asteroid and fires only the salvage
 * element manager (mirroring what {@code FleetMining.updateAI} does, minus the fleet/formation
 * gating). Firing only salvage (not {@code doShooting}) avoids triggering any actual weapons.</p>
 */
public class MiningSalvageListener implements CustomAddOnUseListener {

	/** Cached reflective handle to ShipAIEntity's private per-entity controller unit. */
	private static Field unitField;
	/** Per-ship throttle for debug logging (entity id → last log time ms). */
	private final Map<Integer, Long> lastDebugLog = new ConcurrentHashMap<>();

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
			// canSalvage() is true only when the salvage beams are controlled and linked to storage
			// with free space. If it's false the beams can't actually collect ore, so don't fire.
			if(!aiEntity.canSalvage()) {
				debug(entity, "not salvaging: canSalvage()==false (need salvage beams linked to a cargo/storage with free space)");
				return;
			}

			// Only fire once reasonably close (firing from afar wastes reactor power and never connects).
			// Generous and floored so the reduced AI salvage range can't suppress firing at the
			// navigation hold distance — the beam's own range decides whether it actually connects.
			float fireDist = boundingRadius(asteroid) + Math.max(aiEntity.getSalvageRange(), 600.0f);
			float dist = Vector3fTools.distance(
					entity.getWorldTransform().origin.x, entity.getWorldTransform().origin.y, entity.getWorldTransform().origin.z,
					asteroid.getWorldTransform().origin.x, asteroid.getWorldTransform().origin.y, asteroid.getWorldTransform().origin.z);
			if(dist > fireDist) {
				return; // still being navigated into range by MoveManager
			}

			AIShipControllerStateUnit unit = getUnit(aiEntity);
			if(unit == null) {
				return;
			}

			// Aim the AI target at the asteroid. The salvage beam is aimable, so it steers from the
			// salvage output block toward this position regardless of hull facing. These must be set
			// every tick — the AI's idle state zeroes them otherwise — immediately before firing.
			// We deliberately don't gate on getSalvageRange() (the AI-reduced engagement range, which
			// is smaller than the navigation hold distance and was suppressing all firing); the beam
			// has its own real range and simply doesn't connect until MoveManager brings it close.
			if(entity.getNetworkObject() instanceof NetworkShip) {
				NetworkShip no = (NetworkShip) entity.getNetworkObject();
				no.targetPosition.set(asteroid.getWorldTransform().origin);
				no.targetVelocity.set(0, 0, 0);
				no.targetId.set(asteroid.getAsTargetId());
				no.targetType.set(SimpleGameObject.MINABLE);
			}

			for(SalvageBeamCollectionManager group : managerContainer.getSalvage().getCollectionManagers()) {
				group.setFireMode(FocusableUsableModule.FireMode.UNFOCUSED);
			}
			// Reset the AI mouse-button state exactly as vanilla mining does (doShooting does this for
			// MINABLE targets); without it the salvage aim/fire path never engages and beams don't
			// connect. Then fire ONLY the salvage element manager — we intentionally do NOT call
			// doShooting(), which would also run the repair pass and the attempt-to-shoot listener hook.
			// This guarantees no weapons, missiles or damage beams ever discharge during mining.
			unit.timesBothMouseButtonsDown = 0;
			unit.useBothMouseButtonsDown = false;
			managerContainer.getSalvage().getElementManager().handle(unit, timer);
			debug(entity, "firing salvage beams at " + asteroid.getName());
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error firing mining salvage beams", e);
		}
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

	/** Bounding sphere radius (half-diagonal of the AABB), 0 if unavailable. */
	private static float boundingRadius(FloatingRock asteroid) {
		org.schema.schine.graphicsengine.forms.BoundingBox bb = asteroid.getBoundingBox();
		if(bb == null) {
			return 0.0f;
		}
		float dx = (bb.max.x - bb.min.x) * 0.5f;
		float dy = (bb.max.y - bb.min.y) * 0.5f;
		float dz = (bb.max.z - bb.min.z) * 0.5f;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static AIShipControllerStateUnit getUnit(ShipAIEntity aiEntity) throws Exception {
		if(unitField == null) {
			Field f = ShipAIEntity.class.getDeclaredField("unit");
			f.setAccessible(true);
			unitField = f;
		}
		return (AIShipControllerStateUnit) unitField.get(aiEntity);
	}
}
