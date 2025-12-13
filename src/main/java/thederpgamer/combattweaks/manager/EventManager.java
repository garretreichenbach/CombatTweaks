package thederpgamer.combattweaks.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.block.SegmentPieceAddByMetadataEvent;
import api.listener.events.block.SegmentPieceAddEvent;
import api.listener.events.block.SegmentPieceRemoveEvent;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.gui.MainWindowTabAddEvent;
import api.listener.events.gui.StructureStatsGroupsAddEvent;
import api.listener.events.gui.TargetPanelCreateEvent;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.events.register.RegisterConfigGroupsEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import org.lwjgl.input.Keyboard;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUITabbedContent;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.data.ControlBindingData;
import thederpgamer.combattweaks.effect.ConfigGroupRegistry;
import thederpgamer.combattweaks.gui.controls.ControlBindingsScrollableList;
import thederpgamer.combattweaks.gui.elements.AdvancedStructureStatsArmor;
import thederpgamer.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import thederpgamer.combattweaks.listener.DamageReductionByArmorCalcListenerImpl;
import thederpgamer.combattweaks.listener.ShipAIShootListenerImpl;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import java.util.ArrayList;
import java.util.Locale;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static DamageReductionByArmorCalcListenerImpl damageReductionByArmorCalcListener;

	public static void initialize(CombatTweaks instance) {
		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListenerImpl());
		FastListenerCommon.damageReductionByArmorCalcListeners.add(damageReductionByArmorCalcListener = new DamageReductionByArmorCalcListenerImpl());

		StarLoader.registerListener(MainWindowTabAddEvent.class, new Listener<MainWindowTabAddEvent>() {
			@Override
			public void onEvent(MainWindowTabAddEvent event) {
				if(event.getTitleAsString().equals(Lng.str("KEYBOARD")) && event.getWindow().getTabs().size() == 2) { //Make sure we aren't adding a duplicate tab
					GUIContentPane modControlsPane = event.getWindow().addTab(Lng.str("MOD CONTROLS"));
					GUITabbedContent tabbedContent = new GUITabbedContent(modControlsPane.getState(), modControlsPane.getContent(0));
					tabbedContent.activationInterface = event.getWindow().activeInterface;
					tabbedContent.onInit();
					tabbedContent.setPos(0, 2, 0);
					modControlsPane.getContent(0).attach(tabbedContent);

					for(StarMod mod : ControlBindingData.getBindings().keySet()) {
						ArrayList<ControlBindingData> modBindings = ControlBindingData.getBindings().get(mod);
						if(!modBindings.isEmpty()) {
							GUIContentPane modTab = tabbedContent.addTab(mod.getName().toUpperCase(Locale.ENGLISH));
							ControlBindingsScrollableList scrollableList = new ControlBindingsScrollableList(modTab.getState(), modTab.getContent(0), mod);
							scrollableList.onInit();
							modTab.getContent(0).attach(scrollableList);
						}
					}
				}
			}
		}, instance);

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
						ControlBindingData tacticalBinding = ControlBindingData.getBinding("Tactical Map - Open");
						int tacticalKey = tacticalBinding == null ? -99999 : tacticalBinding.getBinding();
						if((event.getKey() == Keyboard.KEY_ESCAPE || event.getKey() == tacticalKey) && TacticalMapGUIDrawer.getInstance().toggleDraw) {
							TacticalMapGUIDrawer.getInstance().toggleDraw();
						} else {
							try {
								if(event.getKey() == tacticalKey && event.isKeyDown() && GameClient.getClientState().getController().getPlayerInputs().isEmpty()) {
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
