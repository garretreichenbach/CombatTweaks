package org.schema.game.client.view.gui.shiphud.newhud;

import org.newdawn.slick.Color;
import org.schema.common.config.ConfigurationElement;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.player.AbstractCharacter;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.AbstractSceneNode;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUIOverlay;
import org.schema.schine.graphicsengine.forms.gui.GUIScrollablePanel;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDrawnTimerInterface;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUITextOverlayAutoScroll;
import org.schema.schine.input.InputState;
import thederpgamer.combattweaks.gui.hud.TargetShipArmorHPBar;

import javax.vecmath.Vector2f;
import javax.vecmath.Vector4f;

public class TargetPanel extends HudConfig {
	@ConfigurationElement(name = "Position")
	public static GUIPosition POSITION;
	@ConfigurationElement(name = "Offset")
	public static Vector2f OFFSET;
	@ConfigurationElement(name = "NameOffset")
	public static Vector2f nameOffset;
	@ConfigurationElement(name = "MassOffset")
	public static Vector2f massOffset;
	@ConfigurationElement(name = "SpeedOffset")
	public static Vector2f speedOffset;
	@ConfigurationElement(name = "FactionOffset")
	public static Vector2f factionOffset;
	private GUIOverlay background;
	private TargetPlayerHealthBar playerHealthBar;
	private TargetPowerBar powerBar;
	private TargetShieldBar shieldBar;
	private TargetShipHPBar shipHPBar;
	private TargetShipArmorHPBar shipArmorHPBar;
	private GUITextOverlay name;
	private GUITextOverlay faction;
	private GUITextOverlay mass;
	private GUITextOverlay speed;
	private float timeDrawn;
	private GUIScrollablePanel nameScroller;
	private GUIScrollablePanel factionScroller;

	//INSERTED CODE
	public GUITextOverlay getNameTextOverlay() {
		return name;
	}
	///

	public TargetPanel(InputState state) {
		super(state);
	}

	@Override
	public Vector4i getConfigColor() {
		return null;
	}

	@Override
	public GUIPosition getConfigPosition() {
		return POSITION;
	}

	@Override
	public Vector2f getConfigOffset() {
		return OFFSET;
	}

	@Override
	protected String getTag() {
		return "TargetPanel";
	}

	/* (non-Javadoc)
	 * @see org.schema.schine.graphicsengine.forms.gui.GUIAncor#draw()
	 */
	@Override
	public void draw() {
		SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
		if(currentPlayerObject != null) {
			PopupMessageNew.targetPanel.x += getPos().x;
			PopupMessageNew.targetPanel.y += getPos().y;
			GlUtil.glPushMatrix();
			transform();
			background.draw();
			PopupMessageNew.targetPanel.x += background.getPos().x;
			PopupMessageNew.targetPanel.y += background.getPos().y;
			PopupMessageNew.targetPanel.x += background.getWidth();
			PopupMessageNew.targetPanel.y += background.getHeight();
			//			System.err.println("TARGET PANEL ::: "+PopupMessageNew.targetPanel);
			nameScroller.draw();
			factionScroller.draw();
			mass.draw();
			speed.draw();
			if(currentPlayerObject instanceof SegmentController) {
				if(((SegmentController) currentPlayerObject).hasStructureAndArmorHP()) {
					shipHPBar.draw();
					shipArmorHPBar.draw();
				}
				if(currentPlayerObject instanceof ManagedSegmentController<?>) {
					shieldBar.draw();
					powerBar.draw();
				}
			} else {
				if(currentPlayerObject instanceof AbstractCharacter<?>) {
					playerHealthBar.draw();
				}
			}
			for(AbstractSceneNode e : getChilds()) {
				e.draw();
			}
			if(isRenderable() && isMouseUpdateEnabled()) {
				checkMouseInside();
			}
			GlUtil.glPopMatrix();
		}
	}

