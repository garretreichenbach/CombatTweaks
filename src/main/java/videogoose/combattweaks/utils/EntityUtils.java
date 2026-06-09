package videogoose.combattweaks.utils;

import api.common.GameCommon;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.Planet;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.SpaceStation;
import org.schema.game.common.controller.elements.ManagerContainer;

import javax.vecmath.Vector3f;

public class EntityUtils {

	/** Straight-line world-space distance between two entities' origins (same-sector use). */
	public static float getDistance(SegmentController entityA, SegmentController entityB) {
		Transform transformA = new Transform(entityA.getWorldTransform());
		Transform transformB = new Transform(entityB.getWorldTransform());
		Vector3f posA = transformA.origin;
		Vector3f posB = transformB.origin;
		return Math.abs(Vector3fTools.distance(posA.x, posA.y, posA.z, posB.x, posB.y, posB.z));
	}

	public static SegmentController getEntityById(int id) {
		try {
			return (SegmentController) GameCommon.getGameObject(id);
		} catch(Exception ignored) {
			return null;
		}
	}

	public static ManagerContainer<?> getManagerContainer(SegmentController entity) {
		if(entity instanceof Ship) {
			return ((Ship) entity).getManagerContainer();
		} else if(entity instanceof SpaceStation) {
			return ((SpaceStation) entity).getManagerContainer();
		} else if(entity instanceof Planet) {
			return ((Planet) entity).getManagerContainer();
		} else {
			return null;
		}
	}
}
