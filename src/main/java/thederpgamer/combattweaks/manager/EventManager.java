package thederpgamer.combattweaks.manager;

import api.common.GameClient;
import api.listener.Listener;
import api.listener.events.block.SegmentPieceAddByMetadataEvent;
import api.listener.events.block.SegmentPieceAddEvent;
import api.listener.events.block.SegmentPieceRemoveEvent;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.gui.TargetPanelCreateEvent;
import api.listener.events.input.KeyPressEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import api.utils.game.PlayerUtils;
import org.lwjgl.input.Keyboard;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementKeyMap;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import thederpgamer.combattweaks.listener.DamageReductionByArmorCalcListenerImpl;
import thederpgamer.combattweaks.listener.ShipAIShootListenerImpl;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

public class EventManager {

	public static ShipAIShootListenerImpl shipAIShootListener;
	public static DamageReductionByArmorCalcListenerImpl damageReductionByArmorCalcListener;

	public static void initialize(CombatTweaks instance) {
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
						if((event.getKey() == Keyboard.KEY_ESCAPE || event.getKey() == Keyboard.KEY_PERIOD) && TacticalMapGUIDrawer.getInstance().toggleDraw) {
							TacticalMapGUIDrawer.getInstance().toggleDraw();
						} else {
							try {
								if(event.getKey() == Keyboard.KEY_PERIOD && event.isKeyDown() && GameClient.getClientState().getController().getPlayerInputs().isEmpty()) {
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
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController().railController.getRoot());
				}
				if(manager != null) {
					manager.addBlock(event.getAbsIndex(), event.getType());
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
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController().railController.getRoot());
				}
				if(manager != null) {
					manager.addBlock(event.getAbsIndex(), event.getNewType());
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<SegmentPieceRemoveEvent>() {
			@Override
			public void onEvent(SegmentPieceRemoveEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) {
					return;
				}
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(event.getSegment().getSegmentController().railController.isDocked()) {
					manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController().railController.getRoot());
				}
				if(manager != null) {
					manager.removeBlock(ElementCollection.getIndex(new Vector3i(event.getX(), event.getY(), event.getZ())), event.getType());
				}
			}
		}, instance);
	}
}
