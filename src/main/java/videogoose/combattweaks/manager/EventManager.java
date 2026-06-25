package videogoose.combattweaks.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.block.SegmentPieceAddByMetadataEvent;
import api.listener.events.block.SegmentPieceAddEvent;
import api.listener.events.block.SegmentPieceRemoveEvent;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.gui.StructureStatsGroupsAddEvent;
import api.listener.events.gui.TargetPanelCreateEvent;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.events.register.RegisterAddonsEvent;
import api.listener.events.register.RegisterConfigGroupsEvent;
import api.listener.events.systems.ReactorRecalibrateEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import api.network.packets.PacketUtil;
import api.utils.game.PlayerUtils;
import api.utils.game.SegmentControllerUtils;
import org.lwjgl.input.Keyboard;
import org.schema.schine.input.KeyboardMappings;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleCollection;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.gui.elements.AdvancedStructureStatsArmor;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.listener.AuraProjectorAddOnUseListener;
import videogoose.combattweaks.listener.DamageReductionByArmorCalcListenerImpl;
import videogoose.combattweaks.listener.MiningSalvageListener;
import videogoose.combattweaks.listener.ShipAIShootListenerImpl;
import videogoose.combattweaks.network.client.SendThrustBlastPacket;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.system.armor.ArmorHPUnit;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;
import videogoose.combattweaks.system.aura.OffenseAuraAddOn;
import videogoose.combattweaks.system.aura.SupportAuraAddOn;
import videogoose.combattweaks.system.weapon.auradisruptor.AuraDisruptorBeamElementManager;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static DamageReductionByArmorCalcListenerImpl damageReductionByArmorCalcListener;
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

		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListenerImpl());
		FastListenerCommon.damageReductionByArmorCalcListeners.add(damageReductionByArmorCalcListener = new DamageReductionByArmorCalcListenerImpl());
		FastListenerCommon.customAddOnUseListeners.add(miningSalvageListener = new MiningSalvageListener());
		FastListenerCommon.customAddOnUseListeners.add(new AuraProjectorAddOnUseListener());

		StarLoader.registerListener(HudCreateEvent.class, new Listener<>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				HudManager.initializeHud(event);
			}
		}, instance);

		StarLoader.registerListener(ShipJumpEngageEvent.class, new Listener<>() {
			@Override
			public void onEvent(ShipJumpEngageEvent event) {
				try {
					if(event.getController() != null && event.getNewSector() != null) {
						IncomingSignatureManager.getInstance().recordJump(event.getController().getId(), event.getNewSector());
					}
				} catch(Exception ignored) {
				}
			}
		}, instance);

		StarLoader.registerListener(TargetPanelCreateEvent.class, new Listener<>() {
			@Override
			public void onEvent(TargetPanelCreateEvent event) {
				HudManager.initializeTargetPanel(event);
			}
		}, instance);

		StarLoader.registerListener(KeyPressEvent.class, new Listener<>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				if(GameClient.getClientState().getController().getPlayerInputs().isEmpty() && !GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getChatControlManager().isActive()) {
					if(GameClient.getClientPlayerState().getFirstControlledTransformableWOExc() != null && event.isKeyDown()) {
						boolean isTacticalKey = event.isMapping(CombatTweaks.getInstance().tacticalMapKey);
						if((event.getKey() == Keyboard.KEY_ESCAPE || isTacticalKey) && TacticalMapGUIDrawer.getInstance().toggleDraw) {
							if(event.getKey() == Keyboard.KEY_ESCAPE && TacticalMapGUIDrawer.getInstance().placementActive) {
								// Esc during placement cancels the placement and keeps the tactical map open.
								TacticalMapGUIDrawer.getInstance().requestCancelPlacement = true;
							} else {
								TacticalMapGUIDrawer.getInstance().toggleDraw();
							}
						} else {
							try {
								if(isTacticalKey && event.isKeyDown() && GameClient.getClientState().getController().getPlayerInputs().isEmpty()) {
									TacticalMapGUIDrawer.getInstance().toggleDraw();
								}
							} catch(Exception exception) {
								CombatTweaks.getInstance().logException("Error processing tactical map key press", exception);
							}
						}
					}
				}
			}
		}, instance);


		// Thrust Blast: double-tapping a movement key while piloting in flight fires the vanilla Take-Off effect.
		StarLoader.registerListener(KeyPressEvent.class, new Listener<>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				try {
					int key = KeyboardMappings.getEventKeySingle(event.getRawEvent());
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
			}
		}, instance);

		StarLoader.registerListener(RegisterConfigGroupsEvent.class, new Listener<>() {
			@Override
			public void onEvent(RegisterConfigGroupsEvent event) {
				ConfigGroupRegistry.registerEffects(event.getModConfigGroups());
			}
		}, instance);

		// Attach both aura projector addons (support + offense) to every ship's manager container so either can be
		// player-activated. They're mutually exclusive at the chamber level, so at most one is ever usable.
		StarLoader.registerListener(RegisterAddonsEvent.class, new Listener<>() {
			@Override
			public void onEvent(RegisterAddonsEvent event) {
				event.addModule(new SupportAuraAddOn(event.getContainer()));
				event.addModule(new OffenseAuraAddOn(event.getContainer()));
			}
		}, instance);

		// Recompute aura range/effects when a ship's reactor (chambers) changes — for whichever aura it runs.
		StarLoader.registerListener(ReactorRecalibrateEvent.class, new Listener<>() {
			@Override
			public void onEvent(ReactorRecalibrateEvent event) {
				if(event.getImplementation().getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					AuraProjectorAddOn.recalibrateAll((ManagedUsableSegmentController<?>) event.getImplementation().getSegmentController(), event);
				}
			}
		}, instance);

		StarLoader.registerListener(RegisterWorldDrawersEvent.class, new Listener<>() {
			@Override
			public void onEvent(RegisterWorldDrawersEvent event) {
				if(TacticalMapGUIDrawer.getInstance() == null) {
					event.getModDrawables().add(new TacticalMapGUIDrawer());
				}
			}
		}, instance);

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<>() {
			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager = new VoidElementManager<>(event.getSegmentController(), ArmorHPCollection.class);
				event.addModuleCollection(new ManagerModuleSingle<>(armorHPManager, ElementKeyMap.CORE_ID, ElementKeyMap.CORE_ID) {
					@Override
					public boolean needsAnyUpdate() {
						return getCollectionManager().needsUpdate();
					}
				});
				// Attach the Aura Disruptor weapon system (computer/module pair) so ships can mount and fire it.
				event.addModuleCollection(new ManagerModuleCollection(new AuraDisruptorBeamElementManager(event.getSegmentController()), BlockRegistry.AURA_DISRUPTOR_COMPUTER.getId(), BlockRegistry.AURA_DISRUPTOR_MODULE.getId()));
				if(event.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					ArmorHPCollection.enqueueRecalc(event.getSegmentController());
				}
			}
		}, instance);

		StarLoader.registerListener(StructureStatsGroupsAddEvent.class, new Listener<>() {
			@Override
			public void onEvent(StructureStatsGroupsAddEvent event) {
				event.addGroup(new AdvancedStructureStatsArmor(event.getAdvancedStructureStats()));
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddByMetadataEvent.class, new Listener<>() {
			@Override
			public void onEvent(SegmentPieceAddByMetadataEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) {
					return;
				}
				if(!(event.getSegment().getSegmentController() instanceof ManagedUsableSegmentController<?>)) {
					return;
				}
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController().railController.getRoot());
				} else {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController());
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddEvent.class, new Listener<>() {
			@Override
			public void onEvent(SegmentPieceAddEvent event) {
				if(!ElementKeyMap.getInfo(event.getNewType()).isArmor()) {
					return;
				}
				if(!(event.getSegment().getSegmentController() instanceof ManagedUsableSegmentController<?>)) {
					return;
				}
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController().railController.getRoot());
				} else {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController());
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<>() {
			@Override
			public void onEvent(SegmentPieceRemoveEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) {
					return;
				}
				if(!(event.getSegment().getSegmentController() instanceof ManagedUsableSegmentController<?>)) {
					return;
				}
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController().railController.getRoot());
				} else {
					ArmorHPCollection.enqueueRecalc(event.getSegment().getSegmentController());
				}
			}
		}, instance);
	}
}
