package org.schema.game.common.controller.rails;

import java.io.IOException;
import java.sql.SQLException;

import javax.vecmath.Matrix3f;
import javax.vecmath.Vector3f;

import org.schema.common.FastMath;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.cubes.shapes.BlockShapeAlgorithm;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.client.view.cubes.shapes.orientcube.Oriencube;
import org.schema.game.common.controller.elements.ShipyardManagerContainerInterface;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.Element;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.world.SegmentData;
import org.schema.game.common.data.world.SimpleTransformableSendableObject.EntityType;
import org.schema.game.server.data.GameServerState;
import com.bulletphysics.linearmath.Transform;

public class RailRelation {
	/**
	 * block on the structure that is docking on another
	 */
	public final SegmentPiece docked;
	/**
	 * block on the structure where another structure is docked to
	 */
	public final SegmentPiece rail;

	public final Vector3i currentRailContact = new Vector3i();
	private final SegmentPiece tmp0 = new SegmentPiece();
	private final SegmentPiece tmp1 = new SegmentPiece();
	private final Vector3i tmpV = new Vector3i();
	private final SegmentPiece[] tmpPiece = new SegmentPiece[6];
	public boolean executed;
	public Vector3i railContactToGo;
	public DockingPermission dockingPermission = DockingPermission.NORMAL;
	public RotationType rotationCode = RotationType.NONE;
	public byte rotationSide;
	public boolean continueRotation;
	private boolean inRotatedServer;

	private RailRelationVars v;
	public boolean resetRotationOnce;
	public double delayNextMoveSec;

	protected static ThreadLocal<RailRelationVars> threadLocal = new ThreadLocal<RailRelationVars>() {
		@Override
		protected RailRelationVars initialValue() {
			return new RailRelationVars();
		}
	};

	public RailRelation(SegmentPiece docked, SegmentPiece rail) {
		this.docked = docked;
		this.rail = rail;

		for(int i = 0; i < tmpPiece.length; i++) {
			tmpPiece[i] = new SegmentPiece();
		}

		v = threadLocal.get();
	}

	public static boolean existsOrientcubeAlgo(SegmentPiece piece) {
		if(!ElementKeyMap.exists(piece.getType())) {
			return false;
		}
		return ElementKeyMap.getInfoFast(piece.getType()).getBlockStyle() != BlockStyle.NORMAL;
	}

	public static Oriencube getOrientcubeAlgo(SegmentPiece piece) {
		ElementInformation a = ElementKeyMap.getInfo(piece.getType());
		try {
			Oriencube algo = (Oriencube) BlockShapeAlgorithm.getAlgo(a.getBlockStyle(), piece.getOrientation());
			return algo;
		} catch(ArrayIndexOutOfBoundsException e) {
			System.err.println("EXCEPTION FROM: " + piece);
			throw e;
		}

	}

