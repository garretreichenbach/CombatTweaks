package thederpgamer.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import com.bulletphysics.linearmath.Transform;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.viewer.PositionableViewer;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.input.Mouse;

import javax.vecmath.Vector3f;

public class TacticalMapCamera extends Camera {

	public Transform transform;

	// Reusable temporary vector to avoid allocations
	private final Vector3f tmpBack = new Vector3f();

	public TacticalMapCamera() {
		super(GameClient.getClientState(), new PositionableViewer());
	}

	@Override
	public void reset() {
		super.reset();
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
			Transform defaultTransform = new Transform();
			if(transform == null) transform = (((SegmentController) GameClient.getCurrentControl()).getWorldTransform());
			defaultTransform.set(transform);
			defaultTransform.origin.add(new Vector3f(drawer.sectorSize, drawer.sectorSize, -drawer.sectorSize));
			setLookAlgorithm(new TacticalCameraLook(this, transform));

			Transform temp = new Transform(transform);
			temp.basis.set(lookAt(false).basis);
			temp.basis.invert();
			getWorldTransform().set(temp);

			tmpBack.set(getForward());
			tmpBack.scale(((SegmentController) GameClient.getCurrentControl()).getBoundingBox().maxSize() + 15);
			tmpBack.negate();
			getWorldTransform().origin.add(tmpBack);
		}
	}

	@Override
	public void update(Timer timer, boolean server) {
		alwaysAllowWheelZoom = true;
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			if(transform == null) transform = (((SegmentController) GameClient.getCurrentControl()).getWorldTransform());
			if(Mouse.isGrabbed() && Mouse.isButtonDown(1)) {
				getLookAlgorithm().mouseRotate(server, mouseState.dx / 1000.0F, mouseState.dy / 1000.0F, 0.0F, getMouseSensibilityX(), getMouseSensibilityY(), 0.0F);
			}
		}
	}
}