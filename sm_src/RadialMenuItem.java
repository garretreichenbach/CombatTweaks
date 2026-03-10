package org.schema.game.client.view.gui;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import org.lwjgl.opengl.GL11;
import org.schema.common.FastMath;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationHighlightCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.input.InputState;

public abstract class RadialMenuItem<E extends GUIElement> extends GUIElement{
	protected final RadialMenu m;
	private int index;
	
	
	private E label;
	private GUITextOverlay tooltip;
	
	private boolean activateOnDeactiveRadial = true;

	private RadialMenu childMenu;
	private GUIActivationCallback activationCallback;
	private boolean subMenuParent;
	
	
	public abstract E getLabel();
	public RadialMenuItem(InputState state, RadialMenu m, int index, final GUIActivationCallback activationCallback, final GUICallback callback) {
		super(state);
		this.m = m;
		this.index = index;
		
		
		
		this.activationCallback = activationCallback;
		this.tooltip = new GUITextOverlay(30, 30, FontLibrary.getBlenderProHeavy20(), state);
		if(callback != null){
			GUICallback cb = callback;
			if(activationCallback != null){
				cb = new GUICallback() {
					
					@Override
					public boolean isOccluded() {
						return !activationCallback.isActive(getState()) || callback.isOccluded();
					}
					
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						callback.callback(callingGuiElement, event);
					}
				};
			}
			setCallback(cb);
		}else{
			this.subMenuParent = true;
			setCallback(new ParentCallback());
		}
		setMouseUpdateEnabled(true);
	}
	
	public void setToolTip(Object text){
		tooltip.setTextSimple(text);
	}
	private class ParentCallback implements GUICallback{

		@Override
		public void callback(GUIElement callingGuiElement, MouseEvent event) {
			if(event.pressedLeftMouse()){
				if(childMenu != null){
					m.getRadialMenuCallback().menuChanged(childMenu);
					m.getRadialMenuCallback().menuDeactivated(m);
					
				}
			}else if(event.pressedRightMouse()){
				if(m.getParentMenu() != null){
					m.getRadialMenuCallback().menuChanged(m.getParentMenu());
					m.getRadialMenuCallback().menuDeactivated(m);
				}
			}
				
		}

		@Override
		public boolean isOccluded() {
			return (activationCallback != null && !activationCallback.isActive(getState()));
		}
		
	}
	
	public Vector4f getColorCurrent(Vector4f color){
		if(isActive()){
			if(isInside()){
				clrTmp.set(isHightlighted() ? m.highlightSelected : m.colorSelected);
				return clrTmp;
			}else{
				clrTmp.set(isHightlighted() ? m.highlight : m.color);
				return clrTmp;
			}
		}else{
			clrTmp.set(m.deactivated);
			return clrTmp;
		}
	}
	@Override
	public boolean isActive() {
		return super.isActive() && (activationCallback == null || activationCallback.isActive(getState()));
	}

	public boolean isHightlighted(){
		return activationCallback != null && activationCallback instanceof GUIActivationHighlightCallback && ((GUIActivationHighlightCallback)activationCallback).isHighlighted(getState());
	}

	
	
	private float margin = 0.01f;
	private int list;
	private float lastRad;
	private float lastCenRad;
	private int lastTotSlices;
	private float radScale = 1.0f;
	private float alpha = 1.0f;
	protected Vector4f clrTmp = new Vector4f();
	private long lastInside;
	private long insideDuration;

	
	
	
	private Vector2f getStartSector(){
		return getSector(index, margin);
	}
	private Vector2f getEndSector(){
		return getSector(index+1,-margin);
	}
	
	private float getRadFract(int index, float margin, float radiusRelation){
		float fract = (float)index / (float)m.getTotalSlices() ; 
		float radFract = fract * FastMath.TWO_PI + (margin * radiusRelation);
		return radFract;
	}
	private Vector2f getSector(int index, float margin){
		Vector2f s = new Vector2f();
		
		float radFract = getRadFract(index, margin, 1.0f);
		
		s.x = FastMath.cos(radFract) * getRadius();
		s.y = FastMath.sin(radFract) * getRadius();
		
		return s;
	}
	@Override
	public void cleanUp() {
	}
	public void drawFading(float d, float alpha, boolean text, boolean mouseCHeck) {
		this.alpha = alpha;
		this.radScale = d;
		if(list == 0 || getRadius() != lastRad || getCenterRadius() != lastCenRad || m.getTotalSlices() != lastTotSlices){
			recalcLists();
		}
		GlUtil.glPushMatrix();
		transform();
		getColorCurrent(clrTmp);
		clrTmp.w *= this.alpha;
		GlUtil.glColor4f(clrTmp);
		
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glCallList(list);
		
		if(text){
			drawText();
		}
		if(mouseCHeck){
			checkMouseInside();
		}
		
		GlUtil.glPopMatrix();
		
		this.radScale = 1.0f;
		this.alpha = 1.0f;
	}
	public void drawFadingText(float d, float alpha, boolean text, boolean mouseCHeck) {
		this.alpha = alpha;
		this.radScale = d;
		GlUtil.glPushMatrix();
		transform();
		getColorCurrent(clrTmp);
		clrTmp.w *= this.alpha;
		GlUtil.glColor4f(clrTmp);
		
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		
		if(text){
			drawText();
		}
		GlUtil.glPopMatrix();
		
		this.radScale = 1.0f;
		this.alpha = 1.0f;
	}
	
	@Override
	public void draw() {
		
		if(this.label == null){
			this.label = getLabel();
		}
		
		if(list == 0 || getRadius() != lastRad || getCenterRadius() != lastCenRad || m.getTotalSlices() != lastTotSlices){
			recalcLists();
		}
		
		GlUtil.glPushMatrix();
		transform();
		getColorCurrent(clrTmp);
		clrTmp.w *= this.alpha;
		GlUtil.glColor4f(clrTmp);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glCallList(list);
		
		
		drawText();
		
		checkMouseInside();

		if(isInside()){
			if(lastInside != 0){
				long dur = System.currentTimeMillis() - lastInside;
				insideDuration += Math.max(1, dur);
			}
			lastInside = System.currentTimeMillis();
		}else{
			insideDuration = 0;
			lastInside = 0;
		}
		
		GlUtil.glPopMatrix();
		
		
	}
	public void drawLabel() {
		
		if(this.label == null){
			this.label = getLabel();
		}
		
		GlUtil.glPushMatrix();
		transform();
		getColorCurrent(clrTmp);
		clrTmp.w *= this.alpha;
		GlUtil.glColor4f(clrTmp);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		
		
		drawText();

		if(insideDuration > m.getToolTipTime()){
			
			drawToolTip();
		}
		
		checkMouseInside();
		
		
		
		
		GlUtil.glPopMatrix();
	}
	private float getRadius(){
		return radScale * m.getRadius();
	}
	private float getCenterRadius(){
		return radScale * m.getCenterRadius();
	}
	private void drawText() {
		float start = getRadFract(index, margin, 1.0f);
		float end = getRadFract(index+1, -margin, 1.0f);
		
		float half = (end - start) / 2;
		
		float cos = FastMath.cos(start+half);
		float sin = FastMath.sin(start+half);
		
		float x = cos * getRadius() * 0.766f;
		float y = sin * getRadius() * 0.766f;
		
		setColorAndPos(label, x, y, getColorCurrent(clrTmp));
		
		label.draw();
	}
	private void drawToolTip() {
		float start = getRadFract(index, margin, 1.0f);
		float end = getRadFract(index+1, -margin, 1.0f);
		
		float half = (end - start) / 2;
		
		float cos = FastMath.cos(start+half);
		float sin = FastMath.sin(start+half);
		
		float x = cos * getRadius() * 0.766f ;
		float y = sin * getRadius() * 0.766f - 40;
		
		tooltip.getColor().a = getColorCurrent(clrTmp).w;
		
		tooltip.setPos(
				(int)(getCenterX() + x - label.getWidth() / 2), 
				(int)(getCenterY() + y - label.getHeight()/2), 0); 
		tooltip.setColor(m.textColor);	
		tooltip.draw();
	}
	
	protected abstract void setColorAndPos(E label, float x, float y, Vector4f colorCurrent);
	@Override
	public void onInit() {
		
	}
	
	private void recalcLists(){
		int parts;
		if(m.getTotalSlices() >= 8){
			parts = 24;
		}else{
			parts = (int)((8f / (Math.max(1, m.getTotalSlices())))*24f);
		}
		FloatArrayList outer = new FloatArrayList(parts);
		FloatArrayList inner = new FloatArrayList(parts);
		
		
		
		float start = getRadFract(index, margin, (getCenterRadius() > 0 ? (getRadius()/getCenterRadius()) : 1.0f));
		float end = getRadFract(index+1, -margin, (getCenterRadius() > 0 ? (getRadius()/getCenterRadius()) : 1.0f));
		float startOuter = getRadFract(index, margin, 1.0f);
		float endOuter = getRadFract(index+1, -margin, 1.0f);
		float prt = (end - start) / parts;
		float prtOuter = (endOuter - startOuter) / parts;
		float frBase = 0;
		float frBaseOuter = 0;
		for (int c = 0; c < parts+1; c++) {
			float fr = start+frBase;
			float frOuter = startOuter+frBaseOuter;
			
			float cos = FastMath.cos(fr);
			float sin = FastMath.sin(fr);
			
			float cosOuter = FastMath.cos(frOuter);
			float sinOuter = FastMath.sin(frOuter);
			
			

			inner.add(cos * getCenterRadius());
			inner.add(sin * getCenterRadius());
			
			outer.add(cosOuter * getRadius());
			outer.add(sinOuter * getRadius());
			
			frBase += prt;
			frBaseOuter += prtOuter;
		}
		
		if(list == 0){
			list = GL11.glGenLists(1);
		}

		GL11.glNewList(list, GL11.GL_COMPILE);
		GL11.glBegin(GL11.GL_QUADS);
		
		for(int i = 0; i < parts; i++){
			float aX0 = outer.get(i*2);
			float aY0 = outer.get(i*2+1);
			
			float bX0 = inner.get(i*2);
			float bY0 = inner.get(i*2+1);
			
			float aX1 = outer.get((i+1)*2);
			float aY1 = outer.get((i+1)*2+1);
			
			float bX1 = inner.get((i+1)*2);
			float bY1 = inner.get((i+1)*2+1);
			
			
			
			
			GL11.glVertex2f(m.getCenterX()+bX0, m.getCenterY()+bY0);
			GL11.glVertex2f(m.getCenterX()+bX1, m.getCenterY()+bY1);
			GL11.glVertex2f(m.getCenterX()+aX1, m.getCenterY()+aY1);
			GL11.glVertex2f(m.getCenterX()+aX0, m.getCenterY()+aY0);
			
			
//			System.err.println((m.getCenterX()+bX0)+" ;;;;;; "+(m.getCenterY()+bY0));
		}
		
		GL11.glEnd();

		GL11.glEndList();
		
		lastRad = getRadius();
		lastCenRad = getCenterRadius();
		lastTotSlices = m.getTotalSlices(); 
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
	private static boolean areClockwise(Vector2f v1, Vector2f v2) {
		return -v1.x*v2.y + v1.y*v2.x > 0;
	}
	private static boolean  isOutofRadius(Vector2f v, float radiusSquared) {
		return v.x*v.x + v.y*v.y > radiusSquared;
	}
	
	@Override
	protected boolean isCoordsInside(Vector3f relMousePos, float scaleX, float scaleY){
		
		
		
		int relFromCenterX = (int) (relMousePos.x - getCenterX());
		int relFromCenterY = (int) (relMousePos.y - getCenterY());
		
		Vector2f relPoint = new Vector2f(relFromCenterX, relFromCenterY);
		Vector2f sectorStart = getStartSector();
		Vector2f sectorEnd = getEndSector();
		
		
		return (m.getTotalSlices() == 1 || (!areClockwise(sectorStart, relPoint) &&
		         areClockwise(sectorEnd, relPoint))) &&
//		         isWithinRadius(relPoint, getRadius() * getRadius()) && 
		         isOutofRadius(relPoint, getCenterRadius() * getCenterRadius())
		         ;
		
	}





	public RadialMenu getChildMenu() {
		return childMenu;
	}


	public void setChildMenu(RadialMenu childMenu) {
		this.childMenu = childMenu;
	}
	public void activateSelected() {
		if(isActivateOnDeactiveRadial() && !subMenuParent && isActive() && getCallback() != null && !getCallback().isOccluded() && isInside()){
			MouseEvent e = new MouseEvent();
			e.button = 0;
			e.state = true;
			getCallback().callback(this, e);
		}
	}
	public boolean isActivateOnDeactiveRadial() {
		return activateOnDeactiveRadial;
	}
	public void setActivateOnDeactiveRadial(boolean activateOnDeactiveRadial) {
		this.activateOnDeactiveRadial = activateOnDeactiveRadial;
	}
	
}
