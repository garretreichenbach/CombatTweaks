package org.schema.game.common.controller.elements.beam.repair;

import api.listener.fastevents.FastListenerCommon;
import api.listener.fastevents.RepairBeamHitListener;
import api.utils.game.SegmentControllerUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2IntOpenHashMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BlockBuffer;
import org.schema.game.client.controller.manager.ingame.BuildCallback;
import org.schema.game.client.controller.manager.ingame.BuildInstruction.Remove;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.beam.BeamColors;
import org.schema.game.common.controller.*;
import org.schema.game.common.controller.elements.BeamState;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager.OffensiveEffects;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.element.beam.BeamHandler;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.player.inventory.Inventory;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.util.FastCopyLongOpenHashSet;
import org.schema.schine.graphicsengine.core.Timer;
import thederpgamer.combattweaks.element.ElementManager;
import thederpgamer.combattweaks.system.RepairPasteFabricatorSystem;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Modified version of RepairBeamHandler.
 */
public class RepairBeamHandler extends BeamHandler {

	private final Object2ObjectOpenHashMap<Inventory, IntOpenHashSet> connectedInventories = new Object2ObjectOpenHashMap<Inventory, IntOpenHashSet>();
	private final Short2IntOpenHashMap consTmp = new Short2IntOpenHashMap();
	private final Short2IntOpenHashMap haveTmp = new Short2IntOpenHashMap();
	private long lastSentMsg;

	public RepairBeamHandler(SegmentController s, BeamHandlerContainer owner) {
		super(s, owner);
	}

	@Override
	public boolean canhit(BeamState con, SegmentController controller, String[] cannotHitReason, Vector3i position) {
		cannotHitReason[0] = "";
		return !controller.equals(getBeamShooter()) && controller instanceof Salvage && ((Salvage) controller).isRepariableFor(this, cannotHitReason, position);
	}

	@Override
	public float getBeamTimeoutInSecs() {
		return BEAM_TIMEOUT_IN_SECS;
	}

	@Override
	public float getBeamToHitInSecs(BeamState beamState) {
		return beamState.getTickRate();
	}

