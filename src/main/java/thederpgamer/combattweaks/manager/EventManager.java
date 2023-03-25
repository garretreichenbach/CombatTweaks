package thederpgamer.combattweaks.manager;

import api.listener.Listener;
import api.listener.events.block.*;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.events.weapon.MissileHitEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ManagerModuleSingle;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementKeyMap;
import thederpgamer.combattweaks.CombatTweaks;
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

	public static void initialize(CombatTweaks instance) {
		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListener());

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
				event.addModuleCollection(new ManagerModuleSingle<>(new VoidElementManager<>(event.getSegmentController(), ArmorHPCollection.class), Element.TYPE_NONE, Element.TYPE_NONE));
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddByMetadataEvent.class, new Listener<SegmentPieceAddByMetadataEvent>() {
			@Override
			public void onEvent(SegmentPieceAddByMetadataEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) return;
				if(!(event.getSegment().getSegmentController() instanceof ManagedUsableSegmentController<?>)) return;
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(manager != null) manager.addBlock(event.getAbsIndex(), event.getType());
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceAddEvent.class, new Listener<SegmentPieceAddEvent>() {
			@Override
			public void onEvent(SegmentPieceAddEvent event) {
				if(!ElementKeyMap.getInfo(event.getNewType()).isArmor()) return;
				if(!(event.getSegment().getSegmentController() instanceof ManagedUsableSegmentController<?>)) return;
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(manager != null) manager.addBlock(event.getAbsIndex(), event.getNewType());
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<SegmentPieceRemoveEvent>() {
			@Override
			public void onEvent(SegmentPieceRemoveEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) return;
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getSegment().getSegmentController());
				if(manager != null) manager.removeBlock(event.getType());
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceDamageEvent.class, new Listener<SegmentPieceDamageEvent>() {
			@Override
			public void onEvent(SegmentPieceDamageEvent event) {
				SegmentPiece segmentPiece = event.getController().getSegmentBuffer().getPointUnsave(event.getPos());
				if(segmentPiece.getType() == 14) { //Check if hit block was a warhead, if so, have it act as ERA and stop the projectile
					segmentPiece.getSegmentController().sendBlockKill(segmentPiece); //Remove the Warhead without blowing it up
					event.setCanceled(true);
				} else {
					ArmorHPCollection manager = ArmorHPCollection.getCollection(segmentPiece.getSegmentController());
					if(manager != null) {
						double currentHP = manager.getCurrentHP();
						double maxHP = manager.getMaxHP();
						double armorHP = currentHP / maxHP;
						if(armorHP > 0) {
							manager.setCurrentHP(currentHP - event.getDamage());
							event.setCanceled(true);
						}
					}
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceKillEvent.class, new Listener<SegmentPieceKillEvent>() {
			@Override
			public void onEvent(SegmentPieceKillEvent event) {
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getPiece().getSegmentController());
				if(manager != null) {
					double currentHP = manager.getCurrentHP();
					double maxHP = manager.getMaxHP();
					double armorHP = currentHP / maxHP;
					if(armorHP > 0) {
						manager.setCurrentHP(currentHP - ElementKeyMap.getInfo(ElementKeyMap.HULL_ID).getArmorValue());
						event.setCanceled(true);
					}
				}
			}
		}, instance);

		StarLoader.registerListener(MissileHitEvent.class, new Listener<MissileHitEvent>() {
			@Override
			public void onEvent(MissileHitEvent event) {
				ArmorHPCollection manager = ArmorHPCollection.getCollection(event.getRaycast().getSegment().getSegmentController());
				if(manager != null) {
					double currentHP = manager.getCurrentHP();
					double maxHP = manager.getMaxHP();
					double armorHP = currentHP / maxHP;
					if(armorHP > 0) {
						manager.setCurrentHP(currentHP - ElementKeyMap.getInfo(ElementKeyMap.HULL_ID).getArmorValue());
						event.setCanceled(true);
					}
				}
			}
		}, instance);
	}
}
