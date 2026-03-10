package org.schema.game.client.view.gui;


import javax.vecmath.Vector4f;

import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.input.InputState;

public class RadialMenuItemText extends RadialMenuItem<GUITextOverlay>{

	private Object lName;
	
	public RadialMenuItemText(InputState state, RadialMenu m, int index, Object name, final GUIActivationCallback activationCallback, final GUICallback callback) {
		super(state, m, index, activationCallback, callback);
		this.lName = name;
	}

	@Override
	protected void setColorAndPos(GUITextOverlay label, float x, float y, Vector4f colorCurrent) {
		label.getColor().a = getColorCurrent(clrTmp).w;
		
		label.setPos(
				(int)(getCenterX() + x - label.getMaxLineWidth() / 2), 
				(int)(getCenterY() + y - label.getTextHeight()/2), 0); 
		label.setColor(m.textColor);		
	}

	@Override
	public GUITextOverlay getLabel() {
		GUITextOverlay label = new GUITextOverlay(10, 10, m.getFont(), getState());
		label.setTextSimple(lName);
		return label;
	}
	
}
