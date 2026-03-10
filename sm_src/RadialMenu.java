package org.schema.game.client.view.gui;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.newdawn.slick.UnicodeFont;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.input.InputState;

import javax.vecmath.Vector4f;
import java.util.List;

public class RadialMenu extends GUIElement {

	public static final long MAX_FADE = 160;
	public static final Object2IntOpenHashMap<String> tooltipMenuCount = new Object2IntOpenHashMap<String>();
	private static final long TOOLTIP_TIME_AFTER_MS = 1200;
	private static final long TOOLTIP_TIME_FIRST_MS = 150;
	public final String menuId;
	private final RadialMenuCallback radialMenuCallback;
	private final List<RadialMenuItem<?>> items = new ObjectArrayList<RadialMenuItem<?>>();
	public GUIElement posElem;
	Vector4f deactivated = new Vector4f(0.1334f, 0.1569f, 0.18f, 0.80f);
	Vector4f color = new Vector4f(0.2941f, 0.345f, 0.4039f, 0.80f);
	Vector4f colorSelected = new Vector4f(0.1372f, 0.549f, 0.67f, 0.80f);
	Vector4f highlight = new Vector4f(0.2941f, 0.485f, 0.5439f, 0.92f);
	Vector4f highlightSelected = new Vector4f(0.1372f, 0.549f, 0.67f, 0.92f);
	Vector4f textColor = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
	private int height;
	private int width;
	private float radius;
	private float centerRadius;
	private UnicodeFont font;
	private RadialMenu parentMenu;
	private RadialMenuCenter center;
	private RadialMenu fadingOutMenu;
	private long fadingOutTime;
	private long fadeInTime;
	private boolean forceBackButton;

	public RadialMenu(InputState state, String menuId, RadialMenuCallback diag, int width, int height, int centerRadius, UnicodeFont font) {
		this(state, menuId, diag, null);

		this.height = height;
		this.width = width;
		radius = Math.min(width, height) / 2;
		this.centerRadius = centerRadius;
		this.font = font;


	}

	public RadialMenu(InputState state, String menuId, RadialMenuCallback diag, RadialMenu parent) {
		super(state);
		this.menuId = menuId;
		tooltipMenuCount.add(menuId, 1);
		radialMenuCallback = diag;
		parentMenu = parent;

		center = new RadialMenuCenter(state, this, Lng.str("Back"), null);
		fadeIn();
	}

	public long getToolTipTime() {
		return tooltipMenuCount.get(menuId) < 2 ? TOOLTIP_TIME_FIRST_MS : TOOLTIP_TIME_AFTER_MS;
	}

	public void addItem(Object name, GUICallback callback, GUIActivationCallback activationCallback) {
		addItem(name, callback, activationCallback, true);
	}

