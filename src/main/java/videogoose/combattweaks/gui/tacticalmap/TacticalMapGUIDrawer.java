package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.utils.draw.ModWorldDrawer;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import org.schema.common.util.ByteUtil;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.effects.Indication;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.input.KeyboardMappings;
import videogoose.combattweaks.CombatTweaks;

import javax.vecmath.Vector3f;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TacticalMapGUIDrawer extends ModWorldDrawer {

	private static TacticalMapGUIDrawer instance;
	private static final FloatBuffer GL_MODELVIEW = BufferUtils.createFloatBuffer(16);
	private static final FloatBuffer GL_PROJECTION = BufferUtils.createFloatBuffer(16);
	private static final IntBuffer GL_VIEWPORT = BufferUtils.createIntBuffer(16);
	private static final FloatBuffer GL_WIN_COORDS = BufferUtils.createFloatBuffer(3);
	public final int sectorSize;
	public final float maxDrawDistance;
	public final Vector3f labelOffset;
	public final ConcurrentHashMap<Integer, TacticalMapEntityIndicator> drawMap;
	public final ConcurrentLinkedQueue<SegmentController> selectedEntities = new ConcurrentLinkedQueue<>();
	private HudContextHelpManager hud;
	public float selectedRange;
	public TacticalMapControlManager controlManager;
	public TacticalMapCamera camera;
	public boolean toggleDraw;
	public boolean drawMovementPaths = true;
	private boolean initialized;
	private boolean firstTime = true;
	private long updateTimer;
	private final KeyboardMappings tacticalMapMapping;
	/** The indicator currently under the mouse cursor, updated each frame. May be null. */
	public TacticalMapEntityIndicator hoveredIndicator;
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

	private KeyboardMappings getMappingFromName(String name) {
		for(KeyboardMappings mapping : KeyboardMappings.values()) {
			if(mapping.name().equals(name)) {
				return mapping;
			}
		}
		return null;
	}

	public static TacticalMapGUIDrawer getInstance() {
		return instance;
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

	public void clearSelected() {
		ArrayList<SegmentController> temp = new ArrayList<>(selectedEntities);
		for(SegmentController i : temp) {
			TacticalMapEntityIndicator indicator = drawMap.get(i.getId());
			if(indicator != null) {
				indicator.onUnSelect();
			}
		}
		if(shaderOverlay != null) {
			shaderOverlay.clearSelected();
		}
	}

	public void removeAll() {
		clearSelected();
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
			// before drawIndicators() can modify them (label overlays switch to orthographic).
			computeScreenPositions();
			updateHovered();
			drawIndicators();
			shaderOverlay.draw();
			GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		}
		drawHudIndicators();
	}

	public boolean shouldDraw() {
		return (GameClient.getClientState().getPlayerInputs().isEmpty() || GameClient.getClientState().getController().isChatActive() || GameClient.getClientState().isInAnyStructureBuildMode() || GameClient.getClientState().isInFlightMode()) && !GameClient.getClientState().getWorldDrawer().getGameMapDrawer().isMapActive();
	}

	private void drawHudIndicators() {
		if(shouldDraw()) {
			hud.addHelper(tacticalMapMapping, "Toggle Tactical Map", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
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

	private void drawIndicators() {
		ArrayList<Integer> toRemove = null;
		for(Map.Entry<Integer, TacticalMapEntityIndicator> entry : drawMap.entrySet()) {
			try {
				TacticalMapEntityIndicator indicator = entry.getValue();
				if(indicator.getDistance() < maxDrawDistance && indicator.getEntity() != null) {
					indicator.updateEntityTransform();
					Indication indication = indicator.getIndication(indicator.getSystem());
					indicator.drawLabel(indication.getCurrentTransform());

					// Only draw paths for selected or hovered entities
					boolean isSelected = indicator.selected || selectedEntities.contains(indicator.getEntity());
					boolean isHovered = indicator == hoveredIndicator;
					if(isSelected || isHovered) {
						indicator.drawTargetingPath(camera);
						indicator.drawDefendPath(camera);
						if(drawMovementPaths) {
							indicator.drawMovementPath(camera);
						}
					}
				} else {
					// schedule for removal after iteration
					// release pooled UI resources held by the indicator
					indicator.releaseResources();
					if(toRemove == null) {
						toRemove = new ArrayList<>();
					}
					toRemove.add(entry.getKey());
				}
			} catch(Exception exception) {
				exception.printStackTrace();
				CombatTweaks.getInstance().logException("Something went wrong while trying to draw entity indicators", exception);
				if(toRemove == null) {
					toRemove = new ArrayList<>();
				}
				toRemove.add(entry.getKey());
			}
		}
		if(toRemove != null) {
			for(Integer id : toRemove) {
				TacticalMapEntityIndicator removed = drawMap.remove(id);
				// already attempted cleanup above, but be safe
				if(removed != null) {
					removed.releaseResources();
				}
			}
		}
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

		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			Vector3f pos = indicator.getPos();
			GL_WIN_COORDS.clear();
			boolean ok = Project.gluProject(pos.x, pos.y, pos.z, GL_MODELVIEW, GL_PROJECTION, GL_VIEWPORT, GL_WIN_COORDS);
			float depth = GL_WIN_COORDS.get(2);
			indicator.screenPosValid = ok && depth > 0.0f && depth < 1.0f;
			if(indicator.screenPosValid) {
				indicator.screenX = GL_WIN_COORDS.get(0);
				// gluProject gives Y from the bottom; flip to screen-top origin
				indicator.screenY = GLFrame.getHeight() - GL_WIN_COORDS.get(1);
			}
		}
	}

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
}