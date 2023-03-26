package thederpgamer.combattweaks.gui;

import api.utils.gui.GUIInputDialog;
import api.utils.gui.GUIInputDialogPanel;
import org.schema.game.common.data.SegmentPiece;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIContentPane;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDialogWindow;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalArea;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIHorizontalButtonTablePane;
import org.schema.schine.input.InputState;
import thederpgamer.combattweaks.system.AIRemoteControllerModule;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class AIRemoteControllerDialog extends GUIInputDialog {

	private static final int WIDTH = 450;
	private static final int HEIGHT = 300;

	protected final SegmentPiece segmentPiece;
	protected final String name;

	public AIRemoteControllerDialog(SegmentPiece segmentPiece, String name) {
		this.segmentPiece = segmentPiece;
		this.name = name;
	}

	@Override
	public AIRemoteControllerDialogPanel createPanel() {
		return new AIRemoteControllerDialogPanel(getState(), this);
	}

	@Override
	public AIRemoteControllerDialogPanel getInputPanel() {
		return (AIRemoteControllerDialogPanel) super.getInputPanel();
	}

	public class AIRemoteControllerDialogPanel extends GUIInputDialogPanel {
		public AIRemoteControllerDialogPanel(InputState state, GUICallback guiCallback) {
			super(state, "AI_REMOTE_CONTROLLER_DIALOG", "Remote Controller [" + AIRemoteControllerDialog.this.name + "]", "", WIDTH, HEIGHT, guiCallback);
		}

		@Override
		public void onInit() {
			super.onInit();
			GUIContentPane contentPane = ((GUIDialogWindow) background).getMainContentPane();
			contentPane.setTextBoxHeightLast(32);
			GUIHorizontalButtonTablePane buttonPane = new GUIHorizontalButtonTablePane(getState(), 2, 1, contentPane.getContent(0));
			buttonPane.addButton(0, 0, Lng.str("ENTER"), GUIHorizontalArea.HButtonColor.BLUE, new GUICallback() {
				@Override
				public void callback(GUIElement guiElement, MouseEvent event) {
					if(event.pressedLeftMouse() && AIRemoteControllerModule.inRange(segmentPiece.getSegmentController())) {

					}
				}

				@Override
				public boolean isOccluded() {
					return !AIRemoteControllerModule.inRange(segmentPiece.getSegmentController());
				}
			}, new GUIActivationCallback() {
				@Override
				public boolean isVisible(InputState inputState) {
					return true;
				}

				@Override
				public boolean isActive(InputState inputState) {
					return AIRemoteControllerModule.inRange(segmentPiece.getSegmentController());
				}
			});
		}
	}
}