	public void addItem(Object name, final GUICallback callback, final GUIActivationCallback activationCallback, boolean activateOnDeactiveRadial) {
		GUICallback cb = callback;
		if(callback != null && activationCallback != null) {
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
		RadialMenuItemText item = new RadialMenuItemText(getState(), this, getTotalSlices(), name, activationCallback, cb);
		item.setActivateOnDeactiveRadial(activateOnDeactiveRadial);
		items.add(item);
	}

	public void addItemBlock(short type, final GUICallback callback, Object toolTip, final GUIActivationCallback activationCallback) {
		GUICallback cb = callback;
		if(callback != null && activationCallback != null) {
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
		RadialMenuItemBlock item = new RadialMenuItemBlock(getState(), this, getTotalSlices(), type, activationCallback, cb);
		item.setToolTip(toolTip);
		items.add(item);
	}

	public RadialMenu addItemAsSubMenu(Object name, GUIActivationCallback activationCallback) {
		RadialMenu m = new RadialMenu(getState(), menuId + "sub-" + items.size(), radialMenuCallback, this);


		RadialMenuItemText item = new RadialMenuItemText(getState(), this, getTotalSlices(), name, activationCallback, null);
		item.setChildMenu(m);
		items.add(item);

		return m;
	}

	public void setFadingOut(RadialMenu fadingOutMenu) {
		fadingOutTime = System.currentTimeMillis();
		this.fadingOutMenu = fadingOutMenu;
	}

	public RadialMenuCenter getCenter() {
		return center;
	}

	public void setCenter(RadialMenuCenter center) {
		this.center = center;
	}

	public int getCenterX() {
		assert (getWidth() > 0);
		return (int) (getWidth() / 2);
	}

	public int getCenterY() {
		return (int) (getHeight() / 2);
	}


	@Override
	public float getHeight() {
		if(parentMenu != null) {
			return parentMenu.getHeight();
		}
		return height;
	}

	@Override
	public float getWidth() {
		if(parentMenu != null) {
			return parentMenu.getWidth();
		}
		assert (width > 0);
		return width;
	}

	@Override
	public void cleanUp() {

	}

	public void fadeIn() {
		fadeInTime = System.currentTimeMillis();
	}

	@Override
	public void draw() {

//		if(parentMenu == null){
		if(posElem != null) {
			setPos((int) (posElem.getPos().x - getWidth() / 2), (int) (posElem.getPos().y - getHeight() / 2), 0);
		} else {
			setPos((int) (GLFrame.getWidth() / 2 - getWidth() / 2), (int) (GLFrame.getHeight() / 2 - getHeight() / 2), 0);
		}
//		}


		GlUtil.glPushMatrix();
		transform();


		long diffIn = System.currentTimeMillis() - fadeInTime;
		if(diffIn < MAX_FADE) {
			float d = ((float) diffIn / MAX_FADE);
//			System.err.println("FADING: "+d);
			drawFading(d);
		} else {
			if(parentMenu != null || forceBackButton) {
				center.draw();
			}
			for(int i = 0; i < items.size(); i++) {
				RadialMenuItem<?> radialMenuItem = items.get(i);
				radialMenuItem.draw();
			}
			for(int i = 0; i < items.size(); i++) {
				RadialMenuItem<?> radialMenuItem = items.get(i);
				radialMenuItem.drawLabel();
			}

			if(fadingOutMenu != null) {
				long diffOut = System.currentTimeMillis() - fadingOutTime;
				if(diffOut < MAX_FADE) {
					float d = 1.0f - ((float) diffOut / MAX_FADE);
					fadingOutMenu.drawFading(d);
				} else {
					fadingOutMenu = null;
				}
			}
		}
		GlUtil.glPopMatrix();
		if(parentMenu == null) {
			GlUtil.glColor4f(1, 1, 1, 1);
		}
	}

	private void drawFading(float d) {

		for(int i = 0; i < items.size(); i++) {
			RadialMenuItem<?> radialMenuItem = items.get(i);
			radialMenuItem.drawFading(d, d, false, false);
		}
		for(int i = 0; i < items.size(); i++) {
			RadialMenuItem<?> radialMenuItem = items.get(i);
			radialMenuItem.drawFadingText(d, d, false, false);
		}
	}

	@Override
	public void onInit() {

	}

	public int getTotalSlices() {
		return items.size();
	}

	public RadialMenuCallback getRadialMenuCallback() {
		return radialMenuCallback;
	}

	public RadialMenu getParentMenu() {
		return parentMenu;
	}

	public void setParentMenu(RadialMenu parentMenu) {
		this.parentMenu = parentMenu;
	}

	public float getRadius() {
		if(parentMenu != null) {
			return parentMenu.getRadius();
		}
		return radius;
	}


	public float getCenterRadius() {
		if(parentMenu != null) {
			return parentMenu.getCenterRadius();
		}
		return centerRadius;
	}


	public UnicodeFont getFont() {
		if(parentMenu != null) {
			return parentMenu.getFont();
		}
		return font;
	}

	public void activateSelected() {
		for(RadialMenuItem<?> item : items) {
			item.activateSelected();
		}
	}

	public boolean isForceBackButton() {
		return forceBackButton;
	}

	public void setForceBackButton(boolean forceBackButton) {
		this.forceBackButton = forceBackButton;
	}


}
