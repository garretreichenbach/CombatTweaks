package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import api.network.packets.PacketUtil;
import org.schema.game.client.view.gui.RadialMenu;
import org.schema.game.client.view.gui.RadialMenuCenter;
import org.schema.game.client.view.gui.RadialMenuDialog;
import org.schema.game.common.controller.FloatingRock;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIActivationCallback;
import org.schema.schine.graphicsengine.forms.gui.GUICallback;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.input.InputState;
import videogoose.combattweaks.network.client.*;

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

		if(target == null) {
			//Just show idle command
			menu.setCenter(new RadialMenuCenter(getState(), menu, "Order Idle", new GUICallback() {
				@Override
				public void callback(GUIElement guiElement, MouseEvent mouseEvent) {
					if(mouseEvent.pressedLeftMouse()) {
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
				public boolean isActive(InputState inputState) {
					return true;
				}
			}));
			menu.onInit();
			return menu;
		}

		boolean isOwnFaction = target.getEntity().getFactionId() == GameClient.getClientPlayerState().getFactionId() && GameClient.getClientPlayerState().getFactionId() != 0;
		boolean isAlly = GameCommon.getGameState().getFactionManager().isFriend(target.getEntity().getFactionId(), GameClient.getClientPlayerState().getFactionId());
		boolean hasSelection = !drawer.selectedEntities.isEmpty();
		boolean isMinable = target.getEntity() instanceof FloatingRock;

		if(isOwnFaction) {
			// Only show action options if something is already selected
			if(hasSelection) {
				menu.setCenter(new RadialMenuCenter(getState(), menu, "Order Idle", new GUICallback() {
					@Override
					public void callback(GUIElement guiElement, MouseEvent mouseEvent) {
						if(mouseEvent.pressedLeftMouse()) {
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
					public boolean isActive(InputState inputState) {
						return true;
					}
				}));

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
				}, null);
			}
		} else if(hasSelection) {
			// Only show orders if something is selected
			if(isMinable) {
				// Minable target (asteroid) — order selected ships to mine it
				menu.addItem("Order Mine", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship) {
									PacketUtil.sendPacketToServer(new SendMinePacket((Ship) selected, target.getEntity()));
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
				}, null);
			} else if(!isAlly) {
				// Enemy — order attack
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
				}, null);
			} else {
				// Ally/own faction ship — offer both defend and repair
				menu.addItem("Order Defend", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship && !target.getEntity().equals(selected)) {
									PacketUtil.sendPacketToServer(new SendDefensePacket((Ship) selected, target.getEntity()));
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
				}, null);

				menu.addItem("Order Repair", new GUICallback() {
					@Override
					public void callback(GUIElement callingGuiElement, MouseEvent event) {
						if(event.pressedLeftMouse()) {
							for(SegmentController selected : drawer.selectedEntities) {
								if(selected instanceof Ship && !target.getEntity().equals(selected)) {
									PacketUtil.sendPacketToServer(new SendRepairPacket((Ship) selected, target.getEntity()));
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
				}, null);
			}
		}

		menu.addItem("Move To", new GUICallback() {
			@Override
			public void callback(GUIElement callingGuiElement, MouseEvent event) {
				if(event.pressedLeftMouse()) {
					for(SegmentController selected : drawer.selectedEntities) {
						if(selected instanceof Ship && !target.getEntity().equals(selected)) {
							PacketUtil.sendPacketToServer(new SendMoveToPacket((Ship) selected, target.getEntity()));
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
		}, null);
		menu.onInit();
		return menu;
	}
}
