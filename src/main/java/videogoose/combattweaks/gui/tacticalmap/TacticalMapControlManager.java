package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.client.controller.manager.AbstractControlManager;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.view.gui.PlayerPanel;
import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
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
	private float defaultHotbarPos;
	private final KeyboardMappings tacticalMapMapping;

	static final float ENTITY_CLICK_THRESHOLD_PX = 40.0f;
	private static final long DOUBLE_CLICK_TIME_MS = 300;
	private static final int DOUBLE_CLICK_DISTANCE_PX = 10;
	private boolean wasLeftMouseDown;
	private long lastClickTime = 0;
	private int lastClickX = 0;
	private int lastClickY = 0;

	public TacticalMapControlManager(TacticalMapGUIDrawer guiDrawer) {
		super(GameClient.getClientState());
		this.guiDrawer = guiDrawer;
		viewDistance = (float) ConfigManager.getMainConfig().getDouble("tactical_map_view_distance");
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

	public static boolean isOpen() {
		return TacticalMapGUIDrawer.getInstance().controlManager.isActive();
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
		getInteractionManager().setActive(!active);
		getInteractionManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().suspend(active);
		getInteractionManager().getInShipControlManager().getShipControlManager().getSegmentBuildController().suspend(active);
		super.onSwitch(active);
		if(active) {
			if(defaultHotbarPos == 0) {
				defaultHotbarPos = ((BottomBarBuild) getPlayerPanel().getBuildSideBar()).getPos().y;
			}
			//Shitty hack to hide the build hotbar
			((BottomBarBuild) getPlayerPanel().getBuildSideBar()).getPos().y = -10000;
		} else {
			((BottomBarBuild) getPlayerPanel().getBuildSideBar()).getPos().y = defaultHotbarPos;
		}
	}

	@Override
	public void update(Timer timer) {
		CameraMouseState.setGrabbed(Mouse.isButtonDown(1));
		getInteractionManager().suspend(true);
		getInteractionManager().setActive(false);
		getInteractionManager().getBuildToolsManager().suspend(true);
		getInteractionManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().suspend(true);
		getInteractionManager().getInShipControlManager().getShipControlManager().getSegmentBuildController().suspend(true);
		handleInteraction(timer);
	}

	private void handleInteraction(Timer timer) {
		if(!getState().getGlobalGameControlManager().getIngameControlManager().isAnyMenuOrChatActive()) {
			Vector3f movement = new Vector3f();
			int amount = 100;
			if(Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
				amount = 1000;
			}
			/*if(Keyboard.getEventKey() == ControlBindingData.getBinding("Tactical Map - Toggle Movement Paths").getBinding() && Keyboard.getEventKeyState()) {
				guiDrawer.drawMovementPaths = !TacticalMapGUIDrawer.getInstance().drawMovementPaths;
			}*/
			if(Keyboard.isKeyDown(Keyboard.KEY_X)) {
				guiDrawer.camera.reset();
			}
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

			// Entity click detection — only fires on the left-mouse falling edge,
			// and only when the camera-rotate button (RMB) isn't held.
			boolean isLeftDown = Mouse.isButtonDown(0);
			if(isLeftDown && !wasLeftMouseDown && !Mouse.isButtonDown(1) && getState().getPlayerInputs().isEmpty()) {
				// LWJGL gives Y from the bottom; flip to match screen-top convention
				int mouseX = Mouse.getX();
				int mouseY = GLFrame.getHeight() - Mouse.getY();
				TacticalMapEntityIndicator hit = guiDrawer.findIndicatorAtScreen(mouseX, mouseY, ENTITY_CLICK_THRESHOLD_PX);
				if(hit != null) {
					// Check for double-click
					long currentTime = System.currentTimeMillis();
					int deltaX = mouseX - lastClickX;
					int deltaY = mouseY - lastClickY;
					int distanceSq = deltaX * deltaX + deltaY * deltaY;
					boolean isDoubleClick = (currentTime - lastClickTime) < DOUBLE_CLICK_TIME_MS &&
											distanceSq < (DOUBLE_CLICK_DISTANCE_PX * DOUBLE_CLICK_DISTANCE_PX);

					if(isDoubleClick) {
						// Focus camera on double-clicked entity
						focusCameraOnEntity(hit.getEntity());
						lastClickTime = 0; // Reset to prevent triple-click issues
					} else {
						// Single click: open radial menu
						// A new TacticalMapRadial is created per-click so createMenu() always
						// reflects the current target and selection state.
						(new TacticalMapRadial(guiDrawer, hit)).activate();
						lastClickTime = currentTime;
						lastClickX = mouseX;
						lastClickY = mouseY;
					}
				} else if(!hasModifierKeyPressed()) {
					// Clear selection only if clicking empty space without control keys
					guiDrawer.clearSelected();
				}
			}
			wasLeftMouseDown = isLeftDown;
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

	private PlayerPanel getPlayerPanel() {
		return GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel();
	}

	private boolean hasModifierKeyPressed() {
		return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ||
			   Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) ||
			   Keyboard.isKeyDown(Keyboard.KEY_LMENU);
	}

	/**
	 * Focus the tactical map camera on a specific entity.
	 */
	private void focusCameraOnEntity(SegmentController entity) {
		if(entity != null && guiDrawer.camera != null) {
			Vector3f entityPos = entity.getWorldTransform().origin;
			Vector3f cameraPos = new Vector3f(guiDrawer.camera.getWorldTransform().origin);

			// Move camera to look at the entity from a reasonable distance
			// Keep the current camera distance and just change the target position
			Vector3f forward = new Vector3f(guiDrawer.camera.getForward());
			float distance = guiDrawer.selectedRange;

			// Position camera behind entity (relative to current camera direction)
			Vector3f newPos = new Vector3f(entityPos);
			newPos.sub(new Vector3f(forward.x * distance, forward.y * distance, forward.z * distance));

			guiDrawer.camera.getWorldTransform().origin.set(newPos);
		}
	}
}
