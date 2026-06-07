package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import com.bulletphysics.linearmath.Transform;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.viewer.PositionableViewer;
import org.schema.schine.graphicsengine.core.Timer;
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
		if(control != null) {
			TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
			Transform defaultTransform = new Transform();
			if(transform == null) {
				transform = control.getWorldTransform();
			}
			defaultTransform.set(transform);
			int sectorSize = drawer.getSectorSize();
			defaultTransform.origin.add(new Vector3f(sectorSize, sectorSize, -sectorSize));
			setLookAlgorithm(new TacticalCameraLook(this, transform));

			Transform temp = new Transform(transform);
			temp.basis.set(lookAt(false).basis);
			temp.basis.invert();
			getWorldTransform().set(temp);

			// Back the camera off the controlled object. Ships/stations size the offset to their bounds; an
			// astronaut (no meaningful bounding box) uses a fixed stand-off.
			float backoff = control instanceof SegmentController ? ((SegmentController) control).getBoundingBox().maxSize() + 30 : 60.0f;
			tmpBack.set(getForward());
			tmpBack.scale(backoff);
			tmpBack.negate();
			getWorldTransform().origin.add(tmpBack);
		}
	}

	@Override
	public void update(Timer timer, boolean server) {
		alwaysAllowWheelZoom = true;
		SimpleTransformableSendableObject<?> control = control();
		if(control != null) {
			if(transform == null) {
				transform = control.getWorldTransform();
			}
			if(Mouse.isGrabbed() && Mouse.isButtonDown(1)) {
				getLookAlgorithm().mouseRotate(server, mouseState.dx / 1000.0F, mouseState.dy / 1000.0F, 0.0F, getMouseSensibilityX(), getMouseSensibilityY(), 0.0F);
			}
		}
	}
}