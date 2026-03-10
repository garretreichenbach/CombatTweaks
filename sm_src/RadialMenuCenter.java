package org.schema.game.client.view.gui;


import org.lwjgl.opengl.GL11;
import org.schema.common.FastMath;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.input.InputState;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

public class RadialMenuCenter extends GUIElement{
	private RadialMenu m;
	
	
	private GUITextOverlay label;
	
	
	private GUIActivationCallback activationCallback;
	
	public RadialMenuCenter(InputState state, RadialMenu m, Object name, final GUIActivationCallback activationCallback) {
		super(state);
		this.m = m;
		label = new GUITextOverlay(10, 10, m.getFont(), state);
		label.setTextSimple(name);
		this.activationCallback = activationCallback;
		setCallback(new ParentCallback());
		setMouseUpdateEnabled(true);
	}

	public RadialMenuCenter(InputState state, RadialMenu m, Object name, GUICallback callback, GUIActivationCallback activationCallback) {
		super(state);
		this.m = m;
		label = new GUITextOverlay(10, 10, m.getFont(), state);
		label.setTextSimple(name);
		this.activationCallback = activationCallback;
		setCallback(callback);
		setMouseUpdateEnabled(true);
	}
	
	private class ParentCallback implements GUICallback{

		@Override
		public void callback(GUIElement callingGuiElement, MouseEvent event) {
			if(event.pressedLeftMouse()){
				if(m.getParentMenu() != null){
					m.getRadialMenuCallback().menuChanged(m.getParentMenu());
					m.getRadialMenuCallback().menuDeactivated(m);
				}else{
					m.getRadialMenuCallback().menuChanged(null);
					m.getRadialMenuCallback().menuDeactivated(m);
				}
			}
				
		}

		@Override
		public boolean isOccluded() {
			return (activationCallback != null && !activationCallback.isActive(getState()));
		}
		
	}
	
	public Vector4f getColorCurrent(){
		if(isActive()){
			if(isInside()){
				return isHightlighted() ? m.highlightSelected : m.colorSelected;
			}else{
				return isHightlighted() ? m.highlight : m.color;
			}
		}else{
			return m.deactivated;
		}
	}
	@Override
	public boolean isActive() {
		return super.isActive() && (activationCallback == null || activationCallback.isActive(getState()));
	}

	public boolean isHightlighted(){
		return activationCallback != null && activationCallback instanceof GUIActivationHighlightCallback && ((GUIActivationHighlightCallback)activationCallback).isHighlighted(getState());
	}

	
	
	private float margin = 8f;
	private int list;
	private float lastRad;
	private float lastCenRad;
	
	

	
	@Override
	public void cleanUp() {
	}
	@Override
	public void draw() {
		if(list == 0 || m.getRadius() != lastRad || m.getCenterRadius() != lastCenRad ){
			
			recalcLists();
		}
		GlUtil.glPushMatrix();
		transform();
		GlUtil.glColor4f(getColorCurrent());
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glCallList(list);
		
		drawText();
		
		checkMouseInside();
		
		
		GlUtil.glPopMatrix();
	}
	private void drawText() {
	
		
		label.setPos(
				getCenterX() - label.getMaxLineWidth() / 2, 
				getCenterY() - label.getTextHeight()/2, 0); 
		label.setColor(m.textColor);
		label.draw();
	}
	@Override
	public void onInit() {
		
	}
	
	private void recalcLists(){
		int parts = 24;
		
		if(list == 0){
			list = GL11.glGenLists(1);
		}

		GL11.glNewList(list, GL11.GL_COMPILE);
		GL11.glBegin(GL11.GL_TRIANGLE_FAN);
		float rad = 0;
		GL11.glVertex2f(m.getCenterX(), m.getCenterY());
		for(int i = 0; i <= parts; i++){
			
			GL11.glVertex2f(
					m.getCenterX() + FastMath.cos(rad) * (m.getCenterRadius() - margin),
					m.getCenterY() +  FastMath.sin(rad) * (m.getCenterRadius() - margin));
			
			
			rad -= FastMath.TWO_PI / parts;
		}
		
		GL11.glEnd();

		GL11.glEndList();
		
		lastRad = m.getRadius();
		lastCenRad = m.getCenterRadius();
	}
	
	@Override
	public float getHeight() {
		return m.getHeight();
	}
	@Override
	public float getWidth() {
		return m.getWidth();
	}
	public int getCenterX(){
		return m.getCenterX();
	}
	public int getCenterY(){
		return m.getCenterY();
	}
	private static boolean  isWithinRadius(Vector2f v, float radiusSquared) {
		return v.x*v.x + v.y*v.y <= radiusSquared;
	}
	
	@Override
	protected boolean isCoordsInside(Vector3f relMousePos, float scaleX, float scaleY){
		
		
		
		int relFromCenterX = (int) (relMousePos.x - getCenterX());
		int relFromCenterY = (int) (relMousePos.y - getCenterY());
		
		Vector2f relPoint = new Vector2f(relFromCenterX, relFromCenterY);
		
		
		
		return   isWithinRadius(relPoint, (m.getCenterRadius()-margin) * (m.getCenterRadius()-margin));
		
	}





	
}
