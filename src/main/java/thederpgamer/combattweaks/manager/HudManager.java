package thederpgamer.combattweaks.manager;

import api.listener.events.gui.HudCreateEvent;
import api.listener.events.gui.TargetPanelCreateEvent;
import thederpgamer.combattweaks.gui.hud.ShipArmorHPBar;
import thederpgamer.combattweaks.gui.hud.TargetShipArmorHPBar;

public class HudManager {

	private static ShipArmorHPBar shipArmorHPBar;

	private static TargetShipArmorHPBar targetShipArmorHPBar;

	public static void initializeHud(HudCreateEvent event) {
		event.elements.add(shipArmorHPBar = new ShipArmorHPBar(event.getInputState()));
	}

	public static void initializeTargetPanel(TargetPanelCreateEvent event) {
		event.getCustomElements().add(targetShipArmorHPBar = new TargetShipArmorHPBar(event.getTargetPanel().getState()));
	}
}
