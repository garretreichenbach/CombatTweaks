package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.client.controller.manager.AbstractControlManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.navigation.NavigationFilter;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.common.controller.Ship;
import org.schema.game.server.data.GameServerState;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.graphicsengine.camera.CameraMouseState;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.KeyboardMappings;
import videogoose.combattweaks.manager.ConfigManager;

import javax.vecmath.Vector3f;

public class TacticalMapControlManager extends AbstractControlManager {

	private final TacticalMapGUIDrawer guiDrawer;
	public float viewDistance;

	static final float ENTITY_CLICK_THRESHOLD_PX = 40.0f;
	private static final long DOUBLE_CLICK_TIME_MS = 300;
	private static final int DOUBLE_CLICK_DISTANCE_PX = 10;
	private static final int DRAG_THRESHOLD_PX = 6;
	private boolean wasLeftMouseDown;
	private boolean wasMiddleMouseDown;
	public boolean turretTargetingMode;
	private boolean wasADown;
	private boolean wasSDown;
	/** The currently open radial menu, if any. Tracked so a new one closes the old instead of stacking. */
	private TacticalMapRadial activeRadial;
	/** Set while/after a radial is open to swallow the dismiss-click so it doesn't reopen a radial. */
	private boolean suppressClickUntilRelease;
	private static final float FOCUS_DISTANCE = 300.0f;
	private static final float FOCUS_ELEVATION_ANGLE = 0.3f; // ~23 degrees above horizontal
	private int lastClickX;
	private int lastClickY;
	private TacticalMapEntityIndicator pendingClickIndicator;
	private long pendingClickTime;

	public TacticalMapControlManager(TacticalMapGUIDrawer guiDrawer) {
		super(GameClient.getClientState());
		this.guiDrawer = guiDrawer;
		viewDistance = ConfigManager.getMainConfig().tacticalMapViewDistance.value.floatValue();
	}

	@Override
	public void handleKeyEvent(KeyEventInterface keyEvent) {
		if(isActive() && !isSuspended() && !isHinderedInteraction() && getState().getPlayerInputs().isEmpty()) {
			super.handleKeyEvent(keyEvent);
		}
	}

	@Override
	public void handleMouseEvent(MouseEvent mouseEvent) {
		if(isActive() && !isSuspended() && !isHinderedInteraction() && getState().getPlayerInputs().isEmpty()) {
			super.handleMouseEvent(mouseEvent);
		}
	}

	@Override
	public void onSwitch(boolean active) {
		guiDrawer.clearSelected();
		if(!active) {
			// Leaving turret mode restores the main-ship view; clear turret state and the docked
			// navigation filter so nothing carries over after the map is dismissed.
			turretTargetingMode = false;
			guiDrawer.clearSelectedTurrets();
			getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getNavigationControlManager().getFilter().setFilter(false, NavigationFilter.POW_DOCKED);
			// Close any open radial so it doesn't linger after the map is dismissed.
			if(activeRadial != null && activeRadial.isActive()) {
				activeRadial.deactivate();
			}
			activeRadial = null;
		}
		// Mirror every suspend() that update() applies while the map is open, so closing the map fully
		// restores control. Missing the interaction manager and build-tools manager here left build mode
		// locked after exiting via a build block (update() suspends them every frame, but only these two
		// flight/build controllers were being released on exit).
		getInteractionManager().setActive(!active);
		getInteractionManager().suspend(active);
		getInteractionManager().getBuildToolsManager().suspend(active);
		getInteractionManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().suspend(active);
		getInteractionManager().getInShipControlManager().getShipControlManager().getSegmentBuildController().suspend(active);
		super.onSwitch(active);
	}

