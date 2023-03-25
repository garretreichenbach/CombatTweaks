package thederpgamer.combattweaks.manager;

import api.listener.Listener;
import api.listener.events.block.SegmentPieceAddByMetadataEvent;
import api.listener.events.block.SegmentPieceAddEvent;
import api.listener.events.block.SegmentPieceRemoveEvent;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementCollection;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.listener.BeamListener;
import thederpgamer.combattweaks.listener.CannonProjectileListener;
import thederpgamer.combattweaks.listener.ShipAIShootListener;
import thederpgamer.combattweaks.system.RepairPasteFabricatorSystem;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class EventManager {

	public static ShipAIShootListener shipAIShootListener;
	public static CannonProjectileListener cannonProjectileListener;
	public static BeamListener beamListener;

	public static void initialize(CombatTweaks instance) {
		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListener());

		FastListenerCommon.cannonProjectileHitListeners.add(cannonProjectileListener = new CannonProjectileListener());

		FastListenerCommon.damageBeamHitListeners.add(beamListener = new BeamListener());

		StarLoader.registerListener(HudCreateEvent.class, new Listener<HudCreateEvent>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				HudManager.initialize(event);
			}
		}, instance);

		StarLoader.registerListener(ShipJumpEngageEvent.class, new Listener<ShipJumpEngageEvent>() {
			@Override
			public void onEvent(ShipJumpEngageEvent event) {
				JumpHandler.onJumpEngage(event);
			}
		}, instance);

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<ManagerContainerRegisterEvent>() {
			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				event.addModMCModule(new RepairPasteFabricatorSystem(event.getSegmentController(), event.getContainer()));
				event.addModuleCollection(new ManagerModuleSingle<>(new VoidElementManager<>(event.getSegmentController(), ArmorHPCollection.class), Element.TYPE_NONE, Element.TYPE_ALL));
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddByMetadataEvent.class, new Listener<SegmentPieceAddByMetadataEvent>() {
			@Override
			public void onEvent(SegmentPieceAddByMetadataEvent event) {
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegment().getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						ArmorHPCollection collection = (ArmorHPCollection) cm;
						collection.doAdd(ElementCollection.getIndex4(event.getSegment().getAbsoluteIndex(event.getX(), event.getY(), event.getZ()), event.getOrientation()), event.getType());
						return;
					}
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddEvent.class, new Listener<SegmentPieceAddEvent>() {
			@Override
			public void onEvent(SegmentPieceAddEvent event) {
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						ArmorHPCollection collection = (ArmorHPCollection) cm;
						collection.doAdd(ElementCollection.getIndex4(event.getAbsIndex(), event.getOrientation()), event.getNewType());
						return;
					}
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<SegmentPieceRemoveEvent>() {
			@Override
			public void onEvent(SegmentPieceRemoveEvent event) {
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegment().getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						ArmorHPCollection collection = (ArmorHPCollection) cm;
						collection.remove(ElementCollection.getIndex4(event.getSegment().getAbsoluteIndex(event.getX(), event.getY(), event.getZ()), event.getOrientation()));
						return;
					}
				}
			}
		}, instance);
	}
}
