package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import api.utils.draw.ModWorldDrawer;
import api.utils.game.PlayerUtils;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
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
import org.schema.schine.graphicsengine.core.AbstractScene;
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
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.manager.MoveManager;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TacticalMapGUIDrawer extends ModWorldDrawer {

	/** Time for a path's dashes to march forward one full dash+gap cycle. Keeps scroll speed uniform across line lengths. */
	private static final long SCROLL_PERIOD_MS = 1400;
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
	private static final Vector4f PATH_QUEUED = new Vector4f(0.7f, 0.7f, 0.75f, 0.45f); // faint grey for queued orders
	// Bounding box wireframe colors — solid pass (depth tested) and occluded pass (through geometry)
	/**
	 * Near/far planes for the map's 3D passes. These MUST match the planes the engine renders the sector
	 * scene with ({@code MainGameGraphics}: near 0.05, far sectorSize*7) — otherwise our depth values don't
	 * line up with what's in the depth buffer and depth-testing the highlight boxes fails (they'd draw over
	 * geometry that should occlude them). Far is computed live from the sector size.
	 */
	private static final float SCENE_NEAR_PLANE = 0.05f;

	private float sceneFarPlane() {
		return getSectorSize() * 7.0f;
	}

	/**
	 * The field-of-view the engine renders the sector scene with — base FOV times the active zoom factor.
	 *
	 * <p>The engine's scene projection (AbstractScene.initProjection) uses {@code G_FOV * zoomFactor}, and
	 * the zoom factor is non-1 whenever a zoom-capable weapon's zoom is toggled (right-click) — which the
	 * tactical map's right-drag camera rotation can trip, and it stays toggled. If we project our overlays
	 * with the plain FOV while the scene (and its correct markers) use the zoomed FOV, every overlay lands
	 * consistently offset, even with the camera still. Matching the FOV keeps them locked to the scene.</p>
	 */
	private float sceneFov() {
		return (Float) EngineSettings.G_FOV.getCurrentState() * AbstractScene.getZoomFactorForRender(true);
	}

	// Camera-relative rendering temporaries (see loadCameraRelativeModelview).
	private final Transform crView = new Transform();
	private final javax.vecmath.Matrix3f crBasisT = new javax.vecmath.Matrix3f();
	private final Vector3f crCamPos = new Vector3f();
	private final Transform relTransform = new Transform();
	/** Camera world position captured for the current dotted-line batch. */
	private final Vector3f pathCamPos = new Vector3f();

	/**
	 * Loads a <em>camera-relative</em> modelview matrix (the camera's rotation only, eye at the origin)
	 * and returns the camera's world position.
	 *
	 * <p>StarMade world coordinates are large, and the engine renders the sector camera-relative for
	 * precision. If we instead load the full view (rotation + large eye translation) and then multiply by
	 * an entity's large world transform, the GPU computes {@code basis*entityPos − basis*camPos} — a
	 * subtraction of two large floats that loses precision the farther the entity is, so our overlays
	 * (boxes, paths) visibly drift off the ship at 1km+. By loading rotation-only here and subtracting the
	 * camera position from each entity position on the CPU first, the GPU only ever sees small relative
	 * offsets, so overlays stay locked to the engine-rendered geometry at any distance.</p>
	 */
	/** Multiplies the current (camera-relative) modelview by {@code worldTransform} shifted by {@code camPos}. */
	private void multCameraRelative(Transform worldTransform, Vector3f camPos) {
		relTransform.basis.set(worldTransform.basis);
		relTransform.origin.set(worldTransform.origin);
		relTransform.origin.sub(camPos);
		GlUtil.glMultMatrix(relTransform);
	}

	private Vector3f loadCameraRelativeModelview() {
		Transform view = camera.lookAt(false); // compute view (rotation + -basis*eye), don't load it
		crBasisT.set(view.basis);
		crBasisT.transpose();
		crCamPos.set(view.origin);
		crCamPos.negate();
		crBasisT.transform(crCamPos); // eye world pos = -basisᵀ * view.origin
		crView.setIdentity();
		crView.basis.set(view.basis);
		GlUtil.glLoadMatrix(crView);
		return crCamPos;
	}

	private static final Vector4f OUTLINE_SELECTED = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);   // yellow, solid
	private static final Vector4f OUTLINE_SELECTED_OCCLUDED = new Vector4f(1.0f, 1.0f, 0.0f, 0.15f);
	private static final Vector4f OUTLINE_HOVERED = new Vector4f(1.0f, 1.0f, 1.0f, 0.6f);    // white, slightly transparent
	private static final Vector4f OUTLINE_HOVERED_OCCLUDED = new Vector4f(1.0f, 1.0f, 1.0f, 0.08f);
	private static final Vector4f OUTLINE_TURRET = new Vector4f(0.0f, 1.0f, 1.0f, 1.0f);     // cyan, solid
	private static final Vector4f OUTLINE_TURRET_OCCLUDED = new Vector4f(0.0f, 1.0f, 1.0f, 0.15f);
	private static TacticalMapGUIDrawer instance;
	public final Vector3f labelOffset;
	public final ConcurrentHashMap<Integer, TacticalMapEntityIndicator> drawMap = new ConcurrentHashMap<>();
	public final ConcurrentLinkedQueue<SegmentController> selectedEntities = new ConcurrentLinkedQueue<>();
	public final ConcurrentHashMap<Integer, Ship> selectedTurrets = new ConcurrentHashMap<>(); // entity ID → turret Ship
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
		labelOffset = new Vector3f(0.0f, -20.0f, 0.0f);
		updateTimer = 150;
	}

	/**
	 * Maximum distance (from the controlled ship) at which entities are drawn/selectable.
	 * Computed live from the current server sector size so it never freezes on a stale value,
	 * and shared with the camera pan clamp so anything visible can always be reached.
	 */
	public float getMaxDrawDistance() {
		return getSectorSize() * ConfigManager.getMainConfig().tacticalMapViewDistance.value.floatValue();
	}

	/** Current server sector size, read live so it always reflects the synced server config. */
	public int getSectorSize() {
		return (int) ServerConfig.SECTOR_SIZE.getCurrentState();
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

	public void removeAll() {
		clearSelected();
	}

	public void toggleSelectAllFriendly() {
		clearSelected();
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(indicator.getEntity() != null && indicator.getEntity().getFactionId() == GameClient.getClientPlayerState().getFactionId() && GameClient.getClientPlayerState().getFactionId() != 0 && !indicator.getEntity().isDocked() && !isOwnShip(indicator.getEntity())) {
				if(isSelected(indicator.getEntity())) {
					removeSelection(indicator);
				} else {
					addSelection(indicator);
				}
			}
		}
	}

	private boolean isOwnShip(SegmentController entity) {
		return PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()).equals(entity.railController.getRoot());
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
			boolean turretMode = controlManager.turretTargetingMode;
			java.util.HashSet<Integer> currentIds = new java.util.HashSet<>();
			for(SimpleTransformableSendableObject<?> object : GameClient.getClientState().getCurrentSectorEntities().values()) {
				if(object instanceof SegmentController) {
					// Turret mode shows only turrets (docked entities) so main ships don't get in the
					// way of selecting them; normal mode shows only main ships (non-docked).
					boolean isDocked = ((SegmentController) object).isDocked();
					if(isDocked != turretMode) {
						continue;
					}
					currentIds.add(object.getId());
					if(!drawMap.containsKey(object.getId())) {
						drawMap.put(object.getId(), new TacticalMapEntityIndicator((SegmentController) object));
					}
				}
			}
			// Drop indicators for entities that shouldn't currently be shown — those in another sector
			// (after a sector change) or filtered out by turret mode — so stale entities aren't drawn.
			drawMap.keySet().removeIf(id -> !currentIds.contains(id));
			updateTimer = 150;
		}
	}

	/** Forces the entity list to be rebuilt on the next update tick (e.g. when turret mode toggles). */
	public void requestEntityRefresh() {
		updateTimer = 0;
	}

	public boolean shouldDraw() {
		return (GameClient.getClientState().getPlayerInputs().isEmpty() || GameClient.getClientState().getController().isChatActive() || GameClient.getClientState().isInAnyStructureBuildMode() || GameClient.getClientState().isInFlightMode()) && !GameClient.getClientState().getWorldDrawer().getGameMapDrawer().isMapActive();
	}
	private static final float[] BBOX_ZS = {0, 1, 0, 1, 0, 1, 0, 1};

	private void drawGrid(float start, float spacing) {
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPushMatrix();
		float aspect = (float) GLFrame.getWidth() / GLFrame.getHeight();
		GlUtil.gluPerspective(Controller.projectionMatrix, sceneFov(), aspect, SCENE_NEAR_PLANE, sceneFarPlane(), true);
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
				if(indicator.getDistance() < getMaxDrawDistance() && indicator.getEntity() != null) {
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
			hud.addHelper(InputType.KEYBOARD, ConfigManager.getTacticalMapKey(), "Toggle Tactical Map", HudContextHelperContainer.Hos.RIGHT, ContextFilter.IMPORTANT);
		}
		if(toggleDraw) {
			hud.addHelper(InputType.MOUSE, 0, "Drag: Select / Deselect | Double-click: Focus", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.MOUSE, 2, "Open Orders (Radial)", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.MOUSE, 1, "(Hold) Rotate Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_S, "(Ctrl) Toggle Turret Mode", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_A, "(Ctrl) Select All", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
			hud.addHelper(InputType.KEYBOARD, Keyboard.KEY_X, "Reset Camera", HudContextHelperContainer.Hos.RIGHT, ContextFilter.NORMAL);
		}
	}

	/**
	 * Renders entity name labels under each ship in orthogonal mode, reusing the same
	 * screen-space positions computed in {@link #computeScreenPositions()} that drive
	 * click/hover detection — so labels always line up with their entities.
	 */
	private void drawLabels() {
		GUIElement.enableOrthogonal();

		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) {
				continue;
			}
			SegmentController entity = indicator.getEntity();
			if(entity == null || entity.isCloakedFor(getCurrentEntity())) {
				continue;
			}

			GUITextOverlay overlay = TacticalMapIndicatorPool.getInstance().acquireLabelOverlay();
			String displayText = getEntityDisplay(indicator, getCurrentEntity());
			overlay.setTextSimple(displayText);
			overlay.updateTextSize();

			// Centre horizontally on the entity and place the label just below its bounding
			// box, so the name sits under the ship.
			float x = indicator.screenX - overlay.getWidth() / 2.0f;
			float y = indicator.screenMaxY + 6.0f;
			overlay.getPos().set((int) x, (int) y, 0);
			overlay.draw();

			TacticalMapIndicatorPool.getInstance().releaseLabelOverlay(overlay);
		}

		GUIElement.disableOrthogonal();
		GlUtil.printGlError();
	}

	/**
	 * Renders targeting, defend, and movement paths only for own ships, selected, or hovered entities.
	 */
	private void drawPaths() {
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

			// Draw movement path (cyan) only for explicit move orders.
			// Mining/repair assignments have their own paths and should not show an additional move vector.
			if(indicator.getEntity() instanceof Ship) {
				Integer assignedAsteroid = null;
				Integer assignedRepairTarget = null;
				try {
					assignedAsteroid = videogoose.combattweaks.manager.MineManager.getInstance().getAssignedTarget(indicator.getEntity().getId());
				} catch(Exception ignored) {
				}
				try {
					assignedRepairTarget = videogoose.combattweaks.manager.RepairManager.getInstance().getAssignedTarget(indicator.getEntity().getId());
				} catch(Exception ignored) {
				}
				if(assignedAsteroid == null && assignedRepairTarget == null && !MoveManager.getInstance().isArrived(indicator.getEntity().getId())) {
					int shipId = indicator.getEntity().getId();
					Vector3f end = null;
					// Move-to-entity: draw to the target's live client position so the line stays correct
					// across sectors (the stored destination is in the ship's sector frame and won't line up).
					Integer moveTargetId = MoveManager.getInstance().getTargetEntityId(shipId);
					if(moveTargetId != null) {
						TacticalMapEntityIndicator targetIndicator = drawMap.get(moveTargetId);
						if(targetIndicator != null) {
							end = new Vector3f(targetIndicator.entityTransform.origin);
						} else {
							SimpleGameObject obj = (SimpleGameObject) GameCommon.getGameObject(moveTargetId);
							if(obj instanceof SegmentController && ((SegmentController) obj).getWorldTransformOnClient() != null) {
								end = new Vector3f(((SegmentController) obj).getWorldTransformOnClient().origin);
							}
						}
					} else {
						// Fixed-point move: the clicked point is already in the player's sector frame.
						Vector3f destination = MoveManager.getInstance().getAssignedDestination(shipId);
						if(destination != null) {
							end = new Vector3f(destination);
						}
					}
					if(end != null && end.length() != 0) {
						Vector3f start = new Vector3f(indicator.entityTransform.origin);
						if(Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
							startDrawDottedLine();
							drawDottedLine(start, end, PATH_CYAN);
							endDrawDottedLine();
						}
					}
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

				// Queued-order chain (faint): only for selected/hovered ships, show where the ship will go
				// after its current order — current order target -> next target -> ... Unselected ships show
				// only their current-order line (drawn above).
				if(isSelected || isHovered) {
					drawQueuedOrderChain(indicator.getEntity().getId());
				}
			}
		}
	}

	/**
	 * Draws the faint chain of upcoming (queued) orders for a ship: a line from its current order's target
	 * to the next order's target, and so on. The current-order line itself is drawn by the per-type logic
	 * above; this only draws the segments between successive order targets.
	 */
	private void drawQueuedOrderChain(int shipId) {
		int[] chain = videogoose.combattweaks.manager.OrderQueueManager.getInstance().getOrderTargetIds(shipId);
		if(chain.length < 2) {
			return; // nothing queued beyond the active order
		}
		Vector3f prev = orderTargetPos(chain[0]);
		for(int i = 1; i < chain.length; i++) {
			Vector3f next = orderTargetPos(chain[i]);
			if(prev != null && next != null && Vector3fTools.distance(prev.x, prev.y, prev.z, next.x, next.y, next.z) > 1.0f) {
				startDrawDottedLine();
				drawDottedLine(prev, next, PATH_QUEUED);
				endDrawDottedLine();
			}
			if(next != null) {
				prev = next;
			}
		}
	}

	/** Client-frame world position of an order's target entity, or null if it can't be resolved. */
	private Vector3f orderTargetPos(int targetId) {
		TacticalMapEntityIndicator indicator = drawMap.get(targetId);
		if(indicator != null) {
			return new Vector3f(indicator.entityTransform.origin);
		}
		SimpleGameObject obj = (SimpleGameObject) GameCommon.getGameObject(targetId);
		if(obj instanceof SegmentController && ((SegmentController) obj).getWorldTransformOnClient() != null) {
			return new Vector3f(((SegmentController) obj).getWorldTransformOnClient().origin);
		}
		return null;
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
		GlUtil.gluPerspective(Controller.projectionMatrix, sceneFov(), aspect, SCENE_NEAR_PLANE, sceneFarPlane(), true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		pathCamPos.set(loadCameraRelativeModelview()); // capture camera pos for this batch; endpoints drawn relative to it
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
		float dottedSize = Math.min(Math.max(8, len * 0.1f), 40);
		float gap = dottedSize;
		float cycle = dottedSize + gap;
		// Advance exactly one dash-cycle per SCROLL_PERIOD_MS regardless of line length, so the
		// marching speed looks the same up close as far away. (Previously phase advanced at a fixed
		// world-units rate, so a short line — small cycle — wrapped many times per second and
		// appeared to scroll frantically as a ship neared its target.)
		float fraction = (System.currentTimeMillis() % SCROLL_PERIOD_MS) / (float) SCROLL_PERIOD_MS;
		float phase = fraction * cycle;
		GlUtil.glColor4f(color);
		for(float f = phase - cycle; f < len; f += cycle) {
			if(f + dottedSize <= 0) {
				continue;
			}
			float segStart = Math.max(0.0f, f);
			float segEnd = Math.min(len, f + dottedSize);
			if(segEnd <= segStart) {
				continue;
			}
			a.set(dottedDirN);
			a.scale(segStart);
			b.set(dottedDirN);
			b.scale(segEnd);
			// Camera-relative: subtract the captured camera position so GPU coords stay small (precision).
			GL11.glVertex3f(from.x - pathCamPos.x + a.x, from.y - pathCamPos.y + a.y, from.z - pathCamPos.z + a.z);
			GL11.glVertex3f(from.x - pathCamPos.x + b.x, from.y - pathCamPos.y + b.y, from.z - pathCamPos.z + b.z);
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

		syncViewport();

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
	 * Re-syncs the engine's cached GL viewport ({@link Controller#viewport}, what {@link GLFrame#getWidth()}
	 * and {@code GUIElement.enableOrthogonal()} read) to the real window size.
	 *
	 * <p>The map projects entities to screen with the live {@code GL_VIEWPORT} but positions labels and
	 * sizes its perspective from the cached viewport. The engine only refreshes that cache when it catches
	 * a {@code Display.wasResized()} event — and a maximize/resize that lands during startup (before the GL
	 * context is tracking resizes) can be missed, leaving the cache stuck at the initial size for the rest
	 * of the session. Everything the map draws then ends up offset/mis-scaled. We self-heal: if the real
	 * window size differs from the cached viewport, set the GL viewport and refresh the cache so all the
	 * map's matrices (and the GUI ortho) agree with what's actually on screen.</p>
	 */
	private void syncViewport() {
		try {
			int dw = Display.getWidth();
			int dh = Display.getHeight();
			if(dw > 0 && dh > 0 && (dw != GLFrame.getWidth() || dh != GLFrame.getHeight())) {
				GL11.glViewport(0, 0, dw, dh);
				Controller.onViewportChange();
			}
		} catch(Exception ignored) {
		}
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
		GlUtil.gluPerspective(Controller.projectionMatrix, sceneFov(), aspect, SCENE_NEAR_PLANE, sceneFarPlane(), true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		// Camera-relative modelview (rotation only); project points as (worldPos - camPos) for precision.
		Vector3f camPos = loadCameraRelativeModelview();

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
			// Project entity center for label placement (camera-relative: subtract camera position)
			Vector3f pos = indicator.entityTransform.origin;
			GL_WIN_COORDS.clear();
			boolean ok = Project.gluProject(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z, GL_MODELVIEW, GL_PROJECTION, GL_VIEWPORT, GL_WIN_COORDS);
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
				tmpBboxCorner.sub(camPos); // camera-relative
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
	 * Distance is shown from the tactical map camera position, matching sector indicator behaviour.
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
				// Use tactical-map camera position as the reference, same way sector indicators do it.
				Vector3f camPos = camera.getPos();
				Vector3f entityPos = indicator.entityTransform.origin;
				float camDist = Vector3fTools.distance(camPos.x, camPos.y, camPos.z, entityPos.x, entityPos.y, entityPos.z);
				builder.append(StringTools.formatDistance(camDist)).append("\n");
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
		GlUtil.gluPerspective(Controller.projectionMatrix, sceneFov(), aspect, SCENE_NEAR_PLANE, sceneFarPlane(), true);
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
		GlUtil.glPushMatrix();
		GlUtil.glLoadIdentity();
		Vector3f camPos = loadCameraRelativeModelview();
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
			multCameraRelative(indicator.entityTransform, camPos);
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
			multCameraRelative(indicator.entityTransform, camPos);
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
				multCameraRelative(turret.getWorldTransform(), camPos);
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
				multCameraRelative(turret.getWorldTransform(), camPos);
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
	public void applyDragSelection(float minX, float minY, float maxX, float maxY) {
		for(TacticalMapEntityIndicator indicator : drawMap.values()) {
			if(!indicator.screenPosValid) continue;
			if(!canSelect(indicator.getEntity())) continue;
			// Overlap test: two AABBs overlap if neither is fully outside the other
			if(indicator.screenMaxX < minX || indicator.screenMinX > maxX || indicator.screenMaxY < minY || indicator.screenMinY > maxY) {
				continue;
			}
			// Toggle: drag over unselected ships to select them, over already-selected ships to
			// deselect them. Use empty-space click to clear the whole selection.
			if(isSelected(indicator.getEntity())) {
				removeSelection(indicator);
			} else {
				addSelection(indicator);
			}
		}
	}

	private boolean canSelect(SegmentController entity) {
		return GameCommon.getGameState().getFactionManager().isFriend(entity.getFactionId(), GameClient.getClientState().getPlayer().getFactionId()) && entity.getFactionId() != 0;
	}
}