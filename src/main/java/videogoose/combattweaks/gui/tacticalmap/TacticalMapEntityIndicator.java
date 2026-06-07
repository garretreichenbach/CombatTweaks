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
	// Client-side velocity estimate (finite-difference of position), because the entity's RigidBody velocity
	// is inactive/flickery on the client — the physics run server-side. Smoothed and sampled on a short
	// interval so the readout is stable.
	private final Vector3f prevPos = new Vector3f();
	private long prevPosTime;
	private boolean hasPrevPos;
	private final Vector3f velEstimate = new Vector3f();

	public TacticalMapEntityIndicator(SegmentController entity) {
		this.entity = entity;
	}

	/**
	 * Updates entityTransform from the entity's world state. This must be called each frame before any
	 * draw method that uses entityTransform (labels, paths, screen projection).
	 *
	 * <p>Uses the engine's own client render transform ({@code getWorldTransformOnClient()}), which already
	 * places entities in other sectors relative to the player's sector — exactly the way the scene and the
	 * game's own markers are positioned. We previously applied our own sector offset using
	 * {@code ServerConfig.SECTOR_SIZE}, but the engine builds its cross-sector transform with
	 * {@code GameStateInterface.getSectorSize()} (plus planet-rotation handling); the mismatch made our
	 * overlays drift whenever the camera looked at a sector other than the player's. Deferring to the
	 * engine's transform keeps everything locked to the rendered ships.</p>
	 */
	public void updateEntityTransform() {
		if(entity.getWorldTransform() == null) return;
		if(entity.isCloakedFor(getCurrentEntity())) return;
		Transform clientTransform = entity.getWorldTransformOnClient();
		if(clientTransform == null) return;
		entityTransform.set(clientTransform);
		updateVelocityEstimate();
	}

	/**
	 * Estimates velocity from how far {@link #entityTransform} moved since the last sample. The client's
	 * physics body velocity is unreliable (server-authoritative), so we differentiate position instead, with
	 * smoothing and a sanity cap that rejects the position jump a sector change produces.
	 */
	private void updateVelocityEstimate() {
		long now = System.currentTimeMillis();
		Vector3f cur = entityTransform.origin;
		if(!hasPrevPos) {
			prevPos.set(cur);
			prevPosTime = now;
			hasPrevPos = true;
			return;
		}
		float dt = (now - prevPosTime) / 1000.0f;
		if(dt < 0.02f) {
			return; // sample at most ~50 Hz so dt is meaningful
		}
		float dx = cur.x - prevPos.x, dy = cur.y - prevPos.y, dz = cur.z - prevPos.z;
		float instSpeed = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
		if(instSpeed <= 5000.0f) {
			// Exponential smoothing toward the instantaneous velocity.
			float alpha = 0.3f;
			velEstimate.x += (dx / dt - velEstimate.x) * alpha;
			velEstimate.y += (dy / dt - velEstimate.y) * alpha;
			velEstimate.z += (dz / dt - velEstimate.z) * alpha;
		} else {
			velEstimate.set(0, 0, 0); // implausible (sector change / teleport) — drop it
		}
		prevPos.set(cur);
		prevPosTime = now;
	}

	/** Smoothed client-side speed estimate (world units/sec). */
	public float getEstimatedSpeed() {
		return velEstimate.length();
	}

	/** Smoothed client-side velocity estimate into {@code out}. */
	public void getEstimatedVelocity(Vector3f out) {
		out.set(velEstimate);
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
		if(entity.getWorldTransform() == null) return Float.MAX_VALUE;
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
