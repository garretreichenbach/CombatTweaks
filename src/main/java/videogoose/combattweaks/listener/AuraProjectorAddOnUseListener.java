package videogoose.combattweaks.listener;

import api.common.GameCommon;
import api.listener.fastevents.CustomAddOnUseListener;
import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.network.objects.Sendable;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

/**
 * Activates a ship's Aura Projector when the player triggers the addon — but only if there's actually a
 * friendly ship in the sector for it to buff, so it doesn't fire (and drain power) for nothing.
 */
public class AuraProjectorAddOnUseListener implements CustomAddOnUseListener {

	@Override
	public void use(Ship entity, ShipManagerContainer managerContainer, Timer timer) {
		if(entity.railController.isDocked()) {
			return;
		}
		AuraProjectorAddOn auraProjectorAddOn = getAddOn(entity);
		if(auraProjectorAddOn != null && auraProjectorAddOn.isPlayerUsable() && !auraProjectorAddOn.isActive()) {
			if(checkForFriendlies(entity)) {
				auraProjectorAddOn.executeModule();
			}
		}
	}

	private boolean checkForFriendlies(Ship entity) {
		try {
			for(Sendable sendable : entity.getState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
				if(sendable instanceof SegmentController) {
					SegmentController segmentController = (SegmentController) sendable;
					if(segmentController.getFactionId() != 0 && entity.getFactionId() != 0 && (GameCommon.getGameState().getFactionManager().isFriend(entity.getFactionId(), segmentController.getFactionId()) || entity.getFactionId() == segmentController.getFactionId())) {
						return true;
					}
				}
			}
		} catch(Exception ignored) {
		}
		return false;
	}

	private AuraProjectorAddOn getAddOn(Ship entity) {
		return SegmentControllerUtils.getAddon(entity, AuraProjectorAddOn.class);
	}
}
