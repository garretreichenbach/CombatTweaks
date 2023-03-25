package thederpgamer.combattweaks.gui.hud;

import api.common.GameClient;
import api.listener.events.gui.HudCreateEvent;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIAncor;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHelperIcon;

import java.lang.reflect.Field;
import java.util.List;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class RepairPateFabricatorHudOverlay extends GUIAncor {
	private GUITextOverlay textOverlay;

	public RepairPateFabricatorHudOverlay(HudCreateEvent event) {
		super(event.getInputState());
	}

	@Override
	public void onInit() {
		super.onInit();
		(textOverlay = new GUITextOverlay(32, 32, FontLibrary.FontSize.MEDIUM, getState())).onInit();
		attach(textOverlay);
	}

	public void updateText(SegmentController segmentController, float current, float max) {
		try {
			if(textOverlay == null) onInit();
//			if(GameClient.getClientState() != null && segmentController.isFullyLoadedWithDock() && PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()).equals(segmentController)) {
//				if(max > 0) {
//					if(GameClient.getClientState().isInFlightMode() && segmentController.getSegmentBuffer().getPointUnsave(segmentController.getSlotAssignment().getAsIndex(GameClient.getClientPlayerState().getCurrentShipControllerSlot())).getType() == ElementKeyMap.REPAIR_CONTROLLER_ID) {
//						textOverlay.setTextSimple(StringTools.formatPointZero(current) + " / " + StringTools.formatPointZero(max));
//						setTextPos(1);
//					} else if(BuildModeDrawer.currentPiece.getType() == ElementManager.getBlock("Repair Paste Fabricator").getId() && !GameClient.getClientState().isInFlightMode()) {
//						textOverlay.setTextSimple("Repair Paste Fabricator: " + StringTools.formatPointZero(current) + " / " + StringTools.formatPointZero(max));
//						setTextPos(2);
//					} else textOverlay.setTextSimple("");
//				}
//			} else textOverlay.setTextSimple("");
		} catch(Exception exception) {
			exception.printStackTrace();
		}
	}

	private void setTextPos(int mode) {
		if(mode == 1) textOverlay.setPos((float) (GLFrame.getWidth() / 2 + 10), ((float) GLFrame.getHeight() / 2 + 100), 0);
		else if(mode == 2) textOverlay.setPos((float) GLFrame.getWidth() / 2 + 10, getMouseYHeight() + 377, 0);
	}

	private float getMouseYHeight() {
		float height = 0;
		try {
			Field queueMouseField = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getHelpManager().getClass().getDeclaredField("queueMouse");
			queueMouseField.setAccessible(true);
			List<HudContextHelperContainer> queueMouse = (List<HudContextHelperContainer>) queueMouseField.get(GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getHelpManager());
			for(HudContextHelperContainer container : queueMouse) {
				try {
					Field iconField = container.getClass().getDeclaredField("icon");
					iconField.setAccessible(true);
					GUIHelperIcon icon = (GUIHelperIcon) iconField.get(container);
					height += icon.getHeight();
				} catch(Exception exception) {
					exception.printStackTrace();
				}
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}
		return height;
	}
}
