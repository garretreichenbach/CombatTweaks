package thederpgamer.combattweaks.listener;

import api.listener.fastevents.CannonProjectileHitListener;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.damage.projectile.ProjectileController;
import org.schema.game.common.controller.damage.projectile.ProjectileHandlerSegmentController;
import org.schema.game.common.controller.damage.projectile.ProjectileParticleContainer;
import org.schema.game.common.data.physics.CubeRayCastResult;
import thederpgamer.combattweaks.calculator.ProjectileCalculator;

import javax.vecmath.Vector3f;
import java.lang.reflect.Field;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class CannonProjectileListener implements CannonProjectileHitListener {
	@Override
	public ProjectileController.ProjectileHandleState handle(Damager damager, ProjectileController projectileController, Vector3f posBeforeUpdate, Vector3f postAfterUpdate, ProjectileParticleContainer projectileParticleContainer, int particleIndex, CubeRayCastResult cubeRayCastResult, ProjectileHandlerSegmentController projectileHandlerSegmentController) {
		try {
			Field shotHandlerField = projectileHandlerSegmentController.getClass().getDeclaredField("shotHandler");
			shotHandlerField.setAccessible(true);
			Object shotHandlerObj = shotHandlerField.get(projectileHandlerSegmentController);
			if(shotHandlerObj instanceof ProjectileHandlerSegmentController.ShotHandler) {
				ProjectileHandlerSegmentController.ShotHandler shotHandler = (ProjectileHandlerSegmentController.ShotHandler) shotHandlerObj;
				shotHandler.forcedResult = ProjectileCalculator.calculate(projectileController, posBeforeUpdate, postAfterUpdate, cubeRayCastResult, shotHandler);
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
		return null; //The return value is ignored, but it's required by the interface
	}

	@Override
	public ProjectileController.ProjectileHandleState handleBefore(Damager damager, ProjectileController projectileController, Vector3f vector3f, Vector3f vector3f1, ProjectileParticleContainer projectileParticleContainer, int i, CubeRayCastResult cubeRayCastResult, ProjectileHandlerSegmentController projectileHandlerSegmentController) {
		return null;
	}

	@Override
	public ProjectileController.ProjectileHandleState handleAfterIfNotStopped(Damager damager, ProjectileController projectileController, Vector3f vector3f, Vector3f vector3f1, ProjectileParticleContainer projectileParticleContainer, int i, CubeRayCastResult cubeRayCastResult, ProjectileHandlerSegmentController projectileHandlerSegmentController) {
		return null;
	}

	@Override
	public void handleAfterAlways(Damager damager, ProjectileController projectileController, Vector3f vector3f, Vector3f vector3f1, ProjectileParticleContainer projectileParticleContainer, int i, CubeRayCastResult cubeRayCastResult, ProjectileHandlerSegmentController projectileHandlerSegmentController) {
	}
}
