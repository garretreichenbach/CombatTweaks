package videogoose.combattweaks.manager;

import api.common.GameClient;
import api.entity.SegmentControllerUtils;
import api.event.EventResult;
import api.event.entity.ShipJumpEngageListener;
import api.event.gui.HudCreateListener;
import api.event.input.KeyPressListener;
import api.event.lifecycle.ManagerContainerRegisterListener;
import api.event.lifecycle.RegisterAddonsListener;
import api.event.lifecycle.RegisterConfigGroupsListener;
import api.event.render.RegisterWorldDrawersListener;
import api.event.systems.CustomAddOnUseListener;
import api.event.systems.ReactorRecalibrateListener;
import api.event.weapon.ShipAIEntityAttemptToShootListener;
import api.network.packets.PacketUtil;
import api.player.PlayerUtils;
import org.schema.schine.graphicsengine.core.GLFW;
import org.schema.schine.input.KeyboardMappings;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleCollection;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.listener.AuraProjectorAddOnUseListener;
import videogoose.combattweaks.listener.MiningSalvageListener;
import videogoose.combattweaks.listener.ShipAIShootListenerImpl;
import videogoose.combattweaks.network.client.SendThrustBlastPacket;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;
import videogoose.combattweaks.system.aura.OffenseAuraAddOn;
import videogoose.combattweaks.system.aura.SupportAuraAddOn;
import videogoose.combattweaks.system.weapon.auradisruptor.AuraDisruptorBeamElementManager;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static MiningSalvageListener miningSalvageListener;

	/** Movement keys whose in-flight double-tap fires a Thrust Blast (ported from BetterChambers). */
	private static final KeyboardMappings[] THRUST_BLAST_KEYS = {
			KeyboardMappings.FORWARD_SHIP, KeyboardMappings.BACKWARDS_SHIP, KeyboardMappings.UP_SHIP,
			KeyboardMappings.DOWN_SHIP, KeyboardMappings.STRAFE_LEFT_SHIP, KeyboardMappings.STRAFE_RIGHT_SHIP};
	private static final long THRUST_DOUBLE_TAP_MS = 300;
	/** Last movement key seen and its timestamp, for double-tap detection. */
	private static int lastThrustKey = -1;
	private static long lastThrustKeyMs = 0;

	public static void initialize(CombatTweaks instance) {

		ShipAIEntityAttemptToShootListener.TYPE.register(shipAIShootListener = new ShipAIShootListenerImpl(), instance);
		CustomAddOnUseListener.TYPE.register(miningSalvageListener = new MiningSalvageListener(), instance);
		CustomAddOnUseListener.TYPE.register(new AuraProjectorAddOnUseListener(), instance);

		HudCreateListener.TYPE.register((hud, state, elements, isServer) -> HudManager.initializeHud(state, elements), instance);

		ShipJumpEngageListener.TYPE.register((context, isServer) -> {
			try {
				if(context.getController() != null && context.getNewSector() != null) {
					IncomingSignatureManager.getInstance().recordJump(context.getController().getId(), context.getNewSector());
				}
			} catch(Exception ignored) {
			}
			return EventResult.CONTINUE;
		}, instance);


		KeyPressListener.TYPE.register((event, isServer) -> {
			if(GameClient.getClientState().getController().getPlayerInputs().isEmpty() && !GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getChatControlManager().isActive()) {
				if(GameClient.getClientPlayerState().getFirstControlledTransformableWOExc() != null && event.isPressed()) {
					KeyboardMappings tacticalKey = CombatTweaks.getInstance().tacticalMapKey;
					boolean isTacticalKey = tacticalKey != null && event.getKey() == tacticalKey.getMapping();
					if((event.getKey() == GLFW.GLFW_KEY_ESCAPE || isTacticalKey) && TacticalMapGUIDrawer.getInstance().toggleDraw) {
						if(event.getKey() == GLFW.GLFW_KEY_ESCAPE && TacticalMapGUIDrawer.getInstance().placementActive) {
							// Esc during placement cancels the placement and keeps the tactical map open.
							TacticalMapGUIDrawer.getInstance().requestCancelPlacement = true;
						} else {
							TacticalMapGUIDrawer.getInstance().toggleDraw();
						}
					} else {
						try {
							if(isTacticalKey && GameClient.getClientState().getController().getPlayerInputs().isEmpty()) {
								TacticalMapGUIDrawer.getInstance().toggleDraw();
							}
						} catch(Exception exception) {
							CombatTweaks.getInstance().logException("Error processing tactical map key press", exception);
						}
					}
				}
			}
		}, instance);

		// Thrust Blast: double-tapping a movement key while piloting in flight fires the vanilla Take-Off effect.
		KeyPressListener.TYPE.register((event, isServer) -> {
			try {
				int key = KeyboardMappings.getEventKeySingle(event);
				if(lastThrustKey == key) {
					if(lastThrustKeyMs > 0 && System.currentTimeMillis() - lastThrustKeyMs < THRUST_DOUBLE_TAP_MS) {
						if(GameClient.getClientState() != null
								&& GameClient.getClientState().isInFlightMode()
								&& PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()) instanceof ManagedUsableSegmentController<?> control) {
							for(KeyboardMappings mapping : THRUST_BLAST_KEYS) {
								if(mapping.getMapping() == key && mapping.isDown(GameClient.getClientState())) {
									PacketUtil.sendPacketToServer(new SendThrustBlastPacket(control));
									break;
								}
							}
						}
					}
					lastThrustKeyMs = System.currentTimeMillis();
				}
				lastThrustKey = key;
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("Error processing thrust blast key press", exception);
			}
		}, instance);

		RegisterConfigGroupsListener.TYPE.register((pool, groups, isServer) -> ConfigGroupRegistry.registerEffects(groups), instance);

		// Attach both aura projector addons (support + offense) to every ship's manager container so either can be
		// player-activated. They're mutually exclusive at the chamber level, so at most one is ever usable.
		// The addons register themselves with the container in their constructor.
		RegisterAddonsListener.TYPE.register((container, isServer) -> {
			new SupportAuraAddOn(container);
			new OffenseAuraAddOn(container);
		}, instance);

		// Recompute aura range/effects when a ship's reactor (chambers) changes — for whichever aura it runs.
		ReactorRecalibrateListener.TYPE.register((implementation, isServer) -> {
			if(implementation.getSegmentController() instanceof ManagedUsableSegmentController<?> controller) {
				AuraProjectorAddOn.recalibrateAll(controller, implementation);
			}
		}, instance);

		RegisterWorldDrawersListener.TYPE.register((worldDrawer, drawers, isServer) -> {
			if(TacticalMapGUIDrawer.getInstance() == null) {
				drawers.add(new TacticalMapGUIDrawer());
			}
		}, instance);

		ManagerContainerRegisterListener.TYPE.register((context, isServer) -> {
			// Attach the Aura Disruptor weapon system (computer/module pair) so ships can mount and fire it.
			context.addModuleCollection(new ManagerModuleCollection(new AuraDisruptorBeamElementManager(context.getSegmentController()), BlockRegistry.AURA_DISRUPTOR_COMPUTER.getId(), BlockRegistry.AURA_DISRUPTOR_MODULE.getId()));
		}, instance);
	}
}
