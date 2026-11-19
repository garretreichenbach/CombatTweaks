package videogoose.combattweaks.manager;

import org.schema.game.client.data.GameClientState;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import videogoose.combattweaks.gui.hud.AuraHudIndicator;

import java.util.List;

public class HudManager {

	private static AuraHudIndicator auraHudIndicator;

	public static void initializeHud(GameClientState state, List<GUIElement> elements) {
		elements.add(auraHudIndicator = new AuraHudIndicator(state));
	}
}
