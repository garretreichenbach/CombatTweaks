package thederpgamer.combattweaks.manager;

import api.listener.Listener;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.gui.HudCreateEvent;
import api.mod.StarLoader;
import thederpgamer.combattweaks.CombatTweaks;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class EventManager {
	public static void initialize(CombatTweaks instance) {
		StarLoader.registerListener(HudCreateEvent.class, new Listener<HudCreateEvent>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				HudManager.initialize(event);
			}
		}, instance);

		StarLoader.registerListener(ShipJumpEngageEvent.class, new Listener<ShipJumpEngageEvent>() {
			@Override
			public void onEvent(ShipJumpEngageEvent event) {
				JumpHandler.onJumpEngage(event);
			}
		}, instance);
	}
}
