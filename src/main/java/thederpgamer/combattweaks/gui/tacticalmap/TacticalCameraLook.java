package thederpgamer.combattweaks.gui.tacticalmap;

import com.bulletphysics.linearmath.Transform;
import org.schema.common.FastMath;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.camera.look.MouseLookAlgorithm;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.input.Mouse;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;

public class TacticalCameraLook implements MouseLookAlgorithm {

	public final Transform following = new Transform();
	private final Camera camera;
	private final Vector3f vCross = new Vector3f();
	private final Vector3f axis = new Vector3f();
	private final Vector2f mouse = new Vector2f();
	private final Vector2f mouseSum = new Vector2f();
	private final Quat4f result = new Quat4f();
	private final Quat4f rotMulView = new Quat4f();
	private final Quat4f rotConj = new Quat4f();
	private final Quat4f newRotation = new Quat4f();
	private final Quat4f totalRotation = new Quat4f(0, 0, 0, 1);
	private final Quat4f tmpQuat = new Quat4f();
	private Transform lookTo;

	public TacticalCameraLook(Camera camera, Transform defaultTransform) {
		this.camera = camera;
		following.setIdentity();
		following.set(defaultTransform);
	}

	@Override
	public void fix() {
	}

	@Override
	public void force(Transform t) {
	}

	@Override
	public void mouseRotate(boolean server, float dx, float dy, float dz, float xSensitivity, float ySensitivity, float zSensibilits) {
		if(Mouse.isGrabbed() && Mouse.isButtonDown(1)) {
			mouse.x = -dx * xSensitivity;
			mouse.y = (EngineSettings.S_MOUSE_ALL_INVERT.isOn() ? -dy : dy) * ySensitivity;
			mouseSum.add(mouse);
			if(mouseSum.y > FastMath.HALF_PI) {
				mouseSum.y -= mouse.y;
				mouse.y = FastMath.HALF_PI - mouseSum.y;
				mouseSum.y = FastMath.HALF_PI;
			}
			if(mouseSum.y < -FastMath.HALF_PI) {
				mouseSum.y -= mouse.y;
				mouse.y = -(FastMath.HALF_PI - Math.abs(mouseSum.y));
				mouseSum.y = -FastMath.HALF_PI;
			}
			Vector3f view = new Vector3f(camera.getForward()); //Vector3f.subtract(targetVector, position);
			Vector3f upVector = new Vector3f(camera.getUp()); //Vector3f.subtract(targetVector, position);
			Vector3f rightVector = new Vector3f(camera.getRight()); //Vector3f.subtract(targetVector, position);
			if(mouse.y != 0) {
				vCross.cross(camera.getForward(), camera.getUp());
				axis.set(vCross);
				axis.normalize();
				rotateCamera(mouse.y, axis, view, upVector, rightVector);
			}
			rotateCamera(mouse.x, new Vector3f(0, 1, 0), view, upVector, rightVector);
			if(lookTo != null) {
				Vector2f planeXZ = new Vector2f(view.x, view.z);
				Vector3f toForward = GlUtil.getForwardVector(new Vector3f(), lookTo);
				Vector2f toPlaneXZ = new Vector2f(toForward.x, toForward.z);
				while(planeXZ.angle(toPlaneXZ) > 0.01) {
					rotateCamera(0.005f, new Vector3f(0, 1, 0), view, upVector, rightVector);
					planeXZ = new Vector2f(view.x, view.z);
					toPlaneXZ = new Vector2f(toForward.x, toForward.z);
				}
				lookTo = null;
			}
			camera.setForward(view);
			camera.setUp(upVector);
			camera.setRight(rightVector);
		}
	}

	@Override
	public void lookTo(Transform n) {
		lookTo = new Transform(n);
	}

	private void rotateCamera(float angle, Vector3f axis, Vector3f forward, Vector3f up, Vector3f right) {
		newRotation.x = axis.x * FastMath.sin(angle / 2);
		newRotation.y = axis.y * FastMath.sin(angle / 2);
		newRotation.z = axis.z * FastMath.sin(angle / 2);
		newRotation.w = FastMath.cos(angle / 2);
		rotConj.conjugate(newRotation);
		rotate(forward);
		rotate(up);
		rotate(right);
		totalRotation.mul(newRotation);
	}

	private void rotate(Vector3f v) {
		tmpQuat.set(v.x, v.y, v.z, 0);
		rotMulView.mul(newRotation, tmpQuat);
		result.mul(rotMulView, rotConj);
		v.set(result.x, result.y, result.z);
	}
}
