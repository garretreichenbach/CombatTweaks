package org.schema.game.client.view.gui;



import org.schema.game.client.controller.PlayerInput;
import org.schema.game.client.data.GameClientState;
import org.schema.schine.graphicsengine.core.MouseEvent;

public abstract class RadialMenuDialog extends PlayerInput implements RadialMenuCallback{

	private RadialMenu gui;
	
	public RadialMenuDialog(GameClientState state) {
		super(state);
		
	}

	public abstract RadialMenu createMenu(RadialMenuDialog radialMenuDialog);
	
	@Override
	public void handleMouseEvent(MouseEvent e) {
	}

	@Override
	public RadialMenu getInputPanel() {
		if(gui == null){
			this.gui = createMenu(this);
		}
		return gui;
	}

	@Override
	public void onDeactivate() {
		
	}

	@Override
	public void menuChanged(RadialMenu menu) {
		if(menu == null){
			System.err.println("[CLIENT] RADIAL MENU CLOSE REQUESTED");
			deactivate();
		}else{
			gui = menu;
			gui.fadeIn();
		}
	}

	@Override
	public void menuDeactivated(RadialMenu menu) {
		gui.setFadingOut(menu);
		
	}

	public void activateSelected() {
		gui.activateSelected();
	}

}
