package thederpgamer.combattweaks.manager;

import api.common.GameClient;
import api.listener.events.gui.HudCreateEvent;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SendableSegmentController;
import thederpgamer.combattweaks.gui.hud.GUIJumpMarkerHolder;
import thederpgamer.combattweaks.gui.hud.RepairPateFabricatorHudOverlay;
import thederpgamer.combattweaks.gui.hud.ShipArmorHPBar;
import thederpgamer.combattweaks.gui.hud.TargetShipArmorHPBar;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class HudManager {

	private static GUIJumpMarkerHolder jumpMarkers;
	public static RepairPateFabricatorHudOverlay repairPasteHudOverlay;
	public static ShipArmorHPBar shipArmorHPBar;
	public static TargetShipArmorHPBar targetShipArmorHPBar;

	public static void initialize(HudCreateEvent event) {
		jumpMarkers = new GUIJumpMarkerHolder(event);
		repairPasteHudOverlay = new RepairPateFabricatorHudOverlay(event);

		if(shipArmorHPBar != null) shipArmorHPBar.cleanUp();
		shipArmorHPBar = new ShipArmorHPBar(GameClient.getClientState());
		event.addElement(shipArmorHPBar);

		if(targetShipArmorHPBar != null) {
			event.getHud().getTargetPanel().detach(targetShipArmorHPBar);
			targetShipArmorHPBar.cleanUp();
		}
		(targetShipArmorHPBar = new TargetShipArmorHPBar(GameClient.getClientState())).onInit();
		event.getHud().getTargetPanel().attach(targetShipArmorHPBar);
	}

	public static void addNewIncomingJump(SendableSegmentController controller, Vector3i originalSector, Vector3i newSector) {
		if(jumpMarkers != null) jumpMarkers.addNewIncomingJump(controller, originalSector, newSector);
	}
}
