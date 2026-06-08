package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import com.bulletphysics.linearmath.Transform;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.viewer.PositionableViewer;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.input.Mouse;

import javax.vecmath.Vector3f;

public class TacticalMapCamera extends Camera {

	private final Vector3f tmpBack = new Vector3f();
	public Transform transform;

	public TacticalMapCamera() {
		super(GameClient.getClientState(), new PositionableViewer());
	}

	/** The object the player is currently controlling — ship, station, or astronaut character — or null. */
	private static SimpleTransformableSendableObject<?> control() {
		return GameClient.getClientPlayerState() != null ? GameClient.getClientPlayerState().getFirstControlledTransformableWOExc() : null;
	}

	@Override
	public void reset() {
		super.reset();
		SimpleTransformableSendableObject<?> control = control();
		if(control == null) {
			return;
		}
		// Right after an FTL jump / sector change the controlled entity is briefly mid-reload and has no
		// world transform (or bounding box) yet. Bail out WITHOUT caching anything so the next open retries
		// cleanly — dereferencing these here is what crashed clients that opened the map straight after a jump.
		Transform controlTransform = control.getWorldTransform();
		if(controlTransform == null) {
			return;
		}
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		Transform defaultTransform = new Transform();
		// Always re-anchor to the current controlled entity. Caching the very first transform forever left
		// the camera locked to the pre-jump entity's (now stale) transform after a sector change.
		transform = controlTransform;
		defaultTransform.set(transform);
		int sectorSize = drawer.getSectorSize();
		defaultTransform.origin.add(new Vector3f(sectorSize, sectorSize, -sectorSize));
		setLookAlgorithm(new TacticalCameraLook(this, transform));

		Transform temp = new Transform(transform);
		temp.basis.set(lookAt(false).basis);
		temp.basis.invert();
		getWorldTransform().set(temp);

		// Back the camera off the controlled object. Ships/stations size the offset to their bounds (when the
		// bounding box is available); an astronaut — or an entity whose box hasn't loaded — uses a fixed stand-off.
		float backoff = 60.0f;
		if(control instanceof SegmentController) {
			BoundingBox bb = ((SegmentController) control).getBoundingBox();
			if(bb != null) {
				backoff = bb.maxSize() + 30;
			}
		}
		tmpBack.set(getForward());
		tmpBack.scale(backoff);
		tmpBack.negate();
		getWorldTransform().origin.add(tmpBack);
	}

	@Override
	public void update(Timer timer, boolean server) {
		alwaysAllowWheelZoom = true;
		SimpleTransformableSendableObject<?> control = control();
		if(control != null) {
			if(transform == null) {
				transform = control.getWorldTransform(); // may still be null mid-jump; guarded below
			}
			if(transform != null && Mouse.isGrabbed() && Mouse.isButtonDown(1)) {
				getLookAlgorithm().mouseRotate(server, mouseState.dx / 1000.0F, mouseState.dy / 1000.0F, 0.0F, getMouseSensibilityX(), getMouseSensibilityY(), 0.0F);
			}
		}
	}
}