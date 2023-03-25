package thederpgamer.combattweaks.manager;

import api.listener.Listener;
import api.listener.events.block.*;
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
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementKeyMap;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.listener.ShipAIShootListener;
import thederpgamer.combattweaks.system.RepairPasteFabricatorSystem;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import java.util.ArrayList;

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
				event.addModuleCollection(new ManagerModuleSingle<>(new VoidElementManager<>(event.getSegmentController(), ArmorHPCollection.class), Element.TYPE_NONE, Element.TYPE_ALL));
			}
		}, instance);
		StarLoader.registerListener(SegmentPieceAddByMetadataEvent.class, new Listener<SegmentPieceAddByMetadataEvent>() {
			@Override
			public void onEvent(SegmentPieceAddByMetadataEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) return;
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegment().getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						try {
							ArmorHPCollection collection = (ArmorHPCollection) cm;
							collection.addBlock(event.getAbsIndex(), event.getType());
							return;
						} catch(Exception exception) {
							exception.printStackTrace();
						}
					}
				}
			}
		}, instance);
		StarLoader.registerListener(SegmentPieceAddEvent.class, new Listener<SegmentPieceAddEvent>() {
			@Override
			public void onEvent(SegmentPieceAddEvent event) {
				if(!ElementKeyMap.getInfo(event.getNewType()).isArmor()) return;
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						ArmorHPCollection collection = (ArmorHPCollection) cm;
						collection.addBlock(event.getAbsIndex(), event.getNewType());
						return;
					}
				}
			}
		}, instance);
		StarLoader.registerListener(SegmentPieceRemoveEvent.class, new Listener<SegmentPieceRemoveEvent>() {
			@Override
			public void onEvent(SegmentPieceRemoveEvent event) {
				if(!ElementKeyMap.getInfo(event.getType()).isArmor()) return;
				for(ElementCollectionManager<?, ?, ?> cm : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) event.getSegment().getSegmentController(), ArmorHPCollection.class)) {
					if(cm instanceof ArmorHPCollection) {
						ArmorHPCollection collection = (ArmorHPCollection) cm;
						collection.removeBlock(event.getSegment().getAbsoluteIndex(event.getX(), event.getY(), event.getZ()), event.getType());
						return;
					}
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceDamageEvent.class, new Listener<SegmentPieceDamageEvent>() {
			@Override
			public void onEvent(SegmentPieceDamageEvent event) {
				if(!event.getController().isOnServer()) return;
				SegmentPiece segmentPiece = event.getController().getSegmentBuffer().getPointUnsave(event.getPos());
				if(segmentPiece.getType() == 14) { //Check if hit block was a warhead, if so, have it act as ERA and stop the projectile
					segmentPiece.setActive(true); //Activate the Warhead, exploding it if not already done
					segmentPiece.getSegmentController().sendBlockActivation(event.getPos());
					System.err.println("Warhead hit - Stopping projectile");
					segmentPiece.getSegmentController().sendBlockKill(segmentPiece);
					event.setCanceled(true);
				} else if(segmentPiece.getInfo().isArmor()) {
					ArrayList<ElementCollectionManager<?, ?, ?>> managers = SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) segmentPiece.getSegmentController(), ArmorHPCollection.class);
					for(ElementCollectionManager<?, ?, ?> manager : managers) {
						if(manager instanceof ArmorHPCollection) {
							ArmorHPCollection armorHPCollection = (ArmorHPCollection) manager;
							double currentHP = armorHPCollection.getCurrentHP();
							double maxHP = armorHPCollection.getMaxHP();
							double armorHP = currentHP / maxHP;
							float damage = event.getDamage();
							if(armorHP >= 0.3) {
								System.err.println("Armor HP is " + armorHP + " - Stopping projectile");
								armorHPCollection.setCurrentHP(currentHP - damage);
								event.setDamage(0);
								event.setCanceled(true);
							} else if(armorHP > 0) {
								System.err.println("Armor HP is " + armorHP + " - Stopping projectile with reduced damage");
								armorHPCollection.setCurrentHP(currentHP - damage);
								event.setDamage((int) (damage - (damage * armorHP)));
							}
						}
					}
				}
			}
		}, instance);

		StarLoader.registerListener(SegmentPieceKillEvent.class, new Listener<SegmentPieceKillEvent>() {
			@Override
			public void onEvent(SegmentPieceKillEvent event) {
				if(!event.getController().isOnServer()) return;
				SegmentPiece segmentPiece = event.getController().getSegmentBuffer().getPointUnsave(event.getPiece().getAbsoluteIndex());
				if(segmentPiece.getType() == 14) { //Check if hit block was a warhead, if so, have it act as ERA and stop the projectile
					segmentPiece.setActive(true); //Activate the Warhead, exploding it if not already done
					segmentPiece.getSegmentController().sendBlockActivation(event.getPiece().getAbsoluteIndex());
					System.err.println("Warhead hit - Stopping projectile");
					segmentPiece.getSegmentController().sendBlockKill(segmentPiece);
					event.setCanceled(true);
				} else if(segmentPiece.getInfo().isArmor()) {
					ArrayList<ElementCollectionManager<?, ?, ?>> managers = SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) segmentPiece.getSegmentController(), ArmorHPCollection.class);
					for(ElementCollectionManager<?, ?, ?> manager : managers) {
						if(manager instanceof ArmorHPCollection) {
							ArmorHPCollection armorHPCollection = (ArmorHPCollection) manager;
							double currentHP = armorHPCollection.getCurrentHP();
							double maxHP = armorHPCollection.getMaxHP();
							double armorHP = currentHP / maxHP;
							if(armorHP > 0) {
								System.err.println("Armor HP is " + armorHP + " - Stopping projectile");
								armorHPCollection.setCurrentHP(currentHP - segmentPiece.getInfo().getArmorValue());
								event.setCanceled(true);
							}
						}
					}
				}
			}
		}, instance);
	}
}