	@Override
	public void update(Timer timer) {
		if(!Display.isActive()) {
			// Window lost focus — release mouse grab and reset stuck input state
			CameraMouseState.setGrabbed(false);
			wasLeftMouseDown = false;
			guiDrawer.isDragSelecting = false;
			return;
		}
		CameraMouseState.setGrabbed(Mouse.isButtonDown(1));
		getInteractionManager().suspend(true);
		getInteractionManager().setActive(false);
		getInteractionManager().getBuildToolsManager().suspend(true);
		getInteractionManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().suspend(true);
		getInteractionManager().getInShipControlManager().getShipControlManager().getSegmentBuildController().suspend(true);
		handleInteraction(timer);
	}
	private int dragAnchorX;
	/** Entity the camera is tracking after a double-click focus (-1 = not tracking). Cleared on WASD pan. */
	private int focusedEntityId = -1;
	private final Vector3f lastTrackPos = new Vector3f();
	private boolean hasTrackPos;
	/** Entity the camera is animating its rotation toward (to face it), or -1 when not animating. */
	private int faceAnimId = -1;
	/** True while the player is dragging the selection-panel scrollbar thumb. */
	private boolean draggingThumb;

	// Double-tap-to-jump state, one slot per movement direction (forward/back/left/right/up/down).
	private static final long DOUBLE_TAP_MS = 280;
	private final long[] lastTapTime = new long[6];
	private final boolean[] tapKeyWasDown = new boolean[6];

	private static int tapKeyMapping(int i) {
		switch(i) {
			case 0:
				return KeyboardMappings.FORWARD.getMapping();
			case 1:
				return KeyboardMappings.BACKWARDS.getMapping();
			case 2:
				return KeyboardMappings.STRAFE_LEFT.getMapping();
			case 3:
				return KeyboardMappings.STRAFE_RIGHT.getMapping();
			case 4:
				return KeyboardMappings.UP.getMapping();
			default:
				return KeyboardMappings.DOWN.getMapping();
		}
	}

