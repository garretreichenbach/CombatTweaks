package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import api.utils.draw.ModWorldDrawer;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.schema.common.util.ByteUtil;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.gui.PlayerPanel;
import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.BoundingBox;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.input.InputType;
import org.schema.schine.input.KeyboardMappings;
import videogoose.combattweaks.CombatTweaks;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TacticalMapGUIDrawer extends ModWorldDrawer {

	private static final FloatBuffer GL_MODELVIEW = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer GL_PROJECTION = BufferUtils.createFloatBuffer(16);
	private static final IntBuffer GL_VIEWPORT = BufferUtils.createIntBuffer(16);
	private static final FloatBuffer GL_WIN_COORDS = BufferUtils.createFloatBuffer(3);
	// Path colors for dotted line rendering — bright colors with full opacity
	private static final Vector4f PATH_RED = new Vector4f(1.0f, 0.2f, 0.2f, 1.0f);     // bright red for targeting
	private static final Vector4f PATH_CYAN = new Vector4f(0.2f, 1.0f, 1.0f, 1.0f);    // bright cyan for movement
	private static final Vector4f PATH_GREEN = new Vector4f(0.2f, 1.0f, 0.2f, 1.0f);   // bright green for defend
	private static final Vector4f PATH_ORANGE = new Vector4f(1.0f, 0.6f, 0.0f, 1.0f);  // orange for mining
	private static final Vector4f PATH_MAGENTA = new Vector4f(1.0f, 0.2f, 0.8f, 1.0f); // magenta for repair
	// Bounding box wireframe colors — solid pass (depth tested) and occluded pass (through geometry)
	private static final Vector4f OUTLINE_SELECTED = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);   // yellow, solid
	private static final Vector4f OUTLINE_SELECTED_OCCLUDED = new Vector4f(1.0f, 1.0f, 0.0f, 0.15f);
	private static final Vector4f OUTLINE_HOVERED = new Vector4f(1.0f, 1.0f, 1.0f, 0.6f);    // white, slightly transparent
	private static final Vector4f OUTLINE_HOVERED_OCCLUDED = new Vector4f(1.0f, 1.0f, 1.0f, 0.08f);
	private static final Vector4f OUTLINE_TURRET = new Vector4f(0.0f, 1.0f, 1.0f, 1.0f);     // cyan, solid
	private static final Vector4f OUTLINE_TURRET_OCCLUDED = new Vector4f(0.0f, 1.0f, 1.0f, 0.15f);
	private static TacticalMapGUIDrawer instance;
	public final int sectorSize;
	public final float maxDrawDistance;
	public final Vector3f labelOffset;
	public final ConcurrentHashMap<Integer, TacticalMapEntityIndicator> drawMap;
	public final ConcurrentLinkedQueue<SegmentController> selectedEntities = new ConcurrentLinkedQueue<>();
	public final ConcurrentHashMap<Integer, Ship> selectedTurrets = new ConcurrentHashMap<>(); // entity ID → turret Ship
	private final KeyboardMappings tacticalMapMapping;
	// Reusable temporaries for dotted line math
	private final Vector3f dottedDir = new Vector3f();
	private final Vector3f dottedDirN = new Vector3f();
	private final Vector3f dottedA = new Vector3f();
	private final Vector3f dottedB = new Vector3f();
	public float selectedRange;
	public TacticalMapControlManager controlManager;
	public TacticalMapCamera camera;
	public boolean toggleDraw;
	/** The indicator currently under the mouse cursor, updated each frame. May be null. */
	public TacticalMapEntityIndicator hoveredIndicator;
	// Temporary storage for bbox corner projection
	private static final float[] BBOX_XS = {0, 0, 0, 0, 1, 1, 1, 1};
	private static final float[] BBOX_YS = {0, 0, 1, 1, 0, 0, 1, 1};
	private HudContextHelpManager hud;
	private boolean initialized;
	private boolean firstTime = true;
	private long updateTimer;
	private TacticalMapShaderOverlay shaderOverlay;
	private int lastKnownWidth = -1;
	private int lastKnownHeight = -1;

	public TacticalMapGUIDrawer() {
		instance = this;
		toggleDraw = false;
		initialized = false;
		sectorSize = (int) ServerConfig.SECTOR_SIZE.getCurrentState();
		maxDrawDistance = sectorSize * 4.0f;
		labelOffset = new Vector3f(0.0f, -20.0f, 0.0f);
		drawMap = new ConcurrentHashMap<>();
		updateTimer = 150;
		tacticalMapMapping = getMappingFromName("OPEN_TACTICAL_MAP");
	}

	public static TacticalMapGUIDrawer getInstance() {
		return instance;
	}

	private KeyboardMappings getMappingFromName(String name) {
		for(KeyboardMappings mapping : KeyboardMappings.values()) {
			if(mapping.name().equals(name)) {
				return mapping;
			}
		}
		return null;
	}

	public void addSelection(TacticalMapEntityIndicator indicator) {
		selectedEntities.add(indicator.getEntity());
		if(shaderOverlay != null) {
			shaderOverlay.addSelected(indicator.getEntity());
		}
	}

	public void removeSelection(TacticalMapEntityIndicator indicator) {
		selectedEntities.remove(indicator.getEntity());
		if(shaderOverlay != null) {
			shaderOverlay.removeSelected(indicator.getEntity());
		}
	}

	public void removeAll() {
		clearSelected();
	}

	public void toggleSelectAllFriendly() {
		clearSelected();
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(indicator.getEntity() != null && indicator.getEntity().getFactionId() == GameClient.getClientPlayerState().getFactionId() && GameClient.getClientPlayerState().getFactionId() != 0 && !indicator.getEntity().isDocked()) {
				if(isSelected(indicator.getEntity())) {
					removeSelection(indicator);
				} else {
					addSelection(indicator);
				}
			}
		}
	}

	public boolean isSelected(SegmentController entity) {
		return selectedEntities.contains(entity);
	}

	public void addTurretSelection(Ship turret) {
		selectedTurrets.put(turret.getId(), turret);
		if(shaderOverlay != null) {
			shaderOverlay.addSelectedTurret(turret);
		}
	}

	public void removeTurretSelection(Ship turret) {
		selectedTurrets.remove(turret.getId());
		if(shaderOverlay != null) {
			shaderOverlay.removeSelectedTurret(turret);
		}
	}

	public void clearSelectedTurrets() {
		selectedTurrets.clear();
		if(shaderOverlay != null) {
			shaderOverlay.clearSelectedTurrets();
		}
	}

	public boolean isTurretSelected(Ship turret) {
		return selectedTurrets.containsKey(turret.getId());
	}

	public void toggleDraw() {
		if(!initialized) {
			onInit();
		}
		if(!shouldDraw()) {
			toggleDraw = false;
		} else {
			toggleDraw = !toggleDraw;
		}
		if(toggleDraw) {
			if(camera != null) {
				Controller.setCamera(camera);
			}
			controlManager.onSwitch(true);
			if(firstTime) {
				camera.reset();
				firstTime = false;
			}
		} else {
			if(camera != null) {
				Controller.setCamera(getDefaultCamera());
			}
			controlManager.onSwitch(false);
		}
	}

	public Camera getDefaultCamera() {
		if(GameClient.getClientState().isInAnyStructureBuildMode()) {
			return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getInShipControlManager().getShipControlManager().getSegmentBuildController().getShipBuildCamera();
		} else if(GameClient.getClientState().isInFlightMode()) {
			return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().shipCamera;
		} else {
			return Controller.getCamera();
		}
	}

	@Override
	public void update(Timer timer) {
		if(!toggleDraw || !(Controller.getCamera() instanceof TacticalMapCamera)) {
			return;
		}
		controlManager.update(timer);
		updateTimer--;
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			indicator.update(timer);
		}
		if(updateTimer <= 0) {
			for(SimpleTransformableSendableObject<?> object : GameClient.getClientState().getCurrentSectorEntities().values()) {
				if(object instanceof SegmentController && !((SegmentController) object).isDocked() && !drawMap.containsKey(object.getId())) {
					drawMap.put(object.getId(), new TacticalMapEntityIndicator((SegmentController) object));
				}
			}
			updateTimer = 150;
		}
	}

	public boolean shouldDraw() {
		return (GameClient.getClientState().getPlayerInputs().isEmpty() || GameClient.getClientState().getController().isChatActive() || GameClient.getClientState().isInAnyStructureBuildMode() || GameClient.getClientState().isInFlightMode()) && !GameClient.getClientState().getWorldDrawer().getGameMapDrawer().isMapActive();
	}
	private static final float[] BBOX_ZS = {0, 1, 0, 1, 0, 1, 0, 1};

	private void drawGrid(float start, float spacing) {
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPushMatrix();
		float aspect = (float) GLFrame.getWidth() / GLFrame.getHeight();
		GlUtil.gluPerspective(Controller.projectionMatrix, (Float) EngineSettings.G_FOV.getCurrentState(), aspect, 10, 25000, true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		Vector3i selectedPos = new Vector3i();
		selectedPos.x = ByteUtil.modU16(selectedPos.x);
		selectedPos.y = ByteUtil.modU16(selectedPos.y);
		selectedPos.z = ByteUtil.modU16(selectedPos.z);
		GlUtil.glBegin(GL11.GL_LINES);
		float size = spacing * 3;
		float end = (start + (1.0f / 3.0f) * size);
		float lineAlpha;
		float lineAlphaB;
		for(float i = 0; i < 3; i++) {
			lineAlphaB = 1;
			lineAlpha = 1;
			if(i == 0) {
				lineAlpha = 0;
				lineAlphaB = 0.6f;
			} else if(i == 2) {
				lineAlpha = 0.6f;
				lineAlphaB = 0;
			}
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(selectedPos.x * spacing, selectedPos.y * spacing, start);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(selectedPos.x * spacing, selectedPos.y * spacing, end);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(start, selectedPos.y * spacing, selectedPos.z * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(end, selectedPos.y * spacing, selectedPos.z * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(selectedPos.x * spacing, start, selectedPos.z * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(selectedPos.x * spacing, end, selectedPos.z * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(selectedPos.x * spacing, (selectedPos.y + 1) * spacing, start);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(selectedPos.x * spacing, (selectedPos.y + 1) * spacing, end);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(start, (selectedPos.y) * spacing, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(end, (selectedPos.y) * spacing, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f((selectedPos.x) * spacing, start, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f((selectedPos.x) * spacing, end, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, (selectedPos.y) * spacing, start);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, (selectedPos.y) * spacing, end);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(start, (selectedPos.y + 1) * spacing, (selectedPos.z) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(end, (selectedPos.y + 1) * spacing, (selectedPos.z) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, start, (selectedPos.z) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, end, (selectedPos.z) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, (selectedPos.y + 1) * spacing, start);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, (selectedPos.y + 1) * spacing, end);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f(start, (selectedPos.y + 1) * spacing, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f(end, (selectedPos.y + 1) * spacing, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlpha);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, start, (selectedPos.z + 1) * spacing);
			GlUtil.glColor4fForced(1, 1, 1, lineAlphaB);
			GL11.glVertex3f((selectedPos.x + 1) * spacing, end, (selectedPos.z + 1) * spacing);
			end += (1.0f / 3.0f) * size;
			start += (1.0f / 3.0f) * size;
		}
		GlUtil.glEnd();
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public boolean isInvisible() {
		return false;
	}

	@Override
	public void onInit() {
		controlManager = new TacticalMapControlManager(this);
		camera = new TacticalMapCamera();
		camera.reset();
		camera.alwaysAllowWheelZoom = true;
		shaderOverlay = new TacticalMapShaderOverlay();
		shaderOverlay.onInit();
		// create FBO now that we're initialising within a (likely) GL context
		hud = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getHelpManager();
		initialized = true;
	}
	private static final Vector4f DRAG_FILL = new Vector4f(0.3f, 0.6f, 1.0f, 0.15f);

	/** Updates hoveredIndicator based on the current mouse position. Called each draw frame. */
	private void updateHovered() {
		int mouseX = Mouse.getX();
		int mouseY = GLFrame.getHeight() - Mouse.getY();
		hoveredIndicator = findIndicatorAtScreen(mouseX, mouseY, TacticalMapControlManager.ENTITY_CLICK_THRESHOLD_PX);
	}

	private SegmentController getCurrentEntity() {
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			return (SegmentController) GameClient.getCurrentControl();
		} else {
			return null;
		}
	}

	public void clearSelected() {
		ArrayList<SegmentController> temp = new ArrayList<>(selectedEntities);
		for(SegmentController i : temp) {
			TacticalMapEntityIndicator indicator = drawMap.get(i.getId());
			if(indicator != null) {
				indicator.selected = false;
			}
		}
		selectedEntities.clear();
		if(shaderOverlay != null) {
			shaderOverlay.clearSelected();
		}
	}
	private static final Vector4f DRAG_BORDER = new Vector4f(0.3f, 0.6f, 1.0f, 0.8f);

	private void drawIndicators() {
		ArrayList<Integer> toRemove = null;
		for(Map.Entry<Integer, TacticalMapEntityIndicator> entry : drawMap.entrySet()) {
			try {
				TacticalMapEntityIndicator indicator = entry.getValue();
				if(indicator.getDistance() < maxDrawDistance && indicator.getEntity() != null) {
					indicator.updateEntityTransform();
				} else {
					// schedule for removal after iteration
					if(toRemove == null) {
						toRemove = new ArrayList<>();
					}
					toRemove.add(entry.getKey());
				}
			} catch(Exception exception) {
				exception.printStackTrace();
				CombatTweaks.getInstance().logException("Something went wrong while trying to update entity transforms", exception);
				if(toRemove == null) {
					toRemove = new ArrayList<>();
				}
				toRemove.add(entry.getKey());
			}
		}
		if(toRemove != null) {
			for(Integer id : toRemove) {
				drawMap.remove(id);
			}
		}
	}
	private final Vector3f tmpBboxCorner = new Vector3f();
	/** Drag-select rectangle in screen space. Only valid when isDragSelecting == true. */
	public boolean isDragSelecting;
	public float dragMinX, dragMaxX, dragMinY, dragMaxY;

	/** Emits 12 GL_LINES edges of an axis-aligned bounding box. Must be called inside a GL_LINES begin/end or standalone. */
	private static void drawAABBLines(float x0, float y0, float z0, float x1, float y1, float z1) {
		GL11.glBegin(GL11.GL_LINES);
		// Bottom face
		GL11.glVertex3f(x0, y0, z0);
		GL11.glVertex3f(x1, y0, z0);
		GL11.glVertex3f(x1, y0, z0);
		GL11.glVertex3f(x1, y0, z1);
		GL11.glVertex3f(x1, y0, z1);
		GL11.glVertex3f(x0, y0, z1);
		GL11.glVertex3f(x0, y0, z1);
		GL11.glVertex3f(x0, y0, z0);
		// Top face
		GL11.glVertex3f(x0, y1, z0);
		GL11.glVertex3f(x1, y1, z0);
		GL11.glVertex3f(x1, y1, z0);
		GL11.glVertex3f(x1, y1, z1);
		GL11.glVertex3f(x1, y1, z1);
		GL11.glVertex3f(x0, y1, z1);
		GL11.glVertex3f(x0, y1, z1);
		GL11.glVertex3f(x0, y1, z0);
		// Vertical edges
		GL11.glVertex3f(x0, y0, z0);
		GL11.glVertex3f(x0, y1, z0);
		GL11.glVertex3f(x1, y0, z0);
		GL11.glVertex3f(x1, y1, z0);
		GL11.glVertex3f(x1, y0, z1);
		GL11.glVertex3f(x1, y1, z1);
		GL11.glVertex3f(x0, y0, z1);
		GL11.glVertex3f(x0, y1, z1);
		GL11.glEnd();
	}

	private void drawHudIndicators() {
		if(shouldDraw()) {
			hud.addHelper(tacticalMapMapping, "Toggle Tactical Map", HudContextHelperContainer.Hos.RIGHT, ContextFilter.IMPORTANT);
		}
		if(toggleDraw) {
			hud.addHelper(InputType.MOUSE, 0, "Select | Double-click: Focus | Shift+Click: Multi-select", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.MOUSE, 1, "(Hold) Rotate Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_S, "(Holding Left Control) Toggle Docked Entities", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_A, "(Holding Left Control) Select All", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_X, "Reset Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
		}
	}

	/**
	 * Renders entity labels at cached screen coordinates in orthogonal mode.
	 * Manages a per-indicator GUITextOverlay cache.
	 */
	private void drawLabels() {
		GlUtil.printGlError();
		GUIElement.enableOrthogonal();
		GlUtil.printGlError();

		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid || indicator.getEntity().isCloakedFor(getCurrentEntity())) {
				continue;
			}

			GUITextOverlay overlay = TacticalMapIndicatorPool.getInstance().acquireLabelOverlay();
			String displayText = getEntityDisplay(indicator, getCurrentEntity());
			overlay.setTextSimple(displayText);
			overlay.updateTextSize();

			// Position overlay at screen coordinates with upward offset
			float offsetY = -40.0f;
			overlay.getTransform().origin.set(indicator.screenX, indicator.screenY + offsetY, 0);
			overlay.draw();
			GlUtil.printGlError();

			TacticalMapIndicatorPool.getInstance().releaseLabelOverlay(overlay);
		}

		GUIElement.disableOrthogonal();
		GlUtil.printGlError();
	}

	/**
	 * Renders targeting, defend, and movement paths only for own ships, selected, or hovered entities.
	 */
	private void drawPaths() {
		SegmentController playerEntity = getCurrentEntity();
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;

			// Only show paths for: own ships, selected entities, or hovered entity
			boolean isOwnShip = indicator.getEntity().getFactionId() == GameClient.getClientPlayerState().getFactionId();
			boolean isSelected = selectedEntities.contains(indicator.getEntity());
			boolean isHovered = indicator == hoveredIndicator;
			if(!isOwnShip && !isSelected && !isHovered) continue;

			// Draw targeting path (red) if entity has a target
			if(indicator.getCurrentTarget() != null) {
				Vector3f start = new Vector3f(indicator.entityTransform.origin);
				Vector3f end = new Vector3f(indicator.getCurrentTarget().getWorldTransform().origin);
				TacticalMapEntityIndicator targetIndicator = drawMap.get(indicator.getCurrentTarget().getId());
				if(targetIndicator != null) {
					end.set(targetIndicator.entityTransform.origin);
				}
				if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
					startDrawDottedLine();
					drawDottedLine(start, end, PATH_RED);
					endDrawDottedLine();
				}
			}

			// Draw defend path (green) if entity has a defend target
			if(indicator.getDefendTarget() != null) {
				Vector3f start = new Vector3f(indicator.entityTransform.origin);
				Vector3f end = new Vector3f(indicator.getDefendTarget().getWorldTransform().origin);
				TacticalMapEntityIndicator defendIndicator = drawMap.get(indicator.getDefendTarget().getId());
				if(defendIndicator != null) {
					end.set(defendIndicator.entityTransform.origin);
				}
				if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
					startDrawDottedLine();
					drawDottedLine(start, end, PATH_GREEN);
					endDrawDottedLine();
				}
			}

			// Draw movement path (cyan) if configured
			if(indicator.getEntity() instanceof Ship) {
				Vector3f start = new Vector3f(indicator.entityTransform.origin);
				Vector3f end = GlUtil.getForwardVector(dottedA, indicator.getEntity().getWorldTransform());
				end.scale(indicator.getEntity().getSpeedCurrent());
				if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
					startDrawDottedLine();
					drawDottedLine(start, end, PATH_CYAN);
					endDrawDottedLine();
				}
			}

			// Draw mining path (orange) if this ship has a mine assignment
			if(indicator.getEntity() instanceof Ship) {
				Integer assignedAsteroid = null;
				try {
					assignedAsteroid = videogoose.combattweaks.manager.MineManager.getInstance().getAssignedTarget(indicator.getEntity().getId());
				} catch(Exception ignored) {
				}
				if(assignedAsteroid != null) {
					SimpleGameObject obj = (SimpleGameObject) GameCommon.getGameObject(assignedAsteroid);
					if(obj instanceof SegmentController) {
						SegmentController asteroid = (SegmentController) obj;
						Vector3f start = new Vector3f(indicator.entityTransform.origin);
						Vector3f end = new Vector3f(asteroid.getWorldTransform().origin);
						TacticalMapEntityIndicator targetIndicator = drawMap.get(asteroid.getId());
						if(targetIndicator != null) {
							end.set(targetIndicator.entityTransform.origin);
						}
						if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
							startDrawDottedLine();
							drawDottedLine(start, end, PATH_ORANGE);
							endDrawDottedLine();
						}
					}
				}
			}

			// Draw repair path (magenta) if this ship has a repair assignment
			if(indicator.getEntity() instanceof Ship) {
				Integer assignedTarget = null;
				try {
					assignedTarget = videogoose.combattweaks.manager.RepairManager.getInstance().getAssignedTarget(indicator.getEntity().getId());
				} catch(Exception ignored) {
				}
				if(assignedTarget != null) {
					SimpleGameObject obj = (SimpleGameObject) GameCommon.getGameObject(assignedTarget);
					if(obj instanceof SegmentController) {
						SegmentController target = (SegmentController) obj;
						Vector3f start = new Vector3f(indicator.entityTransform.origin);
						Vector3f end = new Vector3f(target.getWorldTransform().origin);
						TacticalMapEntityIndicator targetIndicator = drawMap.get(target.getId());
						if(targetIndicator != null) {
							end.set(targetIndicator.entityTransform.origin);
						}
						if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
							startDrawDottedLine();
							drawDottedLine(start, end, PATH_MAGENTA);
							endDrawDottedLine();
						}
					}
				}
			}
		}
	}

	/**
	 * Prepares GL state for drawing dotted lines.
	 */
	private void startDrawDottedLine() {
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_COLOR_MATERIAL);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPushMatrix();
		float aspect = (float) GLFrame.getWidth() / GLFrame.getHeight();
		GlUtil.gluPerspective(Controller.projectionMatrix, (Float) EngineSettings.G_FOV.getCurrentState(), aspect, 10, 25000, true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		camera.lookAt(true);
		GL11.glLineWidth(2.0f); // Make paths more visible
		GlUtil.glBegin(GL11.GL_LINES);
	}

	/**
	 * Draws a dotted line between two points in world space.
	 */
	private void drawDottedLine(Vector3f from, Vector3f to, Vector4f color) {
		dottedDir.sub(to, from);
		dottedDirN.set(dottedDir);
		dottedDirN.normalize();
		float len = dottedDir.length();
		Vector3f a = dottedA;
		Vector3f b = dottedB;
		float dottedSize = Math.min(Math.max(2, len * 0.1f), 40);
		GlUtil.glColor4f(color);
		boolean first = true;
		// Use a simple animation based on time
		float f = 0;
		for(; f < len; f += (dottedSize * 2)) {
			a.set(dottedDirN);
			a.scale(f);
			if(first) {
				a.set(0, 0, 0);
				first = false;
			}
			b.set(dottedDirN);
			if((f + dottedSize) >= len) {
				b.scale(len);
				f = len;
			} else {
				b.scale(f + dottedSize);
			}
			GL11.glVertex3f(from.x + a.x, from.y + a.y, from.z + a.z);
			GL11.glVertex3f(from.x + b.x, from.y + b.y, from.z + b.z);
		}
	}

	/**
	 * Restores GL state after drawing dotted lines.
	 */
	private void endDrawDottedLine() {
		GlUtil.glEnd();
		GL11.glLineWidth(1.0f); // Restore line width
		GlUtil.glPopMatrix();
		GlUtil.glColor4f(1, 1, 1, 1);
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
	}

	/**
	 * Returns the indicator whose screen-space bounding box contains (mouseX, mouseY),
	 * preferring the smallest bbox when multiple overlap. Returns null if none hit.
	 * The {@code threshold} parameter is unused but kept for call-site compatibility.
	 */
	public TacticalMapEntityIndicator findIndicatorAtScreen(int mouseX, int mouseY, float threshold) {
		TacticalMapEntityIndicator best = null;
		float bestArea = Float.MAX_VALUE;
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			if(mouseX >= indicator.screenMinX && mouseX <= indicator.screenMaxX && mouseY >= indicator.screenMinY && mouseY <= indicator.screenMaxY) {
				float area = (indicator.screenMaxX - indicator.screenMinX) * (indicator.screenMaxY - indicator.screenMinY);
				if(area < bestArea) {
					bestArea = area;
					best = indicator;
				}
			}
		}
		return best;
	}

	private PlayerPanel getPlayerPanel() {
		return GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel();
	}

	@Override
	public void draw() {
		if(!initialized) {
			onInit();
		}

		if(toggleDraw && Controller.getCamera() instanceof TacticalMapCamera) {
			GlUtil.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			GameClient.getClientPlayerState().getNetworkObject().selectedEntityId.set(-1);
//			drawGrid(-sectorSize, sectorSize); Probably not needed
			((BottomBarBuild) getPlayerPanel().getBuildSideBar()).cleanUp();
			// Update entity transforms first so screen positions use current data
			drawIndicators();
			computeScreenPositions();
			updateHovered();
			drawBoundingBoxWireframes();
			drawPaths();
			drawLabels();
			drawDragSelectRect();
			GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		}
		drawHudIndicators();
	}

	/**
	 * Projects every indicator's world position into screen space and caches the results
	 * for use by the click-detection code in the control manager.
	 * Also projects the 8 bounding box corners to compute a screen-space AABB for hit testing.
	 * Uses the same perspective + camera view as drawBoundingBoxWireframes so hover regions
	 * exactly match what is rendered.
	 */
	private void computeScreenPositions() {
		// Detect resolution changes and invalidate cached screen positions
		int currentWidth = GLFrame.getWidth();
		int currentHeight = GLFrame.getHeight();
		if(currentWidth != lastKnownWidth || currentHeight != lastKnownHeight) {
			// Resolution changed — mark all positions as invalid until recalculated
			for(TacticalMapEntityIndicator indicator : drawMap.values()) {
				indicator.screenPosValid = false;
			}
			lastKnownWidth = currentWidth;
			lastKnownHeight = currentHeight;
		}

		// Set up the same matrices used for 3D rendering
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPushMatrix();
		float aspect = (float) currentWidth / currentHeight;
		GlUtil.gluPerspective(Controller.projectionMatrix, (Float) EngineSettings.G_FOV.getCurrentState(), aspect, 10, 25000, true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		camera.lookAt(true); // loads view matrix; push/pop above ensures GL state is restored

		GL_MODELVIEW.clear();
		GL_PROJECTION.clear();
		GL_VIEWPORT.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, GL_MODELVIEW);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, GL_PROJECTION);
		GL11.glGetInteger(GL11.GL_VIEWPORT, GL_VIEWPORT);

		GlUtil.glPopMatrix(); // modelview
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);

		int vpH = GL_VIEWPORT.get(3);

		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			// Project entity center for label placement
			Vector3f pos = indicator.entityTransform.origin;
			GL_WIN_COORDS.clear();
			boolean ok = Project.gluProject(pos.x, pos.y, pos.z, GL_MODELVIEW, GL_PROJECTION, GL_VIEWPORT, GL_WIN_COORDS);
			float depth = GL_WIN_COORDS.get(2);
			indicator.screenPosValid = ok && depth > 0.0f && depth < 1.0f;
			if(!indicator.screenPosValid) continue;

			indicator.screenX = GL_WIN_COORDS.get(0);
			indicator.screenY = vpH - GL_WIN_COORDS.get(1);

			// Project all 8 bounding box corners to get screen-space extents for hit testing
			BoundingBox bb = indicator.getEntity().getBoundingBox();
			if(bb == null) {
				// Fallback: use a small fixed radius around center
				float r = 30.0f;
				indicator.screenMinX = indicator.screenX - r;
				indicator.screenMaxX = indicator.screenX + r;
				indicator.screenMinY = indicator.screenY - r;
				indicator.screenMaxY = indicator.screenY + r;
				continue;
			}

			float sMinX = Float.MAX_VALUE, sMaxX = -Float.MAX_VALUE;
			float sMinY = Float.MAX_VALUE, sMaxY = -Float.MAX_VALUE;
			Transform t = indicator.entityTransform;
			for(int i = 0; i < 8; i++) {
				tmpBboxCorner.set(bb.min.x + BBOX_XS[i] * (bb.max.x - bb.min.x), bb.min.y + BBOX_YS[i] * (bb.max.y - bb.min.y), bb.min.z + BBOX_ZS[i] * (bb.max.z - bb.min.z));
				t.transform(tmpBboxCorner);
				GL_WIN_COORDS.clear();
				boolean cornerOk = Project.gluProject(tmpBboxCorner.x, tmpBboxCorner.y, tmpBboxCorner.z, GL_MODELVIEW, GL_PROJECTION, GL_VIEWPORT, GL_WIN_COORDS);
				if(!cornerOk) continue;
				float cx = GL_WIN_COORDS.get(0);
				float cy = vpH - GL_WIN_COORDS.get(1);
				if(cx < sMinX) sMinX = cx;
				if(cx > sMaxX) sMaxX = cx;
				if(cy < sMinY) sMinY = cy;
				if(cy > sMaxY) sMaxY = cy;
			}
			// Ensure at least a minimal click target even for single-block entities
			float MIN_HALF = 20.0f;
			if(sMaxX - sMinX < MIN_HALF * 2) {
				sMinX = indicator.screenX - MIN_HALF;
				sMaxX = indicator.screenX + MIN_HALF;
			}
			if(sMaxY - sMinY < MIN_HALF * 2) {
				sMinY = indicator.screenY - MIN_HALF;
				sMaxY = indicator.screenY + MIN_HALF;
			}
			indicator.screenMinX = sMinX;
			indicator.screenMaxX = sMaxX;
			indicator.screenMinY = sMinY;
			indicator.screenMaxY = sMaxY;
		}
	}

	/**
	 * Builds the display text for an entity label.
	 */
	private String getEntityDisplay(TacticalMapEntityIndicator indicator, SegmentController playerEntity) {
		SegmentController entity = indicator.getEntity();
		StringBuilder builder = new StringBuilder();
		builder.append(entity.getRealName()); // TODO: distort string for jammed/cloaked
		builder.append("\n");
		if(entity.getFaction() != null) {
			builder.append("[").append(entity.getFaction().getName()).append("]\n");
		}
		if(!entity.equals(playerEntity)) {
			if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
				builder.append("???km\n");
			} else {
				builder.append(StringTools.formatDistance(indicator.getDistanceFromFocusedShip())).append("\n");
			}
		}
		if(indicator.getCurrentTarget() != null) {
			builder.append("Engaging ").append(indicator.getCurrentTarget().getName());
		}
		return builder.toString().trim();
	}

	/**
	 * Draws a 3D wireframe bounding box around each selected or hovered entity,
	 * then immediately samples the GL matrices for screen-position projection.
	 * Both operations use the same perspective + camera view to guarantee consistency.
	 */
	private void drawBoundingBoxWireframes() {
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_COLOR_MATERIAL);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPushMatrix();
		float aspect = (float) GLFrame.getWidth() / GLFrame.getHeight();
		GlUtil.gluPerspective(Controller.projectionMatrix, (Float) EngineSettings.G_FOV.getCurrentState(), aspect, 10, 25000, true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		camera.lookAt(true);
		GL11.glLineWidth(2.0f);

		// Pass 1: depth-tested — draw solid lines only where not occluded by geometry
		GlUtil.glEnable(GL11.GL_DEPTH_TEST);
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			boolean isSelected = indicator.selected || selectedEntities.contains(indicator.getEntity());
			boolean isHovered = indicator == hoveredIndicator && !isSelected;
			if(!isSelected && !isHovered) continue;
			BoundingBox bb = indicator.getEntity().getBoundingBox();
			if(bb == null) continue;
			Vector4f color = isSelected ? OUTLINE_SELECTED : OUTLINE_HOVERED;
			GlUtil.glColor4f(color.x, color.y, color.z, color.w);
			GlUtil.glPushMatrix();
			GlUtil.glMultMatrix(indicator.entityTransform);
			drawAABBLines(bb.min.x, bb.min.y, bb.min.z, bb.max.x, bb.max.y, bb.max.z);
			GlUtil.glPopMatrix();
		}

		// Pass 2: no depth test — draw faint lines through occluding geometry
		GlUtil.glDisable(GL11.GL_DEPTH_TEST);
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			boolean isSelected = indicator.selected || selectedEntities.contains(indicator.getEntity());
			boolean isHovered = indicator == hoveredIndicator && !isSelected;
			if(!isSelected && !isHovered) continue;
			BoundingBox bb = indicator.getEntity().getBoundingBox();
			if(bb == null) continue;
			Vector4f color = isSelected ? OUTLINE_SELECTED_OCCLUDED : OUTLINE_HOVERED_OCCLUDED;
			GlUtil.glColor4f(color.x, color.y, color.z, color.w);
			GlUtil.glPushMatrix();
			GlUtil.glMultMatrix(indicator.entityTransform);
			drawAABBLines(bb.min.x, bb.min.y, bb.min.z, bb.max.x, bb.max.y, bb.max.z);
			GlUtil.glPopMatrix();
		}

		// Pass 3: selected turrets — depth tested (solid)
		if(!selectedTurrets.isEmpty()) {
			GlUtil.glEnable(GL11.GL_DEPTH_TEST);
			for(Ship turret : selectedTurrets.values()) {
				BoundingBox bb = turret.getBoundingBox();
				if(bb == null) continue;
				GlUtil.glColor4f(OUTLINE_TURRET.x, OUTLINE_TURRET.y, OUTLINE_TURRET.z, OUTLINE_TURRET.w);
				GlUtil.glPushMatrix();
				GlUtil.glMultMatrix(turret.getWorldTransform());
				drawAABBLines(bb.min.x, bb.min.y, bb.min.z, bb.max.x, bb.max.y, bb.max.z);
				GlUtil.glPopMatrix();
			}

			// Pass 4: selected turrets — no depth test (occluded ghost)
			GlUtil.glDisable(GL11.GL_DEPTH_TEST);
			for(Ship turret : selectedTurrets.values()) {
				BoundingBox bb = turret.getBoundingBox();
				if(bb == null) continue;
				GlUtil.glColor4f(OUTLINE_TURRET_OCCLUDED.x, OUTLINE_TURRET_OCCLUDED.y, OUTLINE_TURRET_OCCLUDED.z, OUTLINE_TURRET_OCCLUDED.w);
				GlUtil.glPushMatrix();
				GlUtil.glMultMatrix(turret.getWorldTransform());
				drawAABBLines(bb.min.x, bb.min.y, bb.min.z, bb.max.x, bb.max.y, bb.max.z);
				GlUtil.glPopMatrix();
			}
		}

		GL11.glLineWidth(1.0f);
		GlUtil.glColor4f(1, 1, 1, 1);
		GlUtil.glEnable(GL11.GL_DEPTH_TEST);
		GlUtil.glPopMatrix(); // modelview
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glEnable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_TEXTURE_2D);
	}

	/**
	 * Draws the drag-select rectangle overlay in screen-space orthographic mode.
	 */
	private void drawDragSelectRect() {
		if(!isDragSelecting) return;
		GUIElement.enableOrthogonal();
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		// Depth test would reject 2D quads against 3D scene depth values — disable it
		GlUtil.glDisable(GL11.GL_DEPTH_TEST);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

		// Semi-transparent fill
		GlUtil.glColor4f(DRAG_FILL.x, DRAG_FILL.y, DRAG_FILL.z, DRAG_FILL.w);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(dragMinX, dragMinY);
		GL11.glVertex2f(dragMaxX, dragMinY);
		GL11.glVertex2f(dragMaxX, dragMaxY);
		GL11.glVertex2f(dragMinX, dragMaxY);
		GL11.glEnd();

		// Border
		GL11.glLineWidth(1.5f);
		GlUtil.glColor4f(DRAG_BORDER.x, DRAG_BORDER.y, DRAG_BORDER.z, DRAG_BORDER.w);
		GL11.glBegin(GL11.GL_LINE_LOOP);
		GL11.glVertex2f(dragMinX, dragMinY);
		GL11.glVertex2f(dragMaxX, dragMinY);
		GL11.glVertex2f(dragMaxX, dragMaxY);
		GL11.glVertex2f(dragMinX, dragMaxY);
		GL11.glEnd();
		GL11.glLineWidth(1.0f);

		GlUtil.glColor4f(1, 1, 1, 1);
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glEnable(GL11.GL_DEPTH_TEST);
		GUIElement.disableOrthogonal();
	}

	/**
	 * Selects all entities whose screen bbox overlaps the given screen-space rectangle.
	 * If additive is false, the current selection is cleared first.
	 */
	public void applyDragSelection(float minX, float minY, float maxX, float maxY, boolean additive) {
		if(!additive) clearSelected();
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			if(canSelect(indicator.getEntity())) {
				// Overlap test: two AABBs overlap if neither is fully outside the other
				if(indicator.screenMaxX < minX || indicator.screenMinX > maxX || indicator.screenMaxY < minY || indicator.screenMinY > maxY) {
					continue;
				}
				addSelection(indicator);
			}
		}
	}

	private boolean canSelect(SegmentController entity) {
		return GameCommon.getGameState().getFactionManager().isFriend(entity.getFactionId(), GameClient.getClientState().getPlayer().getFactionId()) && entity.getFactionId() != 0;
	}
}