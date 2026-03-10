package videogoose.combattweaks.utils;

import api.common.GameCommon;
import org.schema.game.common.controller.Planet;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.SpaceStation;
import org.schema.game.common.controller.elements.ManagerContainer;

public class EntityUtils {

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
