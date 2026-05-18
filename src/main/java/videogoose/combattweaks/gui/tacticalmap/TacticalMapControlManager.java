package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.client.controller.manager.AbstractControlManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.navigation.NavigationFilter;
import org.schema.game.common.controller.SegmentController;
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
	public boolean turretTargetingMode;
	private boolean wasADown;
	private boolean wasSDown;
	private static final float FOCUS_DISTANCE = 300.0f;
	private static final float FOCUS_ELEVATION_ANGLE = 0.3f; // ~23 degrees above horizontal
	private int lastClickX;
	private int lastClickY;
	private TacticalMapEntityIndicator pendingClickIndicator;
	private long pendingClickTime;

	public TacticalMapControlManager(TacticalMapGUIDrawer guiDrawer) {
		super(GameClient.getClientState());
		this.guiDrawer = guiDrawer;
		viewDistance = (float) ConfigManager.getMainConfig().getDouble("tactical_map_view_distance");
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
		if(!active) turretTargetingMode = false;
		getInteractionManager().setActive(!active);
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
		if(getDistanceFromControl(newPos) < (int) ServerConfig.SECTOR_SIZE.getCurrentState() * ConfigManager.getMainConfig().getDouble("tactical_map_view_distance")) {
			guiDrawer.camera.getWorldTransform().origin.set(newPos);
		}
	}

	private float getDistanceFromControl(Vector3f newPos) {
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			Vector3f controlPos = ((SegmentController) GameClient.getCurrentControl()).getWorldTransform().origin;
			return Math.abs(Vector3fTools.distance(newPos.x, newPos.y, newPos.z, controlPos.x, controlPos.y, controlPos.z));
		}
		return (int) ServerConfig.SECTOR_SIZE.getCurrentState();
	}

	private PlayerInteractionControlManager getInteractionManager() {
		return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
	}

	private int dragAnchorY;

	private void handleInteraction(Timer timer) {
		if(!getState().getGlobalGameControlManager().getIngameControlManager().isAnyMenuOrChatActive()) {
			Vector3f movement = new Vector3f();
			int amount = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1000 : 100;
			if(Keyboard.isKeyDown(Keyboard.KEY_X)) {
				guiDrawer.camera.reset();
			}
			// Edge-detected Ctrl+A / Ctrl+S commands
			boolean aDown = Keyboard.isKeyDown(Keyboard.KEY_A);
			boolean sDown = Keyboard.isKeyDown(Keyboard.KEY_S);
			if(Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
				if(aDown && !wasADown) {
					guiDrawer.toggleSelectAllFriendly();
				} else if(sDown && !wasSDown) {
					turretTargetingMode = !turretTargetingMode;
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
			movement.scale(timer.getDelta());
			move(movement);
			if(Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
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

			// LMB just pressed: record anchor for potential drag
			if(isLeftDown && !wasLeftMouseDown && !rightDown) {
				dragAnchorX = mouseX;
				dragAnchorY = mouseY;
			}

			// LMB held: update drag rect once threshold is exceeded
			if(isLeftDown && !rightDown) {
				int dx = mouseX - dragAnchorX;
				int dy = mouseY - dragAnchorY;
				if(!guiDrawer.isDragSelecting && (dx * dx + dy * dy) > DRAG_THRESHOLD_PX * DRAG_THRESHOLD_PX) {
					guiDrawer.isDragSelecting = true;
				}
				if(guiDrawer.isDragSelecting) {
					guiDrawer.dragMinX = Math.min(dragAnchorX, mouseX);
					guiDrawer.dragMaxX = Math.max(dragAnchorX, mouseX);
					guiDrawer.dragMinY = Math.min(dragAnchorY, mouseY);
					guiDrawer.dragMaxY = Math.max(dragAnchorY, mouseY);
				}
			}

			// Flush pending single-click if the double-click window has expired
			if(pendingClickIndicator != null && (System.currentTimeMillis() - pendingClickTime) >= DOUBLE_CLICK_TIME_MS) {
				(new TacticalMapRadial(guiDrawer, pendingClickIndicator)).activate();
				pendingClickIndicator = null;
			}

			// LMB released
			if(!isLeftDown && wasLeftMouseDown && !rightDown) {
				if(guiDrawer.isDragSelecting) {
					// Commit marquee selection
					guiDrawer.applyDragSelection(guiDrawer.dragMinX, guiDrawer.dragMinY,
							guiDrawer.dragMaxX, guiDrawer.dragMaxY,
							Keyboard.isKeyDown(Keyboard.KEY_LSHIFT));
					guiDrawer.isDragSelecting = false;
				} else {
					TacticalMapEntityIndicator hit = guiDrawer.findIndicatorAtScreen(mouseX, mouseY, ENTITY_CLICK_THRESHOLD_PX);
					if(hit != null) {
						if(turretTargetingMode) {
							handleTurretTargeting(hit.getEntity());
						} else if(pendingClickIndicator != null) {
							// Second click arrived within the window — double-click
							int deltaX = mouseX - lastClickX;
							int deltaY = mouseY - lastClickY;
							int distSq = deltaX * deltaX + deltaY * deltaY;
							if(distSq < DOUBLE_CLICK_DISTANCE_PX * DOUBLE_CLICK_DISTANCE_PX) {
								focusCameraOnEntity(hit.getEntity());
							} else {
								// Too far from first click — treat as a new single click
								(new TacticalMapRadial(guiDrawer, pendingClickIndicator)).activate();
								pendingClickTime = System.currentTimeMillis();
								lastClickX = mouseX;
								lastClickY = mouseY;
								pendingClickIndicator = hit;
								return;
							}
							pendingClickIndicator = null;
						} else {
							// First click — defer action until we know it's not a double-click
							pendingClickIndicator = hit;
							pendingClickTime = System.currentTimeMillis();
							lastClickX = mouseX;
							lastClickY = mouseY;
						}
					} else {
						// Clicked empty space — flush any pending click and clear selection
						if(pendingClickIndicator != null) {
							(new TacticalMapRadial(guiDrawer, pendingClickIndicator)).activate();
							pendingClickIndicator = null;
						} else if(!hasModifierKeyPressed()) {
							guiDrawer.clearSelected();
						}
					}
				}
			} else if(isMiddleDown && !rightDown) {
				if(!guiDrawer.selectedEntities.isEmpty()) {
					(new TacticalMapRadial(guiDrawer, null)).activate();
				}
			}

			wasLeftMouseDown = isLeftDown;
		}
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

		Vector3f target = entity.getWorldTransform().origin;

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
	}

	/**
	 * Handle turret targeting mode when Ctrl+Click is used.
	 */
	private void handleTurretTargeting(SegmentController entity) {
		if(entity == null) {
			return;
		}

		TacticalMapTurretSelector turretSelector = new TacticalMapTurretSelector(guiDrawer, entity);
		turretSelector.activate();
	}
}