	public static Transform getTrans(SegmentPiece piece, Transform res, boolean mirr, RailRelationVars v) {

		Vector3f blockPosLocal = piece.getAbsolutePos(v.tmpVec3f0);
		blockPosLocal.x -= SegmentData.SEG_HALF;
		blockPosLocal.y -= SegmentData.SEG_HALF;
		blockPosLocal.z -= SegmentData.SEG_HALF;

		Transform primaryTransform;
		Transform secondaryTransform;

		if(piece.getSegmentController().getType() == EntityType.PLANET_ICO) {
			res.setIdentity();
//			res.set(((PlanetIco)piece.getSegmentController()).getRelativeCoreTransorm());
		} else if(piece.getType() == ElementKeyMap.CORE_ID) {
			res.setIdentity();
			res.origin.set(blockPosLocal);
		} else if(ElementKeyMap.isValidType(piece.getType())) {

			if(existsOrientcubeAlgo(piece)) {
				Oriencube algo = getOrientcubeAlgo(piece);
				if(mirr) {
					primaryTransform = algo.getMirrorAlgo().getPrimaryTransform(blockPosLocal, 0, v.tmpTrans3);
					secondaryTransform = algo.getMirrorAlgo().getSecondaryTransform(v.tmpTrans4);

				} else {
					//place inside if it's a shipyard core
					primaryTransform = algo.getPrimaryTransform(blockPosLocal, piece.getType() == ElementKeyMap.SHIPYARD_CORE_POSITION ? 0 : 1, v.tmpTrans3);
					secondaryTransform = algo.getSecondaryTransform(v.tmpTrans4);
				}

				res.set(primaryTransform);
				res.mul(secondaryTransform);
			} else {
				System.err.println("[ERROR][" + piece.getSegmentController().getState() + "][RAILRELATION] " + piece + " on " + piece.getSegmentController() + " has no valid block algo ");
				res.setIdentity();
				res.origin.set(blockPosLocal);
			}
		} else {
			System.err.println("[ERROR][" + piece.getSegmentController().getState() + "][RAILRELATION] " + piece + " on " + piece.getSegmentController() + " is not a valid docker ");
			res.setIdentity();
			res.origin.set(blockPosLocal);
		}

		return res;
	}

	public DockValidity getDockingValidity() {
		if(docked.getSegmentController() == null || rail.getSegmentController() == null) {
			return DockValidity.UNKNOWN;
		}
		SegmentPiece dockingBlock = docked.getSegmentController().getSegmentBuffer().getPointUnsave(docked.getAbsoluteIndex(), tmp0);

		if(dockingBlock == null) {
			return DockValidity.UNKNOWN;
		}

		if(rail.getSegmentController().railController.isRoot() && rail.getSegmentController().getType() == EntityType.PLANET_ICO) {

			return DockValidity.OK;
		}

		if(rail.getType() == ElementKeyMap.SHIPYARD_CORE_POSITION && ElementKeyMap.isValidType(dockingBlock.getType()) && dockingBlock.getType() == ElementKeyMap.CORE_ID) {

			SegmentPiece coreAnchorBlock = rail.getSegmentController().getSegmentBuffer().getPointUnsave(rail.getAbsoluteIndex(), tmp1);
			if(coreAnchorBlock == null) {
				return DockValidity.UNKNOWN;
			}

			//shipyard docked
			if(coreAnchorBlock.getType() == ElementKeyMap.SHIPYARD_CORE_POSITION && rail.getSegmentController() instanceof ManagedSegmentController<?> && ((ManagedSegmentController<?>) rail.getSegmentController()).getManagerContainer() instanceof ShipyardManagerContainerInterface && ((ShipyardManagerContainerInterface) ((ManagedSegmentController<?>) rail.getSegmentController()).getManagerContainer()).getShipyard().getElementManager().isValidShipYard(rail)) {
				//shipyard is ok and valid
				return DockValidity.OK;
			} else {
				if(docked.getSegmentController().isOnServer()) {
					if(docked.getSegmentController().isVirtualBlueprint()) {
						System.err.println("[SERVER][DOCKING][SHIPYARD] Design no longer docked because of missing anchor: " + docked.getSegmentController() + "; WRITING AND REMOVING OBJECT");
						docked.getSegmentController().setVirtualBlueprintRecursive(true);
						try {
							((GameServerState) docked.getSegmentController().getState()).getController().writeSingleEntityWithDock(docked.getSegmentController());
						} catch(IOException e) {
							e.printStackTrace();
						} catch(SQLException e) {
							e.printStackTrace();
						}
						docked.getSegmentController().setMarkedForDeleteVolatileIncludingDocks(true);
					}
				}
				return DockValidity.SHIPYARD_FAILED;
			}

		} else if(rail.getType() == ElementKeyMap.SHIPYARD_CORE_POSITION && !ElementKeyMap.isValidType(dockingBlock.getType())) {
			System.err.println("[SHIPYARD][RAIL] invalid dock " + docked.getSegmentController() + " on " + rail.getSegmentController() + " because docked core doesnt exist: should be " + docked.getSegmentController() + "; " + docked.getAbsolutePos(new Vector3i()) + ", " + ElementKeyMap.toString(docked.getType()) + "; but was " + dockingBlock + "; originally " + docked);
		}
		if(getDockedRController().shootOutFlag || getDockedRController().shootOutExecute) {
			return DockValidity.OK;
		}
		if(!ElementKeyMap.isValidType(dockingBlock.getType()) || !ElementKeyMap.getInfo(dockingBlock.getType()).isRailDocker()) {
//			System.err.println("[SERVER][RAIL] RAIL INVALID (DOCKER MISSING)"+dockingBlock);
			return DockValidity.RAIL_DOCK_MISSING;
		}

		for(int i = 0; i < 6; i++) {
			tmpV.set(currentRailContact);
			tmpV.add(Element.DIRECTIONSi[i]);

			SegmentPiece cp = rail.getSegmentController().getSegmentBuffer().getPointUnsave(tmpV, tmpPiece[i]);
			if(cp == null) {
				return DockValidity.UNKNOWN;
			} else if(ElementKeyMap.isValidType(cp.getType()) && ElementKeyMap.getInfo(cp.getType()).isRailDockable()) {
				return DockValidity.OK;
			}

		}
//		System.err.println("[SERVER][RAIL] RAIL BECAME INVALID: PRINTING MORE INFO:");
		for(int i = 0; i < 6; i++) {
			tmpV.set(currentRailContact);
			tmpV.add(Element.DIRECTIONSi[i]);

			SegmentPiece cp = rail.getSegmentController().getSegmentBuffer().getPointUnsave(tmpV, tmpPiece[i]);
//			System.err.println("#### "+i+" RAIL DIRECTION: "+Element.DIRECTIONSi[i]+"; POS: "+tmpV+" -> BLOCK: "+cp);

		}
		return DockValidity.TRACK_MISSING;
	}

