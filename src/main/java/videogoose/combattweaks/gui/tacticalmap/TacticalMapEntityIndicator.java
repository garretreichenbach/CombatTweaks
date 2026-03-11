package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.SpaceStation;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.world.VoidSystem;
import org.schema.game.server.ai.SegmentControllerAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.utils.SectorUtils;

import javax.vecmath.Vector3f;

public class TacticalMapEntityIndicator {
	// Shared empty transform to avoid allocations when current entity is null
	private static final Transform EMPTY_TRANSFORM = new Transform();
	public final Transform entityTransform = new Transform();
	private final SegmentController entity;
	// Reusable sector temporaries
	private final Vector3i tmpSector = new Vector3i();
	private final Vector3i tmpSectorOther = new Vector3i();
	public boolean selected;
	// Screen-space position cached each draw frame for click hit-testing
	public float screenX, screenY;
	// Screen-space bounding box extents for hover/click detection
	public float screenMinX, screenMaxX, screenMinY, screenMaxY;
	public boolean screenPosValid;
	private SegmentController targetData;
	private SegmentController defendTarget;
	private float timer;

	public TacticalMapEntityIndicator(SegmentController entity) {
		this.entity = entity;
	}

	/**
	 * Updates entityTransform from the entity's world state, adjusting for cross-sector
	 * offsets relative to the player's ship. This must be called each frame before any
	 * draw method that uses entityTransform (labels, paths, screen projection).
	 */
	public void updateEntityTransform() {
		if(entity.isCloakedFor(getCurrentEntity())) return;
		entityTransform.set(entity.getWorldTransform());
		SegmentController current = getCurrentEntity();
		if(current != null) {
			Vector3i curSector = current.getSector(tmpSectorOther);
			if(!getSector().equals(curSector)) SectorUtils.transformToSector(entityTransform, curSector, getSector());
		}
	}

	private SegmentController getCurrentEntity() {
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			return (SegmentController) GameClient.getCurrentControl();
		} else {
			return null;
		}
	}

	public Vector3i getSector() {
		return entity.getSector(tmpSector);
	}

	public SegmentController getEntity() {
		return entity;
	}

	public float getDistance() {
		Vector3f currentPos = getCurrentEntityTransform().origin;
		Vector3f entityPos = entity.getWorldTransform().origin;
		return Math.abs(Vector3fTools.distance(currentPos.x, currentPos.y, currentPos.z, entityPos.x, entityPos.y, entityPos.z));
	}

	/**
	 * Get distance from the focused/controlled ship to this entity.
	 * If no ship is focused by the camera, falls back to player distance.
	 * Used for displaying distance in labels on the tactical map.
	 */
	public float getDistanceFromFocusedShip() {
		SegmentController focusedShip = getCurrentEntity();
		if(focusedShip == null) {
			// No focused ship - fall back to distance from player
			return getDistance();
		}

		Vector3f focusedPos = focusedShip.getWorldTransform().origin;
		Vector3f entityPos = entity.getWorldTransform().origin;
		return Math.abs(Vector3fTools.distance(focusedPos.x, focusedPos.y, focusedPos.z, entityPos.x, entityPos.y, entityPos.z));
	}

	private Transform getCurrentEntityTransform() {
		SegmentController entity = getCurrentEntity();
		if(entity != null) {
			return entity.getWorldTransform();
		} else {
			return EMPTY_TRANSFORM;
		}
	}

	public SegmentController getCurrentTarget() {
		if(targetData == null && getAIEntity() != null && getAIEntity().getCurrentProgram() instanceof TargetProgram<?> && ((TargetProgram<?>) getAIEntity().getCurrentProgram()).getTarget() != null) {
			SimpleGameObject obj = ((TargetProgram<?>) getAIEntity().getCurrentProgram()).getTarget();
			if(obj instanceof SegmentController) {
				targetData = (SegmentController) obj;
			}
		}
		return targetData;
	}

	public void setCurrentTarget(SegmentController targetData) {
		this.targetData = targetData;
	}

	public SegmentController getDefendTarget() {
		return defendTarget;
	}

	public void setDefendTarget(SegmentController defendTarget) {
		this.defendTarget = defendTarget;
	}


	public SegmentControllerAIEntity<?> getAIEntity() {
		switch(entity.getType()) {
			case SHIP:
				return ((Ship) entity).getAiConfiguration().getAiEntityState();
			case SPACE_STATION:
				return ((SpaceStation) entity).getAiConfiguration().getAiEntityState();
			default:
				return null; //Only support Ship or Station AIs
		}
	}


	public void update(Timer timer) {
		this.timer += timer.getDelta();
	}

	@Override
	public int hashCode() {
		return getEntityId();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof TacticalMapEntityIndicator) {
			return ((TacticalMapEntityIndicator) obj).getEntityId() == getEntityId();
		}
		return false;
	}

	public int getEntityId() {
		return entity.getId();
	}

	public Vector3i getSystem() {
		return VoidSystem.getContainingSystem(getSector(), tmpSectorOther);
	}
}
