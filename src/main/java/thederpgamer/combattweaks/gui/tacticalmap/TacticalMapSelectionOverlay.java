package thederpgamer.combattweaks.gui.tacticalmap;

import org.newdawn.slick.Color;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.input.InputState;

import javax.vecmath.Vector4f;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [Description]
 *
 * @author TheDerpGamer
 */
public class TacticalMapSelectionOverlay extends GUIAncor {
	private TacticalMapGUIDrawer drawer;
	private GUIElementList entityList;
	private ConcurrentHashMap<Integer, String> selectedEntities;

	public TacticalMapSelectionOverlay(InputState inputState, TacticalMapGUIDrawer drawer) {
		super(inputState);
		this.drawer = drawer;
	}

	@Override
	public void draw() {
		super.draw();
	}

	@Override
	public void onInit() {
		(entityList = new GUIElementList(getState())).onInit();
		attach(entityList);
		selectedEntities = new ConcurrentHashMap<>();
	}

	public void addEntity(SegmentController entity) {
		for(GUIListElement element : entityList) {
			if(element.getContent().getUserPointer().equals(entity.getId())) return;
		}
		GUITextOverlay entityOverlay = new GUITextOverlay(30, 20, getState());
		entityOverlay.onInit();
		entityOverlay.setFont(FontLibrary.FontSize.MEDIUM.getFont());
		entityOverlay.setTextSimple(entity.getRealName());
		entityOverlay.setUserPointer(entity.getId());
		Vector4f color = new Vector4f(Color.white.r, Color.white.g, Color.white.b, 0.0f);
		if(selectedEntities.containsKey(entity.getId())) color.w = 0.5f;
		GUIColoredRectangle rectangle = new GUIColoredRectangle(getState(), entityOverlay.getFont().getWidth(entityOverlay.getText().toString()), 30, color);
		rectangle.setUserPointer(entity.getId());
		rectangle.rounded = 4.0f;
		rectangle.onInit();
		GUITextOverlay selectedOverlay = new GUITextOverlay(30, 20, getState());
		selectedOverlay.onInit();
		selectedOverlay.setFont(FontLibrary.FontSize.MEDIUM.getFont());
		selectedOverlay.setTextSimple(entity.getRealName());
		selectedOverlay.setUserPointer(entity.getId());
		rectangle.attach(selectedOverlay);
		GUIListElement element = new GUIListElement(entityOverlay, rectangle, getState());
		element.onInit();
		entityList.add(element);
	}

	public void removeEntity(SegmentController entity) {
		GUIListElement toRemove = null;
		for(GUIListElement element : entityList) {
			if(element.getContent().getUserPointer().equals(entity.getId())) {
				toRemove = element;
				break;
			}
		}
		if(toRemove != null) entityList.remove(toRemove);
	}

	public void addSelected(SegmentController entity) {
		selectedEntities.put(entity.getId(), entity.getRealName());
	}

	public void removeSelected(SegmentController entity) {
		selectedEntities.remove(entity.getId());
	}

	public void removeAll() {
		selectedEntities.clear();
		entityList.clear();
	}
}