	@Override
	public int onBeamHit(BeamState hittingBeam, int beamHits, BeamHandlerContainer<SegmentController> container, SegmentPiece hitPiece, Vector3f from, Vector3f to, Timer timer, Collection<Segment> updatedSegments) {
		if(getBeamShooter() instanceof EditableSendableSegmentController) {
			if(hitPiece.getSegmentController().isClientOwnObject() && ((GameClientState) hitPiece.getSegmentController().getState()).getWorldDrawer() != null) {
				((GameClientState) hitPiece.getSegmentController().getState()).getWorldDrawer().getGuiDrawer().notifyEffectHit(hitPiece.getSegmentController(), OffensiveEffects.REPAIR);
			}

			//INSERTED CODE
			for (RepairBeamHitListener listener : FastListenerCommon.repairBeamHitListeners) listener.hitFromShip(this, hittingBeam, beamHits, container, hitPiece, from, to, timer, updatedSegments);
			///

			ArrayList<ElementCollectionManager<?, ?, ?>> managers = SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) hitPiece.getSegmentController(), ArmorHPCollection.class);
			for(ElementCollectionManager<?, ?, ?> manager : managers) {
				if(manager instanceof ArmorHPCollection) {
					ArmorHPCollection armorHPCollection = (ArmorHPCollection) manager;
					armorHPCollection.setCurrentHP(armorHPCollection.getCurrentHP() + (ElementKeyMap.getInfo(ElementKeyMap.HULL_ID).getArmorValue() * 1000));
				}
				break;
			}
			if(getBeamShooter().isOnServer()) {
				int maxHP = hitPiece.getInfo().getMaxHitPointsByte();
				if(hitPiece.getHitpointsByte() < maxHP) {
					hitPiece.setHitpointsByte(maxHP);
					hitPiece.applyToSegment(getBeamShooter().isOnServer());
				}
				if(hitPiece.getSegmentController() instanceof ManagedUsableSegmentController<?>) {
					ManagedUsableSegmentController<?> c = (ManagedUsableSegmentController<?>) hitPiece.getSegmentController();
					c.getHpController().forceReset();
					if(c.getBlockKillRecorder().size() > 0) {
						undo(hittingBeam.controllerPos, c, c.getBlockKillRecorder(), (int) hittingBeam.getPower());
					}
				}
			}
		}
		return beamHits;
	}

	public void undo(Vector3i controllerPos, ManagedUsableSegmentController<?> c, BlockBuffer blockRemoveBuffer, int amount) {
		try {
			connectInventories(ElementCollection.getIndex(controllerPos));
			checkInventories(blockRemoveBuffer, amount);
			final Remove r = new Remove();
			r.where = new SegmentPiece();
			r.connectedFromThis = new LongOpenHashSet();
			SegmentCollisionCheckerCallback cb = new SegmentCollisionCheckerCallback();

			//		for (final Remove r : blockRemoveBuffer.getRemoves()) {
			//			SegmentPiece p = r.where;
			//			SegmentCollisionCheckerCallback cb = new SegmentCollisionCheckerCallback();
			//
			//
			//			if (c.getCollisionChecker().checkPieceCollision(p, cb, false)) {
			//				notDone++;
			//			}
			//		}
			for(int i = 0; i < Math.min(amount, blockRemoveBuffer.size()); i++) {
				//INSERTED CODE
				boolean hasEnoughPaste = false;
				if(consumeResource(blockRemoveBuffer.peakNextType())) {
					short nextType = blockRemoveBuffer.peakNextType();
					ElementInformation elementInfo = ElementKeyMap.getInfo(nextType);
					float pasteNeeded = elementInfo.getMaxHitPointsFull() + elementInfo.getArmorValue();
					float currentPaste;
					if(((ManagedSegmentController<?>) getBeamShooter()).getManagerContainer() instanceof ShipManagerContainer) {
						try {
							ShipManagerContainer managerContainer = (ShipManagerContainer) ((ManagedSegmentController<?>) getBeamShooter()).getManagerContainer();
							RepairPasteFabricatorSystem moduleContainer = (RepairPasteFabricatorSystem) managerContainer.getModMCModule(ElementManager.getBlock("Repair Paste Fabricator").getId());
							if(moduleContainer.getRepairPasteCapacityMax() > 0) {
								currentPaste = moduleContainer.getRepairPasteCapacity();
								if(currentPaste >= pasteNeeded) {
									moduleContainer.setRepairPasteCapacity(currentPaste - pasteNeeded);
									hasEnoughPaste = true;
								}
							}
						} catch(Exception exception) {
							exception.printStackTrace();
						}
					}
					if(getBeamShooter().getState().getUpdateTime() - lastSentMsg > 1000) {
						short sourceType = (short) ElementKeyMap.getInfo(nextType).getSourceReference();
						short consType = sourceType != 0 ? sourceType : nextType;
						//if(!hasAnyPaste) getBeamShooter().sendServerMessage(Lng.astr("Not enough resources in connected or player inventory to repair!\nNeed %s", ElementKeyMap.getInfo(consType).getName()), ServerMessage.MESSAGE_TYPE_WARNING);
						//else if(!hasEnoughPaste) getBeamShooter().sendServerMessage("Not enough repair paste: " + StringTools.formatPointZero(currentPaste) + " / " + pasteNeeded + ".0", ServerMessage.MESSAGE_TYPE_WARNING);
						lastSentMsg = getBeamShooter().getState().getUpdateTime();
					}
					if(!hasEnoughPaste) return;
				}
				//
				blockRemoveBuffer.createInstruction(c, r);

				if(c.getCollisionChecker().checkPieceCollision(r.where, cb, false)) return;

				Vector3i absOnOut = new Vector3i(); //doen't matter since we have no callback
				BuildCallback b = new BuildCallback() {

					@Override
					public void onBuild(Vector3i posBuilt, Vector3i posNextToBuild, short type) {
					}

					@Override
					public long getSelectedControllerPos() {
						return r.controller;
					}
				};
				final SegmentPiece p = r.where;

				if(p.getType() == 0) {
					// Prevent crash caused from too many undo/redo calls too quick
					continue;
				}

				Vector3i pos = p.getAbsolutePos(new Vector3i());

				c.build(pos.x, pos.y, pos.z, p.getType(), p.getOrientation(), p.isActive(), b, absOnOut, new int[] {0, 1}, null, null);
				//INSERTED CODE
				if(r.connectedFromThis != null && !hasEnoughPaste) {
					//
					for(long l : r.connectedFromThis) {
						c.getControlElementMap().removeControlledFromAll(ElementCollection.getPosIndexFrom4(l), (short) ElementCollection.getType(l), true);
						c.getControlElementMap().addControllerForElement(p.getAbsoluteIndex(), ElementCollection.getPosIndexFrom4(l), (short) ElementCollection.getType(l));
					}
				}
			}
		} finally {
			for(it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry<Inventory, IntOpenHashSet> e : connectedInventories.object2ObjectEntrySet()) {
				e.getKey().sendInventoryModification(e.getValue());
			}

			connectedInventories.clear();
			consTmp.clear();
			haveTmp.clear();
		}
	}

	private void connectInventories(long beamController) {
		connectedInventories.clear();
		Short2ObjectOpenHashMap<FastCopyLongOpenHashSet> cm = getBeamShooter().getControlElementMap().getControllingMap().get(beamController);
		if(cm == null) {
			return;
		}

		for(short t : cm.keySet()) {
			if(ElementKeyMap.isValidType(t) && ElementKeyMap.getInfoFast(t).isInventory()) {
				FastCopyLongOpenHashSet invSet = cm.get(t);
				for(long indexInv : invSet) {
					Inventory inv = ((ManagedSegmentController<?>) getBeamShooter()).getManagerContainer().getInventory(ElementCollection.getPosIndexFrom4(indexInv));
					if(inv != null) {
						connectedInventories.put(inv, new IntOpenHashSet());
					}
				}
			}
		}

		if(connectedInventories.isEmpty()) {
			if(getBeamShooter().getOwnerState() != null) {
				connectedInventories.put(getBeamShooter().getOwnerState().getInventory(), new IntOpenHashSet());
			}
		}

	}

	private void checkInventories(BlockBuffer blockRemoveBuffer, int amount) {
		consTmp.clear();
		haveTmp.clear();
		amount = Math.min(blockRemoveBuffer.size(), amount);
		blockRemoveBuffer.peak(amount, consTmp);

		for(short type : consTmp.keySet()) {
			for(Inventory e : connectedInventories.keySet()) {
				short sourceType = (short) ElementKeyMap.getInfo(type).getSourceReference();
				haveTmp.add(type, e.getOverallQuantity(sourceType != 0 ? sourceType : type));
			}
		}
		//		System.err.println("BLOCK REMOVE BUFFER PEAK: "+consTmp);
		//		System.err.println("HAVE PEAK (connected invs: "+connectedInventories.size()+"): "+haveTmp);
	}

	private boolean consumeResource(short type) {
		//		System.err.println("HAVE::: "+haveTmp+"; peak: "+type+"; HAS: "+haveTmp.get(type));
		if(haveTmp.get(type) <= 0) {
			return false;
		}
		haveTmp.add(type, -1);
		for(it.unimi.dsi.fastutil.objects.Object2ObjectMap.Entry<Inventory, IntOpenHashSet> e : connectedInventories.object2ObjectEntrySet()) {
			short sourceType = (short) ElementKeyMap.getInfo(type).getSourceReference();
			short consType = sourceType != 0 ? sourceType : type;
			if(e.getKey().containsAny(consType)) {
				e.getKey().decreaseBatch(consType, 1, e.getValue());
				break;
			}
		}
		return true;
	}

	@Override
	protected boolean onBeamHitNonCube(BeamState con, int hits, BeamHandlerContainer<SegmentController> owner, Vector3f from, Vector3f to, CubeRayCastResult cubeResult, Timer timer, Collection<Segment> updatedSegments) {
		return false;
	}

	@Override
	public Vector4f getDefaultColor(BeamState beamState) {
		Vector4f clr = getColorRange(BeamColors.PURPLE);
		return clr;
	}

	@Override
	public boolean ignoreBlock(short type) {
		ElementInformation f = ElementKeyMap.getInfoFast(type);
		return f.isDrawnOnlyInBuildMode() && !f.hasLod();
	}

}