package videogoose.combattweaks.listener;

import api.event.systems.CustomAddOnUseListener;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.ShipManagerContainer;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

/**
 * Lets AI-controlled ships activate their Aura Projector (fired from {@code ShipAIEntity}'s update), but only when
 * something in range qualifies for the aura — friends for Support, enemies for Offense — so it doesn't fire (and
 * drain power) for nothing.
 */
public class AuraProjectorAddOnUseListener implements CustomAddOnUseListener {

	@Override
	public void use(Ship entity, ShipManagerContainer managerContainer, Timer timer) {
		if(entity.railController.isDocked()) {
			return;
		}
		// getActiveAura matches by instanceof; SegmentControllerUtils.getAddon matches the exact (abstract) class and never hits.
		AuraProjectorAddOn aura = AuraProjectorAddOn.getActiveAura(entity);
		if(aura != null) {
			aura.tryAiActivate();
		}
	}
}
