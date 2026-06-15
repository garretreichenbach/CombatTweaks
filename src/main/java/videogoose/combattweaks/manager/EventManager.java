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
import api.utils.game.SegmentControllerUtils;
import org.lwjgl.input.Keyboard;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.gui.elements.AdvancedStructureStatsArmor;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.listener.AuraProjectorAddOnUseListener;
import videogoose.combattweaks.listener.DamageReductionByArmorCalcListenerImpl;
import videogoose.combattweaks.listener.MiningSalvageListener;
import videogoose.combattweaks.listener.ShipAIShootListenerImpl;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.system.armor.ArmorHPUnit;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static DamageReductionByArmorCalcListenerImpl damageReductionByArmorCalcListener;
	public static MiningSalvageListener miningSalvageListener;

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
							TacticalMapGUIDrawer.getInstance().toggleDraw();
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


		StarLoader.registerListener(RegisterConfigGroupsEvent.class, new Listener<>() {
			@Override
			public void onEvent(RegisterConfigGroupsEvent event) {
				ConfigGroupRegistry.registerEffects(event.getModConfigGroups());
			}
		}, instance);

		// Attach the Aura Projector addon to every ship's manager container so it can be player-activated.
		StarLoader.registerListener(RegisterAddonsEvent.class, new Listener<>() {
			@Override
			public void onEvent(RegisterAddonsEvent event) {
				event.addModule(new AuraProjectorAddOn(event.getContainer()));
			}
		}, instance);

		// Recompute aura range/effects when a ship's reactor (chambers) changes.
		StarLoader.registerListener(ReactorRecalibrateEvent.class, new Listener<>() {
			@Override
			public void onEvent(ReactorRecalibrateEvent event) {
				if(event.getImplementation().getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					AuraProjectorAddOn projector = SegmentControllerUtils.getAddon((ManagedUsableSegmentController<?>) event.getImplementation().getSegmentController(), AuraProjectorAddOn.class);
					if(projector != null) {
						projector.onReactorRecalibrate(event);
					}
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