	/**
	 * Detects a quick double-tap of a movement key and jumps the camera a discrete step in that direction:
	 * one subsector normally, a full sector with Shift held. Continuous holding still pans as before.
	 */
	private void handleMovementTaps() {
		// Don't fire while a modifier owns the movement keys (Ctrl+A etc.), but keep edge state fresh.
		boolean modifier = Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL);
		boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT);
		float s = guiDrawer.getSectorSize();
		int div = Math.max(1, Math.min(8, (int) Math.round(ConfigManager.getMainConfig().tacticalMapSubsectorDivisions.getValue())));
		float jumpDist = shift ? s : s / div;
		long now = System.currentTimeMillis();
		for(int i = 0; i < 6; i++) {
			boolean down = Keyboard.isKeyDown(tapKeyMapping(i));
			if(!modifier && down && !tapKeyWasDown[i]) {
				if(now - lastTapTime[i] <= DOUBLE_TAP_MS) {
					jumpCamera(i, jumpDist);
					lastTapTime[i] = 0; // consume so a third tap needs a fresh pair
				} else {
					lastTapTime[i] = now;
				}
			}
			tapKeyWasDown[i] = down;
		}
	}

	/** Instantly shifts the camera {@code dist} in movement-direction {@code i} (matching the WASD axes). */
	private void jumpCamera(int i, float dist) {
		Vector3f m = new Vector3f();
		switch(i) {
			case 0:
				m.z = dist;
				break; // forward
			case 1:
				m.z = -dist;
				break; // back
			case 2:
				m.x = dist;
				break; // strafe left
			case 3:
				m.x = -dist;
				break; // strafe right
			case 4:
				m.y = dist;
				break; // up
			default:
				m.y = -dist;
				break; // down
		}
		focusedEntityId = -1; // a manual jump releases entity tracking
		faceAnimId = -1;
		hasTrackPos = false;
		move(m);
	}

	/**
	 * While focused on an entity, translate the camera by the entity's movement each frame so it stays framed
	 * — the camera follows it. Rotation (right-drag) is unaffected; a WASD pan clears the focus (handled in
	 * {@link #handleInteraction}).
	 */
	private void updateFocusTracking() {
		if(focusedEntityId == -1 || guiDrawer.camera == null) {
			return;
		}
		// Resolve the CLIENT entity (not via GameCommon, which returns the SERVER instance in single-player —
		// calling getWorldTransformOnClient() on a server entity casts its state to GameClientState and throws).
		SegmentController e = null;
		try {
			org.schema.schine.network.objects.Sendable s = GameClient.getClientState().getLocalAndRemoteObjectContainer().getLocalObjects().get(focusedEntityId);
			if(s instanceof SegmentController) {
				e = (SegmentController) s;
			}
		} catch(Exception ignored) {
		}
		if(e == null) {
			focusedEntityId = -1;
			hasTrackPos = false;
			return;
		}
		// Use the rebased client transform — the SAME frame the camera and focusCameraOnEntity use. The raw
		// world transform is sector-local, so for an other-sector ship its origin is in a different frame and
		// the first tracking delta would be a whole-sector jump (tracking would "not work" across sectors).
		Transform ct = e.getWorldTransformOnClient();
		if(ct == null) {
			return;
		}
		Vector3f cur = ct.origin;
		if(hasTrackPos) {
			Vector3f cam = guiDrawer.camera.getWorldTransform().origin;
			cam.x += cur.x - lastTrackPos.x;
			cam.y += cur.y - lastTrackPos.y;
			cam.z += cur.z - lastTrackPos.z;
		}
		lastTrackPos.set(cur);
		hasTrackPos = true;
	}

	/** Resolves the client-side entity for an id (server instance would throw in getWorldTransformOnClient). */
	private SegmentController resolveClientEntity(int id) {
		try {
			org.schema.schine.network.objects.Sendable s = GameClient.getClientState().getLocalAndRemoteObjectContainer().getLocalObjects().get(id);
			if(s instanceof SegmentController) {
				return (SegmentController) s;
			}
		} catch(Exception ignored) {
		}
		return null;
	}

	/**
	 * Eases the camera's orientation toward looking at the focused entity (shortest-arc quaternion slerp).
	 * Recomputed each frame so it still ends up facing the entity even if it moved during the turn. A manual
	 * right-drag rotation cancels it.
	 */
	private void updateFaceRotation(float dt) {
		if(faceAnimId == -1 || guiDrawer.camera == null) {
			return;
		}
		if(Mouse.isButtonDown(1)) {
			faceAnimId = -1; // player is rotating the camera by hand — stop fighting them
			return;
		}
		SegmentController e = resolveClientEntity(faceAnimId);
		if(e == null) {
			faceAnimId = -1;
			return;
		}
		Transform ct = e.getWorldTransformOnClient();
		if(ct == null) {
			return;
		}
		Vector3f camPos = guiDrawer.camera.getWorldTransform().origin;
		Vector3f fwd = new Vector3f();
		fwd.sub(ct.origin, camPos);
		if(fwd.lengthSquared() < 1.0e-6f) {
			faceAnimId = -1;
			return;
		}
		fwd.normalize();
		Vector3f worldUp = new Vector3f(0, 1, 0);
		if(Math.abs(fwd.dot(worldUp)) > 0.99f) {
			worldUp.set(0, 0, 1);
		}
		// Build a PROPER right-handed basis matching the camera's column convention (col0=right, col1=up,
		// col2=forward, with right x up = forward). right = worldUp x forward (NOT forward x worldUp) keeps the
		// determinant at +1; an improper (det -1) basis converts to a garbage quaternion and rolls the whole view.
		Vector3f right = new Vector3f();
		right.cross(worldUp, fwd);
		if(right.lengthSquared() < 1.0e-6f) {
			right.set(1, 0, 0);
		} else {
			right.normalize();
		}
		Vector3f up = new Vector3f();
		up.cross(fwd, right);
		up.normalize();

		// Build the target orientation via the camera's own vector setters (so it matches the basis convention).
		Transform tmp = new Transform();
		tmp.setIdentity();
		org.schema.schine.graphicsengine.core.GlUtil.setForwardVector(fwd, tmp);
		org.schema.schine.graphicsengine.core.GlUtil.setRightVector(right, tmp);
		org.schema.schine.graphicsengine.core.GlUtil.setUpVector(up, tmp);
		javax.vecmath.Quat4f tgtQ = new javax.vecmath.Quat4f();
		tgtQ.set(tmp.basis);
		javax.vecmath.Quat4f curQ = new javax.vecmath.Quat4f();
		curQ.set(guiDrawer.camera.getWorldTransform().basis);
		float alpha = Math.min(1.0f, dt * 8.0f);
		curQ.interpolate(tgtQ, alpha); // vecmath slerp, shortest path
		guiDrawer.camera.getWorldTransform().basis.set(curQ);
		float dot = Math.abs(curQ.x * tgtQ.x + curQ.y * tgtQ.y + curQ.z * tgtQ.z + curQ.w * tgtQ.w);
		if(dot > 0.99995f) {
			faceAnimId = -1; // aligned — done
		}
	}

	private void move(Vector3f movement) {
		Vector3f move = new Vector3f();
		Vector3f forward = new Vector3f(guiDrawer.camera.getForward());
		Vector3f up = new Vector3f(guiDrawer.camera.getUp());
		Vector3f right = new Vector3f(guiDrawer.camera.getRight());
		if(movement.x != 0) {
			right.scale(movement.x);
			move.add(right);
		}
		if(movement.y != 0) {
			up.scale(movement.y);
			move.add(up);
		}
		if(movement.z != 0) {
			forward.scale(movement.z);
			move.add(forward);
		}
		Vector3f newPos = new Vector3f(guiDrawer.camera.getWorldTransform().origin);
		newPos.add(move);
		// Allow the camera to pan to anything that is drawn/selectable (same range as the draw cull),
		// plus one sector of slack so entities at the very edge can still be centred and clicked.
		float maxCameraDistance = guiDrawer.getMaxDrawDistance() + (int) ServerConfig.SECTOR_SIZE.getCurrentState();
		if(getDistanceFromControl(newPos) < maxCameraDistance) {
			guiDrawer.camera.getWorldTransform().origin.set(newPos);
		}
	}

	private float getDistanceFromControl(Vector3f newPos) {
		// Anchor to whatever the player controls — ship, station, or astronaut character — so panning limits
		// work regardless of context, not only while piloting a ship.
		SimpleTransformableSendableObject<?> control = GameClient.getClientPlayerState() != null ? GameClient.getClientPlayerState().getFirstControlledTransformableWOExc() : null;
		if(control != null) {
			Vector3f controlPos = control.getWorldTransform().origin;
			return Math.abs(Vector3fTools.distance(newPos.x, newPos.y, newPos.z, controlPos.x, controlPos.y, controlPos.z));
		}
		return (int) ServerConfig.SECTOR_SIZE.getCurrentState();
	}

	private PlayerInteractionControlManager getInteractionManager() {
		return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
	}

	private int dragAnchorY;

	private void handleInteraction(Timer timer) {
		// While a radial menu (a PlayerInput) is open it handles its own mouse input. This method polls
		// the mouse directly every frame, so without this guard the click that selects a radial item is
		// ALSO read here as a click on the entity behind the radial, queuing a pending click that
		// reopens a radial. Skip interaction while it's open, and keep swallowing input until the
		// dismiss-click is fully released (the radial closes on mouse-press, before the release).
		if(activeRadial != null && activeRadial.isActive()) {
			suppressClickUntilRelease = true;
			wasLeftMouseDown = Mouse.isButtonDown(0);
			wasMiddleMouseDown = Mouse.isButtonDown(2);
			pendingClickIndicator = null;
			guiDrawer.isDragSelecting = false;
			return;
		}
		if(suppressClickUntilRelease) {
			wasLeftMouseDown = Mouse.isButtonDown(0);
			wasMiddleMouseDown = Mouse.isButtonDown(2);
			if(Mouse.isButtonDown(0) || Mouse.isButtonDown(2)) {
				return; // still holding the button that dismissed the radial
			}
			suppressClickUntilRelease = false; // released — resume normal handling next frame
			return;
		}
		if(!getState().getGlobalGameControlManager().getIngameControlManager().isAnyMenuOrChatActive()) {
			// Follow a focused entity (camera translates with it); rotation doesn't disturb this.
			updateFocusTracking();
			updateFaceRotation(timer.getDelta());
			Vector3f movement = new Vector3f();
			int amount = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1000 : 100;
			if(Keyboard.isKeyDown(Keyboard.KEY_X)) {
				guiDrawer.camera.reset();
				focusedEntityId = -1; // reset camera also stops tracking
				faceAnimId = -1;
				hasTrackPos = false;
			}
			// Edge-detected Ctrl+A / Ctrl+S commands
			boolean aDown = Keyboard.isKeyDown(Keyboard.KEY_A);
			boolean sDown = Keyboard.isKeyDown(Keyboard.KEY_S);
			if(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
				if(aDown && !wasADown) {
					guiDrawer.toggleSelectAllFriendly();
				} else if(sDown && !wasSDown) {
					turretTargetingMode = !turretTargetingMode;
					// Keep the selection across the toggle. Targets (enemy main ships) are only visible in
					// normal mode, so the flow is: enter turret mode, pick turrets, leave turret mode, then
					// middle-click the target to order the attack — clearing here would drop the turrets
					// before you could command them. The selected turrets stay highlighted in both views.
					// Swap the visible entities (main ships <-> turrets) immediately instead of waiting
					// for the next periodic rebuild.
					guiDrawer.requestEntityRefresh();
					getState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getNavigationControlManager().getFilter().setFilter(turretTargetingMode, NavigationFilter.POW_DOCKED);
				}
			}
			wasADown = aDown;
			wasSDown = sDown;

			// Skip camera movement when Ctrl or Meta is held to avoid conflicts with selection commands
			if(!Keyboard.isKeyDown(Keyboard.KEY_LMETA) && !Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
				if(Keyboard.isKeyDown(KeyboardMappings.FORWARD.getMapping())) {
					movement.add(new Vector3f(0, 0, amount));
				}
				if(Keyboard.isKeyDown(KeyboardMappings.BACKWARDS.getMapping())) {
					movement.add(new Vector3f(0, 0, -amount));
				}
				if(Keyboard.isKeyDown(KeyboardMappings.STRAFE_LEFT.getMapping())) {
					movement.add(new Vector3f(amount, 0, 0));
				}
				if(Keyboard.isKeyDown(KeyboardMappings.STRAFE_RIGHT.getMapping())) {
					movement.add(new Vector3f(-amount, 0, 0));
				}
				if(Keyboard.isKeyDown(KeyboardMappings.UP.getMapping())) {
					movement.add(new Vector3f(0, amount, 0));
				}
				if(Keyboard.isKeyDown(KeyboardMappings.DOWN.getMapping())) {
					movement.add(new Vector3f(0, -amount, 0));
				}
			}
			if(movement.lengthSquared() > 0.0f) {
				focusedEntityId = -1; // a manual WASD pan releases entity tracking
				faceAnimId = -1;
				hasTrackPos = false;
			}
			movement.scale(timer.getDelta());
			move(movement);
			// Double-tap-to-jump is disabled — it triggered too easily during normal panning.
			// handleMovementTaps();
			if(Mouse.hasWheel() && Mouse.getEventDWheel() != 0 && guiDrawer.isOverSelectionPanel(Mouse.getX(), GLFrame.getHeight() - Mouse.getY())) {
				// Hovering the selection panel: scroll it instead of zooming the map.
				guiDrawer.scrollSelectionPanel(Mouse.getEventDWheel() > 0 ? -48.0f : 48.0f);
			} else if(Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
				float wheelAmount = (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) ? 1000 : 100;
				if(Mouse.getEventDWheel() == 0) {
					wheelAmount = 0;
				} else if(Mouse.getEventDWheel() < 0) {
					wheelAmount *= -1;
				}
				float newRange = guiDrawer.selectedRange + wheelAmount;
				if(newRange > 20000) {
					newRange = 20000;
				} else if(newRange < 100) {
					newRange = 100;
				}
				guiDrawer.selectedRange = newRange;
			}

			boolean isLeftDown = Mouse.isButtonDown(0);
			boolean rightDown = Mouse.isButtonDown(1);
			boolean isMiddleDown = Mouse.isButtonDown(2);
			int mouseX = Mouse.getX();
			int mouseY = GLFrame.getHeight() - Mouse.getY();

			// Cancel drag if RMB is held (camera rotation takes priority)
			if(rightDown && guiDrawer.isDragSelecting) {
				guiDrawer.isDragSelecting = false;
			}

			// LMB just pressed: grab the scrollbar thumb if it's under the cursor, else record a drag anchor.
			if(isLeftDown && !wasLeftMouseDown && !rightDown) {
				if(guiDrawer.isOverScrollThumb(mouseX, mouseY)) {
					draggingThumb = true;
				} else {
					dragAnchorX = mouseX;
					dragAnchorY = mouseY;
				}
			}

			// LMB held: update drag rect once threshold is exceeded (but never start a drag from inside the
			// config panel — that's a click target, not a selection region).
			if(isLeftDown && !rightDown) {
				if(draggingThumb) {
					guiDrawer.scrollPanelToThumbY(mouseY);
				} else {
					int dx = mouseX - dragAnchorX;
					int dy = mouseY - dragAnchorY;
					if(!guiDrawer.isDragSelecting && (dx * dx + dy * dy) > DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX && !guiDrawer.isInConfigPanel(dragAnchorX, dragAnchorY)) {
						guiDrawer.isDragSelecting = true;
					}
					if(guiDrawer.isDragSelecting) {
						guiDrawer.dragMinX = Math.min(dragAnchorX, mouseX);
						guiDrawer.dragMaxX = Math.max(dragAnchorX, mouseX);
						guiDrawer.dragMinY = Math.min(dragAnchorY, mouseY);
						guiDrawer.dragMaxY = Math.max(dragAnchorY, mouseY);
					}
				}
			}

			// Expire a stale single-click once the double-click window has passed. A single left-click
			// has no action of its own — it only exists to detect a double-click to focus the camera.
			if(pendingClickIndicator != null && (System.currentTimeMillis() - pendingClickTime) >= DOUBLE_CLICK_TIME_MS) {
				pendingClickIndicator = null;
			}

			// LMB released — selection and double-click-to-focus only. The radial is opened with the
			// middle mouse button (below) so it no longer conflicts with selecting/focusing on left-click.
			if(!isLeftDown && wasLeftMouseDown && !rightDown) {
				if(draggingThumb) {
					draggingThumb = false; // released the scrollbar thumb — don't select/focus
				} else if(guiDrawer.isDragSelecting) {
					// Commit marquee selection (toggles entities in the box: selects unselected,
					// deselects already-selected)
					// Plain drag replaces the selection; hold a modifier (Shift/Ctrl) to add/toggle.
					guiDrawer.applyDragSelection(guiDrawer.dragMinX, guiDrawer.dragMinY,
							guiDrawer.dragMaxX, guiDrawer.dragMaxY, hasModifierKeyPressed());
					guiDrawer.isDragSelecting = false;
				} else if(guiDrawer.handleConfigClick(mouseX, mouseY) || guiDrawer.handlePanelClick(mouseX, mouseY)) {
					// Click consumed by the settings panel or a pin button — don't select/focus/clear.
				} else {
					TacticalMapEntityIndicator hit = guiDrawer.findIndicatorAtScreen(mouseX, mouseY, ENTITY_CLICK_THRESHOLD_PX);
					if(hit != null) {
						if(turretTargetingMode) {
							handleTurretTargeting(hit.getEntity());
						} else if(pendingClickIndicator != null) {
							// Second click within the window — double-click to focus the camera
							int deltaX = mouseX - lastClickX;
							int deltaY = mouseY - lastClickY;
							int distSq = deltaX * deltaX + deltaY * deltaY;
							if(distSq < DOUBLE_CLICK_DISTANCE_PX * DOUBLE_CLICK_DISTANCE_PX) {
								focusCameraOnEntity(hit.getEntity());
								pendingClickIndicator = null;
							} else {
								// Too far from the first click — restart double-click detection
								pendingClickIndicator = hit;
								pendingClickTime = System.currentTimeMillis();
								lastClickX = mouseX;
								lastClickY = mouseY;
							}
						} else {
							// First click — remember it so a second click can be read as a double-click
							pendingClickIndicator = hit;
							pendingClickTime = System.currentTimeMillis();
							lastClickX = mouseX;
							lastClickY = mouseY;
						}
					} else if(!hasModifierKeyPressed()) {
						// Clicked empty space — clear selection
						guiDrawer.clearSelected();
					}
				}
			} else if(isMiddleDown && !wasMiddleMouseDown && !rightDown) {
				// Middle-click opens the radial: targeting the entity under the cursor if there is one,
				// otherwise the orders-only menu (idle, etc.) when a selection exists. Edge-triggered so
				// it opens once per press rather than every frame the button is held.
				TacticalMapEntityIndicator hit = guiDrawer.findIndicatorAtScreen(mouseX, mouseY, ENTITY_CLICK_THRESHOLD_PX);
				if(hit != null) {
					openRadial(hit);
				} else if(!guiDrawer.selectedEntities.isEmpty()) {
					openRadial(null);
				}
			}

			wasLeftMouseDown = isLeftDown;
			wasMiddleMouseDown = isMiddleDown;
		}
	}

	/**
	 * Opens a tactical-map radial for the given target (null = orders-only center menu),
	 * closing any radial that is still open first so instances never stack.
	 */
	private void openRadial(TacticalMapEntityIndicator target) {
		if(activeRadial != null && activeRadial.isActive()) {
			activeRadial.deactivate();
		}
		activeRadial = new TacticalMapRadial(guiDrawer, target);
		activeRadial.activate();
	}

	private boolean hasModifierKeyPressed() {
		return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_LMENU);
	}

	/**
	 * Focus the tactical map camera on a specific entity RTS-style:
	 * positions the camera at a fixed distance above and behind the entity,
	 * preserving the current horizontal orbit angle but looking down at the target.
	 */
	private void focusCameraOnEntity(SegmentController entity) {
		if(entity == null || guiDrawer.camera == null) return;

		// Use the engine's client render transform, which rebases entities in other sectors into the player's
		// sector frame — the same frame the camera and all the map's drawables live in. The raw world
		// transform is sector-local, so for a contact in another sector it would send the camera to a point
		// a whole sector (or more) away from where the entity is actually drawn.
		com.bulletphysics.linearmath.Transform ct = entity.getWorldTransformOnClient();
		Vector3f target = (ct != null ? ct : entity.getWorldTransform()).origin;

		// Preserve current camera heading (yaw) so orientation feels natural
		Vector3f camPos = guiDrawer.camera.getWorldTransform().origin;
		float dx = camPos.x - target.x;
		float dz = camPos.z - target.z;
		float yaw = (float) Math.atan2(dx, dz); // horizontal angle from entity

		// Place camera at FOCUS_DISTANCE away, elevated by FOCUS_ELEVATION_ANGLE radians
		float hDist = (float) (FOCUS_DISTANCE * Math.cos(FOCUS_ELEVATION_ANGLE));
		float vDist = (float) (FOCUS_DISTANCE * Math.sin(FOCUS_ELEVATION_ANGLE));

		Vector3f newPos = new Vector3f(target.x + (float) Math.sin(yaw) * hDist, target.y + vDist, target.z + (float) Math.cos(yaw) * hDist);
		guiDrawer.camera.getWorldTransform().origin.set(newPos);

		// Camera auto-rotation on focus is disabled: building a look-at orientation and slerping the camera's
		// (view-convention) basis rolls the whole view. We only reposition + track; the camera keeps its
		// existing heading. (updateFaceRotation is left in place but never armed.)
		// faceAnimId = entity.getId();

		// Begin tracking: the camera will follow this entity until the player pans (WASD).
		focusedEntityId = entity.getId();
		lastTrackPos.set(target);
		hasTrackPos = true;
	}

	/**
	 * Handle turret targeting mode when Ctrl+Click is used.
	 */
	private void handleTurretTargeting(SegmentController entity) {
		if(entity == null) {
			return;
		}

		// The map shows one marker per turret unit (the entity docked to the ship); clicking it toggles
		// selection. It also goes into the normal selection set so the order radial can command it — the
		// attack order then cascades to the unit's weapon entity (the barrel), which runs its own turret AI
		// and engages with no need for the turret itself to be in a fleet.
		if(entity instanceof Ship turret && entity.isDocked()) {
			if(guiDrawer.isTurretSelected(turret)) {
				guiDrawer.removeTurretSelection(turret);
				guiDrawer.selectedEntities.remove(turret);
			} else {
				guiDrawer.addTurretSelection(turret);
				if(!guiDrawer.selectedEntities.contains(turret)) {
					guiDrawer.selectedEntities.add(turret);
				}
			}
			return;
		}

		// Fallback: clicking a main ship selects the turrets docked on it.
		new TacticalMapTurretSelector(guiDrawer, entity).activate();
	}
}