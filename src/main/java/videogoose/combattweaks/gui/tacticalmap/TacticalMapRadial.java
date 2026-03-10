package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import api.network.packets.PacketUtil;
import org.schema.game.client.view.gui.RadialMenu;
import org.schema.game.client.view.gui.RadialMenuDialog;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.input.InputState;
import videogoose.combattweaks.network.client.SendAttackPacket;
import videogoose.combattweaks.network.client.SendDefensePacket;
import videogoose.combattweaks.network.client.SendIdlePacket;

public class TacticalMapRadial extends RadialMenuDialog {

	private final TacticalMapGUIDrawer drawer;
	private final TacticalMapEntityIndicator target;

	public TacticalMapRadial(TacticalMapGUIDrawer drawer, TacticalMapEntityIndicator target) {
		super(GameClient.getClientState());
		this.drawer = drawer;
		this.target = target;
	}

	@Override
	public RadialMenu createMenu(RadialMenuDialog radialMenuDialog) {
		RadialMenu menu = new RadialMenu(getState(), "TacticalMapRadial", radialMenuDialog, 800, 600, 130, FontLibrary.getBOLDBlender20());

		boolean isOwnFaction = target.getEntity().getFactionId() == GameClient.getClientPlayerState().getFactionId();
		boolean isAlly = GameCommon.getGameState().getFactionManager().isFriend(target.getEntity().getFactionId(), GameClient.getClientPlayerState().getFactionId());

		if(isOwnFaction) {
			boolean alreadySelected = drawer.selectedEntities.contains(target.getEntity());
			menu.addItem(alreadySelected ? "Deselect" : "Select", new GUICallback() {
				@Override
				public void callback(GUIElement callingGuiElement, MouseEvent event) {
					if(event.pressedLeftMouse()) {
						if(drawer.selectedEntities.contains(target.getEntity())) {
							drawer.removeSelection(target);
						} else {
							drawer.addSelection(target);
						}
						deactivate();
					}
				}

				@Override
				public boolean isOccluded() {
					return false;
				}
			}, null);
			if(!alreadySelected) {
				menu.addItem("Defend", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship && !target.getEntity().equals(selected)) {
									PacketUtil.sendPacketToServer(new SendDefensePacket((Ship) selected, target.getEntity()));
									// Update client-side indicator immediately so the green path appears at once
									TacticalMapEntityIndicator defenderIndicator = drawer.drawMap.get(selected.getId());
									if(defenderIndicator != null) {
										defenderIndicator.setDefendTarget(target.getEntity());
									}
								}
							}
							drawer.clearSelected();
							deactivate();
						}
					}

					@Override
					public boolean isOccluded() {
						return false;
					}
				}, new GUIActivationCallback() {
					@Override
					public boolean isVisible(InputState inputState) {
						return true;
					}

					@Override
					public boolean isActive(InputState state) {
						return !drawer.selectedEntities.isEmpty();
					}
				});
			}
			menu.addItem("Idle", new GUICallback() {
				@Override
				public void callback(GUIElement callingGuiElement, MouseEvent event) {
					if(event.pressedLeftMouse()) {
						for(SegmentController selected : drawer.selectedEntities) {
							if(selected instanceof Ship) {
								PacketUtil.sendPacketToServer(new SendIdlePacket((Ship) selected));
								TacticalMapEntityIndicator indicator = drawer.drawMap.get(selected.getId());
								if(indicator != null) {
									indicator.setDefendTarget(null);
									indicator.setCurrentTarget(null);
								}
							}
						}
						drawer.clearSelected();
						deactivate();
					}
				}

				@Override
				public boolean isOccluded() {
					return false;
				}
			}, new GUIActivationCallback() {
				@Override
				public boolean isVisible(InputState inputState) {
					return true;
				}

				@Override
				public boolean isActive(InputState state) {
					return !drawer.selectedEntities.isEmpty();
				}
			});
		} else {
			if(!isAlly) {
				// Order all selected friendly ships to attack this entity
				menu.addItem("Order Attack", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship && !target.getEntity().equals(selected)) {
									PacketUtil.sendPacketToServer(new SendAttackPacket((Ship) selected, target.getEntity()));
								}
							}
							drawer.clearSelected();
							deactivate();
						}
					}

					@Override
					public boolean isOccluded() {
						return false;
					}
				}, new GUIActivationCallback() {
					@Override
					public boolean isVisible(InputState inputState) {
						return true;
					}

					@Override
					public boolean isActive(InputState state) {
						return !drawer.selectedEntities.isEmpty();
					}
				});
			} else {
				menu.addItem("Order Defend", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship && !target.getEntity().equals(selected)) {
									PacketUtil.sendPacketToServer(new SendDefensePacket((Ship) selected, target.getEntity()));
									// Update client-side indicator immediately so the green path appears at once
									TacticalMapEntityIndicator defenderIndicator = drawer.drawMap.get(selected.getId());
									if(defenderIndicator != null) {
										defenderIndicator.setDefendTarget(target.getEntity());
									}
								}
							}
							drawer.clearSelected();
							deactivate();
						}
					}

					@Override
					public boolean isOccluded() {
						return false;
					}
				}, new GUIActivationCallback() {
					@Override
					public boolean isVisible(InputState inputState) {
						return true;
					}

					@Override
					public boolean isActive(InputState state) {
						return !drawer.selectedEntities.isEmpty();
					}
				});
			}
		}

		return menu;
	}
}