	public RailController getDockedRController() {
		return docked.getSegmentController().railController;
	}

	public RailController getRailRController() {
		return rail.getSegmentController().railController;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return (int) (docked.getSegmentController().getId() * rail.getSegmentController().getId() + docked.getAbsoluteIndex() * rail.getAbsoluteIndex());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {

		return obj != null && obj instanceof RailRelation && ((RailRelation) obj).docked.equals(docked) && ((RailRelation) obj).rail.equals(rail);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "RailRelation [docked=" + docked + ", rail=" + rail + "]";
	}

	public Transform getBlockTransform(Transform outTurret, Transform originalOut, Transform movingTurretIn, Transform movingIn) {

		if(v != threadLocal.get()) {
			throw new RuntimeException("Consistency Exception. Found potential running condition. Please report this error with its logs");
		}

		Transform transLocalFromWithTurretMovement = getTrans(docked, v.tmpTrans0, true, v);
		Transform transLocalFromBasic = getTrans(docked, v.tmpTrans1, true, v);
		Transform transLocalTo = getTrans(rail, v.tmpTrans2, false, v);

		Matrix3f m = v.m;
		m.set(movingIn.basis);
		m.mul(transLocalTo.basis);
		transLocalTo.basis.set(m);

		transLocalFromWithTurretMovement.basis.invert();
		transLocalFromWithTurretMovement.basis.mul(movingTurretIn.basis);
		transLocalFromWithTurretMovement.basis.invert();

		/*
		 * both blocks are now exactly matching each other
		 *
		 * rotate around the secondary axis to get the
		 * docked object to mirror on the rail
		 */
		transLocalFromWithTurretMovement.inverse();

		transLocalFromBasic.inverse();

		outTurret.setIdentity();
		outTurret.origin.set(movingIn.origin);
		outTurret.mul(transLocalTo);
		outTurret.mul(transLocalFromWithTurretMovement);

		originalOut.setIdentity();
		originalOut.origin.set(movingIn.origin);
		originalOut.mul(transLocalTo);
		originalOut.mul(transLocalFromBasic);

		return outTurret;

	}

	public boolean isTurretDockBasic() {
		return ElementKeyMap.exists(rail.getType()) && ElementKeyMap.getInfoFast(rail.getType()).isRailTurret();
	}

	public boolean isTurretDock() {
		return isTurretDockBasic() && isRailTurretDockedValid();
	}

	private boolean isRailTurretDockedValid() {
		//check if its either top/bottom to top/bottom, or left/right to left/right
		return isRailTurretXAxis() || isRailTurretYAxis();
	}

	public boolean isRailTurretYAxis() {
		if(!ElementKeyMap.exists(rail.getType()) || !ElementKeyMap.exists(docked.getType())) {
			return false;
		}
		if(ElementKeyMap.getInfo(docked.getType()).getBlockStyle() != BlockStyle.NORMAL24) {
			return false;
		}
		Oriencube algoDock = (Oriencube) BlockShapeAlgorithm.getAlgo(ElementKeyMap.getInfo(docked.getType()).getBlockStyle(), docked.getOrientation());

		return isTurretDockBasic() && (algoDock.getOrientCubePrimaryOrientation() == Element.TOP || algoDock.getOrientCubePrimaryOrientation() == Element.BOTTOM);
	}

	public boolean isRailTurretXAxis() {
		if(!ElementKeyMap.exists(rail.getType()) || !ElementKeyMap.exists(docked.getType())) {
			return false;
		}
		if(ElementKeyMap.getInfo(docked.getType()).getBlockStyle() != BlockStyle.NORMAL24) {
			return false;
		}
		Oriencube algoDock = (Oriencube) BlockShapeAlgorithm.getAlgo(ElementKeyMap.getInfo(docked.getType()).getBlockStyle(), docked.getOrientation());

		return isTurretDockBasic() && (algoDock.getOrientCubePrimaryOrientation() == Element.LEFT || algoDock.getOrientCubePrimaryOrientation() == Element.RIGHT);
	}

	public SegmentPiece[] getCurrentRailContactPiece(SegmentPiece[] out) {
		for(int i = 0; i < 6; i++) {
			tmpV.set(currentRailContact);
			tmpV.add(Element.DIRECTIONSi[i]);

			SegmentPiece pointUnsave = rail.getSegmentController().getSegmentBuffer().getPointUnsave(tmpV, tmpPiece[i]);

//			System.err.println("#### "+i+" ("+Element.getSideString(i)+") FROM "+currentRailContact+" FOOOODOOO "+pointUnsave);

			if(pointUnsave != null && ElementKeyMap.isValidType(pointUnsave.getType()) && ElementKeyMap.getInfo(pointUnsave.getType()).isRailTrack() && Element.getOpposite(getOrientcubeAlgo(pointUnsave).getOrientCubePrimaryOrientationSwitchedLeftRight()) == i) {
				//only add rails that are actually facing the docker block of the docked structure
				//e.g. if we are testing the block above, make sure that the primary orientation of that block points down
				out[i] = pointUnsave;
			} else {
//				if(pointUnsave != null && ElementKeyMap.isValidType(pointUnsave.getType()) &&
//						ElementKeyMap.getInfo(pointUnsave.getType()).isRailTrack()){
//					System.err.println("FOUND BLOCK AT: "+pointUnsave+" but not opposite i "+Element.getSideString(i)+"  !=   "+(Element.getSideString(Element.getOpposite(getOrientcubeAlgo(pointUnsave).getOrientCubePrimaryOrientationSwitchedLeftRight()))));
//				}
				out[i] = null;
			}
		}
		return out;
	}

	public void setInRotationServer() {
		inRotatedServer = true;
	}

	public void resetInRotationServer() {
		this.resetRotationOnce = true;
		inRotatedServer = false;
	}

	public boolean doneInRotationServer() {
		return inRotatedServer;
	}

	public boolean isCurrentRailContactPiece(SegmentPiece toReplace) {
		for(int i = 0; i < 6; i++) {
			int side = i;
			tmpV.set(currentRailContact);
			tmpV.add(Element.DIRECTIONSi[side]);

			SegmentPiece pointUnsave = rail.getSegmentController().getSegmentBuffer().getPointUnsave(tmpV, tmpPiece[side]);

			//FIXME: hack... not very nice
			side = Element.switchLeftRight(side);

			if(pointUnsave != null && ElementKeyMap.isValidType(pointUnsave.getType()) && ElementKeyMap.getInfo(pointUnsave.getType()).isRailTrack() && Element.getOpposite(getOrientcubeAlgo(pointUnsave).getOrientCubePrimaryOrientation()) == side && toReplace.getAbsoluteIndex() == pointUnsave.getAbsoluteIndex()) {
				//only add rails that are actually facing the docker block of the docked structure
				//e.g. if we are testing the block above, make sure that the primary orientation of that block points down
				return true;
			}
		}
		return false;
	}

	public boolean isTurretDockLastAxis() {
		if(isTurretDock()) {
			for(RailRelation r : docked.getSegmentController().railController.next) {
				if(r.isTurretDock()) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public enum DockingPermission {
		NORMAL,
		PUBLIC
	}

	public enum DockValidity {
		UNKNOWN,
		OK,
		RAIL_DOCK_MISSING,
		TRACK_MISSING,
		SHIPYARD_FAILED,
	}

	public enum RotationType {
		NONE(0, false),
		CW_45(1, false),
		CW_90(2, false),
		CW_135(3, false),
		CW_180(4, false),
		CW_225(5, false),
		CW_270(6, false),
		CW_315(7, false),
		CW_360(8, false),

		CCW_45(1, true),
		CCW_90(2, true),
		CCW_135(3, true),
		CCW_180(4, true),
		CCW_225(5, true),
		CCW_270(6, true),
		CCW_315(7, true),
		CCW_360(8, true),
		;

		public final float rad;
		private final Matrix3f[] out = new Matrix3f[8];
		private final boolean counterClockwise;

		private RotationType(float rad, boolean counterClockwise) {
			this.rad = rad;
			this.counterClockwise = counterClockwise;
			assert (ordinal() == 0 || this.rad != 0);
			for(int i = 0; i < 8; i++) {
				out[i] = new Matrix3f();
			}
		}

		public float getRailSpeed(float basis) {
			return basis / getRailSpeedDivider();
		}

		private float getRailSpeedDivider() {
			float s = ordinal() > 8 ? ordinal() - 8 : ordinal();
			return s;
		}

		public Matrix3f[] getRotation(byte rotationSide) {
			float hp = FastMath.HALF_PI * 0.5f;
			for(int i = 0; i < rad; i++) {
				float v = (counterClockwise ? -1 : 1) * (i + 1);
				switch(rotationSide) {
					case (Element.FRONT):
						out[i].rotZ((v * hp));
						break;
					case (Element.BACK):
						out[i].rotZ(-(v * hp));
						break;
					case (Element.RIGHT):
						out[i].rotX(-(v * hp));
						break;
					case (Element.LEFT):
						out[i].rotX((v * hp));
						break;
					case (Element.TOP):
						out[i].rotY((v * hp));
						break;
					case (Element.BOTTOM):
						out[i].rotY(-(v * hp));
						break;

					default:
						throw new IllegalArgumentException("INVALID rotation orientation: " + rotationSide);
				}
			}
			return out;
		}
	}

	public boolean isShipyardDock() {
		return rail.getType() == ElementKeyMap.SHIPYARD_CORE_POSITION;
	}

	public RailRequest getRailRequest(RailController railController) {
		return railController.getRailRequest(docked, rail, currentRailContact, railContactToGo, dockingPermission);
	}

}