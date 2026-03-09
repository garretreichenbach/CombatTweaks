package videogoose.combattweaks.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.block.SegmentPieceAddByMetadataEvent;
import api.listener.events.block.SegmentPieceAddEvent;
import api.listener.events.block.SegmentPieceRemoveEvent;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.gui.StructureStatsGroupsAddEvent;
import api.listener.events.gui.TargetPanelCreateEvent;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.events.register.RegisterConfigGroupsEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import api.utils.game.PlayerUtils;
import org.lwjgl.input.Keyboard;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.input.KeyboardMappings;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.gui.elements.AdvancedStructureStatsArmor;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.listener.DamageReductionByArmorCalcListenerImpl;
import videogoose.combattweaks.listener.ShipAIShootListenerImpl;
import videogoose.combattweaks.system.armor.ArmorHPCollection;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static DamageReductionByArmorCalcListenerImpl damageReductionByArmorCalcListener;

	private static KeyboardMappings tacticalMapMapping;

	private static KeyboardMappings getMappingFromName(String name) {
		for(KeyboardMappings mapping : KeyboardMappings.values()) {
			if(mapping.name().equals(name)) {
				return mapping;
			}
		}
		return null;
	}

	public static void initialize(CombatTweaks instance) {
		tacticalMapMapping = getMappingFromName("OPEN_TACTICAL_MAP");

		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListenerImpl());
		FastListenerCommon.damageReductionByArmorCalcListeners.add(damageReductionByArmorCalcListener = new DamageReductionByArmorCalcListenerImpl());

		StarLoader.registerListener(HudCreateEvent.class, new Listener<HudCreateEvent>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				HudManager.initializeHud(event);
			}
		}, instance);

		StarLoader.registerListener(TargetPanelCreateEvent.class, new Listener<TargetPanelCreateEvent>() {
			@Override
			public void onEvent(TargetPanelCreateEvent event) {
				HudManager.initializeTargetPanel(event);
			}
		}, instance);

		StarLoader.registerListener(KeyPressEvent.class, new Listener<KeyPressEvent>() {
			@Override
			public void onEvent(KeyPressEvent event) {
				if(GameClient.getClientState().getController().getPlayerInputs().isEmpty() && !GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getChatControlManager().isActive()) {
					if(PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()) instanceof ManagedUsableSegmentController<?> && event.isKeyDown()) {
						int tacticalKey = tacticalMapMapping.get();
						// Determine if the event corresponds to the tactical key. Some layouts deliver character events with key==0 (NONE),
						// so we accept the backslash character as a fallback.
						boolean isTacticalKey = false;
						try {
							if(event.getKey() == tacticalKey) {
								isTacticalKey = true;
							} else if(event.getKey() == 0) {
								char c = Keyboard.getEventCharacter();
								if(c == '\\') {
									isTacticalKey = true;
								}
							}
						} catch(Throwable t) {
							// Ignore, but do not block processing
						}
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


		StarLoader.registerListener(RegisterConfigGroupsEvent.class, new Listener<RegisterConfigGroupsEvent>() {
			@Override
			public void onEvent(RegisterConfigGroupsEvent event) {
				ConfigGroupRegistry.registerEffects(event.getModConfigGroups());
			}
		}, instance);

		StarLoader.registerListener(RegisterWorldDrawersEvent.class, new Listener<RegisterWorldDrawersEvent>() {
			@Override
			public void onEvent(RegisterWorldDrawersEvent event) {
				if(TacticalMapGUIDrawer.getInstance() == null) {
					event.getModDrawables().add(new TacticalMapGUIDrawer());
				}
			}
		}, instance);

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<ManagerContainerRegisterEvent>() {
			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				event.addModuleCollection(new ManagerModuleSingle<>(new VoidElementManager<>(event.getSegmentController(), ArmorHPCollection.class), ElementKeyMap.CORE_ID, ElementKeyMap.CORE_ID));
				if(event.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					ArmorHPCollection.enqueueRecalc(event.getSegmentController());
				}
			}
		}, instance);

		StarLoader.registerListener(StructureStatsGroupsAddEvent.class, new Listener<StructureStatsGroupsAddEvent>() {
			@Override
			public void onEvent(StructureStatsGroupsAddEvent event) {
				event.addGroup(new AdvancedStructureStatsArmor(event.getAdvancedStructureStats()));
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddByMetadataEvent.class, new Listener<SegmentPieceAddByMetadataEvent>() {
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

		StarLoader.registerListener(SegmentPieceAddEvent.class, new Listener<SegmentPieceAddEvent>() {
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

		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<SegmentPieceRemoveEvent>() {
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