	/* (non-Javadoc)
	 * @see org.schema.schine.graphicsengine.forms.gui.GUIAncor#onInit()
	 */
	@Override
	public void onInit() {
		super.onInit();
		background = new GUIOverlay(Controller.getResLoader().getSprite("HUD_Target-2x4-gui-"), getState());
		powerBar = new TargetPowerBar(getState()) {
			@Override
			public void draw() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				super.draw();
			}
		};
		playerHealthBar = new TargetPlayerHealthBar(getState());
		shieldBar = new TargetShieldBar(getState());
		shipHPBar = new TargetShipHPBar(getState());
		shipArmorHPBar = new TargetShipArmorHPBar(getState());
		background.onInit();
		powerBar.onInit();
		shieldBar.onInit();
		playerHealthBar.onInit();
		shipHPBar.onInit();
		shipArmorHPBar.onInit();
		nameScroller = new GUIScrollablePanel(background.getWidth() - nameOffset.x * 2, 24, getState());
		nameScroller.setLeftRightClipOnly = true;
		nameScroller.setScrollable(0);
		name = new GUITextOverlayAutoScroll(10, 10, FontLibrary.getBlenderProMedium15(), nameScroller, new GUIDrawnTimerInterface() {
			@Override
			public float getTimeDrawn() {
				return timeDrawn;
			}

			@Override
			public void setTimeDrawn(float setTimeDrawn) {
				timeDrawn = setTimeDrawn;
			}
		}, getState());
		name.setTextSimple(new Object() {
			@Override
			public String toString() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				if(currentPlayerObject != null) {
					String r = currentPlayerObject.getRealName();
					if(currentPlayerObject.getAdditionalObjectInformation() != null) {
						return r + currentPlayerObject.getAdditionalObjectInformation();
					}
					return r;
				} else {
					return Lng.str("n/a");
				}
			}
		});
		nameScroller.setPos(nameOffset.x, nameOffset.y, 0);
		factionScroller = new GUIScrollablePanel(background.getWidth() - factionOffset.x * 2, 24, getState());
		faction = new GUITextOverlayAutoScroll(10, 10, FontLibrary.getBlenderProMedium15(), factionScroller, new GUIDrawnTimerInterface() {
			@Override
			public void setTimeDrawn(float setTimeDrawn) {
				timeDrawn = setTimeDrawn;
			}

			@Override
			public float getTimeDrawn() {
				return timeDrawn;
			}
		}, getState()) {
			final Vector4f c = new Vector4f(1, 1, 1, 1);

			/* (non-Javadoc)
			 * @see org.schema.schine.graphicsengine.forms.gui.newgui.GUITextOverlayAutoScroll#draw()
			 */
			@Override
			public void draw() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				if(currentPlayerObject != null) {
					HudIndicatorOverlay.getColor(currentPlayerObject, c, false, (GameClientState) getState());
					setColor(c);
					super.draw();
				}
			}
		};
		faction.setTextSimple(new Object() {
			@Override
			public String toString() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				if(currentPlayerObject != null) {
					FactionManager fm = ((GameClientState) getState()).getFactionManager();
					if(fm.existsFaction(currentPlayerObject.getFactionId())) {
						return fm.getFaction(currentPlayerObject.getFactionId()).getName();
					} else {
						return Lng.str("Neutral");
					}
				} else {
					return Lng.str("n/a");
				}
			}
		});
		factionScroller.setPos(factionOffset.x, factionOffset.y, 0);
		//		factionScroller.setPos(textInset, 15 + 82, 0);
		mass = new GUITextOverlay(10, 10, FontLibrary.getBlenderProMedium15(), new Color(215, 224, 236, 255), getState());
		mass.setTextSimple(new Object() {
			@Override
			public String toString() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				if(currentPlayerObject != null) {
					return Lng.str("Mass: %s", StringTools.formatSeperated(Math.ceil(currentPlayerObject.getMass())));
				} else {
					return Lng.str("n/a");
				}
			}
		});
		mass.setPos(massOffset.x, massOffset.y, 0);
		speed = new GUITextOverlay(10, 10, FontLibrary.getBlenderProMedium15(), new Color(215, 224, 236, 255), getState());
		speed.setTextSimple(new Object() {
			@Override
			public String toString() {
				SimpleTransformableSendableObject currentPlayerObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
				if(currentPlayerObject != null) {
					return Lng.str("%s m/s", StringTools.formatSeperated(Math.round(currentPlayerObject.getSpeedCurrent())));
				} else {
					return Lng.str("");
				}
			}
		});
		speed.setPos(speedOffset.x, speedOffset.y, 0);
		width = background.getWidth();
		height = background.getHeight();
		powerBar.getPos().set(TargetPowerBar.OFFSET.x, TargetPowerBar.OFFSET.y, 0);
		shieldBar.getPos().set(TargetShieldBar.OFFSET.x, TargetShieldBar.OFFSET.y, 0);
		playerHealthBar.getPos().set(PlayerHealthBar.OFFSET.x, PlayerHealthBar.OFFSET.y, 0);
		shipHPBar.getPos().set(TargetShipHPBar.OFFSET.x, TargetShipHPBar.OFFSET.y, 0);
		shipArmorHPBar.getPos().set(TargetShipArmorHPBar.OFFSET.x, 136, 0);

		mass.setPos(massOffset.x, massOffset.y + shipArmorHPBar.getHeight(), 0);
		speed.setPos(speedOffset.x, speedOffset.y + shipArmorHPBar.getHeight(), 0);
		factionScroller.setPos(factionOffset.x, factionOffset.y + shipArmorHPBar.getHeight(), 0);
		height = background.getHeight() + shipArmorHPBar.getHeight();
	}

	/* (non-Javadoc)
	 * @see org.schema.schine.graphicsengine.forms.AbstractSceneNode#update(org.schema.schine.graphicsengine.core.Timer)
	 */
	@Override
	public void update(Timer timer) {
		updateOrientation();
		timeDrawn += timer.getDelta();
	}
}
