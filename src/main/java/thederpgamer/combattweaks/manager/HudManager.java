package thederpgamer.combattweaks.manager;

import api.listener.events.gui.HudCreateEvent;
import org.schema.game.common.controller.SendableSegmentController;
import thederpgamer.combattweaks.gui.hud.GUIJumpMarkerHolder;
import thederpgamer.combattweaks.gui.hud.RepairPateFabricatorHudOverlay;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class HudManager {

	private static GUIJumpMarkerHolder jumpMarkers;
	public static RepairPateFabricatorHudOverlay repairPasteHudOverlay;

	public static void initialize(HudCreateEvent event) {
		jumpMarkers = new GUIJumpMarkerHolder(event);
		repairPasteHudOverlay = new RepairPateFabricatorHudOverlay(event);
	}

	public static void addNewIncomingJump(SendableSegmentController controller) {
		if(jumpMarkers != null) jumpMarkers.addNewIncomingJump(controller);
	}

	public static void removeIncomingJump(SendableSegmentController controller) {
		if(jumpMarkers != null) jumpMarkers.removeIncomingJump(controller);
	}
}
