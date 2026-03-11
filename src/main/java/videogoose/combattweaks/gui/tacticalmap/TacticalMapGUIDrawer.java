package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.schema.common.util.ByteUtil;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.shader.Shader;
import org.schema.schine.input.InputType;
import org.schema.schine.input.KeyboardMappings;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ResourceManager;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.awt.*;
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
	private static TacticalMapGUIDrawer instance;
	public final int sectorSize;
	public final float maxDrawDistance;
	public final Vector3f labelOffset;
	public final ConcurrentHashMap<Integer, TacticalMapEntityIndicator> drawMap;
	public final ConcurrentLinkedQueue<SegmentController> selectedEntities = new ConcurrentLinkedQueue<>();
	public final ConcurrentHashMap<String, Object> selectedTurrets = new ConcurrentHashMap<>(); // Stores selected turrets by ID
	private final KeyboardMappings tacticalMapMapping;
	public float selectedRange;
	public TacticalMapControlManager controlManager;
	public TacticalMapCamera camera;
	public boolean toggleDraw;
	public boolean drawMovementPaths = true;
	/** The indicator currently under the mouse cursor, updated each frame. May be null. */
	public TacticalMapEntityIndicator hoveredIndicator;
	private HudContextHelpManager hud;
	private boolean initialized;
	private boolean firstTime = true;
	private long updateTimer;
	private TacticalMapShaderOverlay shaderOverlay;

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

	// Path colors for dotted line rendering
	private static final Vector4f PATH_RED = new Vector4f(Color.RED.getColorComponents(new float[4]));

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

	public void addTurretSelection(String turretId) {
		selectedTurrets.put(turretId, true);
		if(shaderOverlay != null) {
			shaderOverlay.addSelectedTurret(turretId);
		}
	}

	public void removeTurretSelection(String turretId) {
		selectedTurrets.remove(turretId);
		if(shaderOverlay != null) {
			shaderOverlay.removeSelectedTurret(turretId);
		}
	}

	public void clearSelectedTurrets() {
		selectedTurrets.clear();
		if(shaderOverlay != null) {
			shaderOverlay.clearSelectedTurrets();
		}
	}

	public boolean isTurretSelected(String turretId) {
		return selectedTurrets.containsKey(turretId);
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
	private static final Vector4f PATH_CYAN = new Vector4f(Color.CYAN.getColorComponents(new float[4]));

	public boolean shouldDraw() {
		return (GameClient.getClientState().getPlayerInputs().isEmpty() || GameClient.getClientState().getController().isChatActive() || GameClient.getClientState().isInAnyStructureBuildMode() || GameClient.getClientState().isInFlightMode()) && !GameClient.getClientState().getWorldDrawer().getGameMapDrawer().isMapActive();
	}

	private void drawHudIndicators() {
		if(shouldDraw()) {
			hud.addHelper(tacticalMapMapping, "Toggle Tactical Map", HudContextHelperContainer.Hos.RIGHT, ContextFilter.IMPORTANT);
		}
		if(toggleDraw) {
			hud.addHelper(InputType.MOUSE, 0, "Select | Double-click: Focus | Shift+Click: Multi-select", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.MOUSE, 1, "(Hold) Rotate Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_LCONTROL, "(Hold) Show Docked Entities", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_A, "(Holding Left Control) Select All", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_X, "Reset Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
		}
	}

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
	private static final Vector4f PATH_GREEN = new Vector4f(Color.GREEN.getColorComponents(new float[4]));

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
	// Reusable temporaries for dotted line math
	private final Vector3f dottedDir = new Vector3f();

	/**
	 * Returns the indicator whose cached screen position is closest to (mouseX, mouseY),
	 * or null if none falls within {@code threshold} pixels.
	 */
	public TacticalMapEntityIndicator findIndicatorAtScreen(int mouseX, int mouseY, float threshold) {
		TacticalMapEntityIndicator closest = null;
		float closestDistSq = threshold * threshold;
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			float dx = indicator.screenX - mouseX;
			float dy = indicator.screenY - mouseY;
			float distSq = dx * dx + dy * dy;
			if(distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = indicator;
			}
		}
		return closest;
	}

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

	// Ring indicator constants
	private static final Vector4f OUTLINE_SELECTED = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f); // yellow
	private static final Vector4f OUTLINE_HOVERED = new Vector4f(1.0f, 1.0f, 1.0f, 0.6f); // white, slightly transparent
	private static final float RING_RADIUS = 25.0f;
	private final Vector3f dottedDirN = new Vector3f();
	private final Vector3f dottedA = new Vector3f();
	private final Vector3f dottedB = new Vector3f();

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

	@Override
	public void draw() {
		if(!initialized) {
			onInit();
		}

		if(toggleDraw && Controller.getCamera() instanceof TacticalMapCamera) {
			GlUtil.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			GameClient.getClientPlayerState().getNetworkObject().selectedEntityId.set(-1);
			drawGrid(-sectorSize, sectorSize);
			// Read GL matrices immediately after drawGrid() restores the engine's 3D state —
			// before label overlays switch to orthographic.
			computeScreenPositions();
			updateHovered();
			drawIndicators();
			drawPaths();
			drawLabels();
			drawRingIndicators();
			GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		}
		drawHudIndicators();
	}

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

	/**
	 * Projects every indicator's world position into screen space and caches the results
	 * for use by the click-detection code in the control manager.
	 * We snapshot whatever GL matrices the engine currently has rather than replicating
	 * them ourselves — this guarantees the projection matches the actual rendered scene.
	 * Must be called from the GL thread (inside draw()), after drawGrid() sets up 3D state.
	 */
	private void computeScreenPositions() {
		GL_MODELVIEW.clear();
		GL_PROJECTION.clear();
		GL_VIEWPORT.clear();
		GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, GL_MODELVIEW);
		GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, GL_PROJECTION);
		GL11.glGetInteger(GL11.GL_VIEWPORT, GL_VIEWPORT);

		int debugCount = 0;
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			Vector3f pos = indicator.entityTransform.origin;
			GL_WIN_COORDS.clear();
			boolean ok = Project.gluProject(pos.x, pos.y, pos.z, GL_MODELVIEW, GL_PROJECTION, GL_VIEWPORT, GL_WIN_COORDS);
			float depth = GL_WIN_COORDS.get(2);
			indicator.screenPosValid = ok && depth > 0.0f && depth < 1.0f;
			if(indicator.screenPosValid) {
				indicator.screenX = GL_WIN_COORDS.get(0);
				// gluProject gives Y from the bottom; flip to screen-top origin
				indicator.screenY = GLFrame.getHeight() - GL_WIN_COORDS.get(1);
				if(debugCount < 3) {
					CombatTweaks.getInstance().logInfo("Screen pos for " + indicator.getEntity().getName() + ": (" + indicator.screenX + ", " + indicator.screenY + ") viewport: " + GLFrame.getWidth() + "x" + GLFrame.getHeight());
					debugCount++;
				}
			}
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
	 * Renders targeting, defend, and movement paths for selected or hovered entities.
	 */
	private void drawPaths() {
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			boolean isSelected = indicator.selected || selectedEntities.contains(indicator.getEntity());
			boolean isHovered = indicator == hoveredIndicator;
			if(!isSelected && !isHovered) continue;

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
			if(drawMovementPaths && indicator.getEntity() instanceof Ship) {
				Vector3f start = new Vector3f(indicator.entityTransform.origin);
				Vector3f end = GlUtil.getForwardVector(dottedA, indicator.getEntity().getWorldTransform());
				end.scale(indicator.getEntity().getSpeedCurrent());
				if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
					startDrawDottedLine();
					drawDottedLine(start, end, PATH_CYAN);
					endDrawDottedLine();
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
		GlUtil.translateModelview(-50.0f, -50.0f, -50.0f);
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
		GlUtil.glPopMatrix();
		GlUtil.glColor4f(1, 1, 1, 1);
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
	}

	/**
	 * Builds the display text for an entity label.
	 */
	private String getEntityDisplay(TacticalMapEntityIndicator indicator, SegmentController playerEntity) {
		SegmentController entity = indicator.getEntity();
		StringBuilder builder = new StringBuilder();
		if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
			builder.append(entity.getRealName()); // TODO: distort string for jammed/cloaked
		} else {
			builder.append(entity.getRealName());
		}
		builder.append("\n");
		if(entity.getFaction() != null) {
			if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
				builder.append("[").append(entity.getFaction().getName()).append("]\n"); // TODO: distort
			} else {
				builder.append("[").append(entity.getFaction().getName()).append("]\n");
			}
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
	 * Draws anti-aliased rings at the screen-projected position of each hovered or selected entity.
	 * Uses a custom fragment shader to produce a smooth ring shape from a screen-space quad.
	 */
	private void drawRingIndicators() {
		Shader shader = ResourceManager.getShader("tactical_ring");
		if(shader == null) {
			CombatTweaks.getInstance().logWarning("tactical_ring shader not loaded");
			return;
		}

		CombatTweaks.getInstance().logInfo("Starting ring indicator render. Screen: " + GLFrame.getWidth() + "x" + GLFrame.getHeight());
		GlUtil.printGlError();
		GUIElement.enableOrthogonal();
		GlUtil.printGlError();

		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		shader.loadWithoutUpdate();
		GlUtil.printGlError();

		int ringCount = 0;
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			boolean isSelected = indicator.selected || selectedEntities.contains(indicator.getEntity());
			boolean isHovered = indicator == hoveredIndicator && !isSelected;
			if(!isSelected && !isHovered) continue;

			ringCount++;
			Vector4f color = isSelected ? OUTLINE_SELECTED : OUTLINE_HOVERED;
			GlUtil.updateShaderVector4f(shader, "color", color);

			float sx = indicator.screenX;
			float sy = indicator.screenY;

			if(ringCount <= 2) {
				CombatTweaks.getInstance().logInfo("Ring for " + indicator.getEntity().getName() + " at (" + sx + ", " + sy + ") radius=" + RING_RADIUS + " selected=" + isSelected);
			}

			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2f(0, 0); GL11.glVertex2f(sx - RING_RADIUS, sy - RING_RADIUS);
			GL11.glTexCoord2f(1, 0); GL11.glVertex2f(sx + RING_RADIUS, sy - RING_RADIUS);
			GL11.glTexCoord2f(1, 1); GL11.glVertex2f(sx + RING_RADIUS, sy + RING_RADIUS);
			GL11.glTexCoord2f(0, 1); GL11.glVertex2f(sx - RING_RADIUS, sy + RING_RADIUS);
			GL11.glEnd();
		}

		if(ringCount > 0) {
			CombatTweaks.getInstance().logInfo("Drew " + ringCount + " ring indicators");
		}
		GlUtil.printGlError();

		shader.unloadWithoutExit();
		GlUtil.glDisable(GL11.GL_BLEND);
		GUIElement.disableOrthogonal();
		GlUtil.printGlError();
	}
}