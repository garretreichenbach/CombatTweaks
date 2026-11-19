package videogoose.combattweaks.manager;

import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.newhud.TargetPanel;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import videogoose.combattweaks.gui.hud.AuraHudIndicator;
import videogoose.combattweaks.gui.hud.ShipArmorHPBar;
import videogoose.combattweaks.gui.hud.TargetShipArmorHPBar;

import java.util.List;

public class HudManager {

	private static ShipArmorHPBar shipArmorHPBar;

	private static AuraHudIndicator auraHudIndicator;

	private static TargetShipArmorHPBar targetShipArmorHPBar;

	public static void initializeHud(GameClientState state, List<GUIElement> elements) {
		elements.add(shipArmorHPBar = new ShipArmorHPBar(state));
		elements.add(auraHudIndicator = new AuraHudIndicator(state));
	}

	public static void initializeTargetPanel(TargetPanel panel, List<GUIElement> elements) {
		elements.add(targetShipArmorHPBar = new TargetShipArmorHPBar(panel.getState()));
	}
}
