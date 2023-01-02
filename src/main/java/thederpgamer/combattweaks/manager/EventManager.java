package thederpgamer.combattweaks.manager;

import api.listener.Listener;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.listener.events.gui.HudCreateEvent;
import api.listener.events.register.ManagerContainerRegisterEvent;
import api.listener.fastevents.FastListenerCommon;
import api.mod.StarLoader;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.listener.ShipAIShootListener;
import thederpgamer.combattweaks.system.RepairPasteFabricatorSystem;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class EventManager {

	public static ShipAIShootListener shipAIShootListener;

	public static void initialize(CombatTweaks instance) {
		FastListenerCommon.shipAIEntityAttemptToShootListeners.add(shipAIShootListener = new ShipAIShootListener());

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

		StarLoader.registerListener(ManagerContainerRegisterEvent.class, new Listener<ManagerContainerRegisterEvent>() {
			@Override
			public void onEvent(ManagerContainerRegisterEvent event) {
				event.addModMCModule(new RepairPasteFabricatorSystem(event.getSegmentController(), event.getContainer()));
			}
		}, instance);
	}
}
