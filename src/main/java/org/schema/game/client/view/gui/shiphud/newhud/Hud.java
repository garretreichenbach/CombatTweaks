package org.schema.game.client.view.gui.shiphud.newhud;

import api.listener.events.gui.HudCreateEvent;
import api.mod.StarLoader;
import com.bulletphysics.collision.dispatch.CollisionWorld.ClosestRayResultCallback;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.client.controller.manager.ingame.ship.ShipExternalFlightController;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.camera.InShipCamera;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.SlotAssignment;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.elements.ControlBlockElementCollectionManager;
import org.schema.game.common.controller.elements.FireingUnit;
import org.schema.game.common.controller.elements.ManagerModuleCollection;
import org.schema.game.common.controller.elements.UsableControllableElementManager;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager.OffensiveEffects;
import org.schema.game.common.controller.elements.weapon.WeaponCollectionManager;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.game.common.data.physics.PhysicsExt;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.*;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIStatisticsGraph;
import org.schema.schine.graphicsengine.util.timer.SinusTimerUtil;
import org.schema.schine.network.StateInterface;
import org.schema.schine.physics.PhysicsState;
import thederpgamer.combattweaks.gui.hud.ShipArmorHPBar;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.ArrayList;

public class Hud extends GUIElement {
	/* (non-Javadoc)d
	 * @see org.schema.schine.graphicsengine.forms.gui.GUIOverlay#draw(javax.media.openGL11.GL21, javax.media.openGL11.GLU.gl2.GLU)
	 */ int x = 0;
	int y = 0;
	float dist = 128;
	long lastTest = 0;
	private FillableBar powerBar;
	private FillableBar reactorPowerBar;
	private FillableBar speedBarFarRight;
	private FillableBar speedBarRight;
	private FillableBar shieldBarLeft;
	private FillableBar shieldBarRight;
	private FillableBar healthBar;
	private FillableBar shipHPBar;
	private TargetPanel targetPanel;
	private PositiveEffectBar positiveEffectBar;
	private NegativeEffectBar negativeEffectBar;
	private Radar radar;
	private HudIndicatorOverlay indicator;
	private final HudContextHelpManager helpManager;
	private GUIOverlay backgroundCrosshairHUD;
	private GUIOverlay backgroundCrosshair;
	private GUIOverlay hitNotification;
	private GUITextOverlay targetName;
	private SinusTimerUtil sinusTimerUtil = new SinusTimerUtil(10);
	private SinusTimerUtil testSinusTimerUtil = new SinusTimerUtil(4);
	private boolean updateSine;
	private ShipArmorHPBar shipArmorHPBar;
	private GUIAncor rightBottom;
	private GUIAncor leftBottom;
	private PowerBatteryBar powerBatteryBar;
	private PowerStabilizationBar powerStabilizationBar;
	private PowerConsumptionBar powerConsumptionBar;
	//INSERTED CODE @86
	public static ArrayList<GUIElement> customElements = new ArrayList<GUIElement>();

	///
	//INSERTED CODE
	public TargetPanel getTargetPanel() {
		return targetPanel;
	}
	///

	public Hud(GameClientState state) {
		super(state);
		radar = new Radar(state);
		powerBar = new PowerBar(state);
		reactorPowerBar = new ReactorPowerBar(state);
		speedBarFarRight = new SpeedBarFarRight(state);
		speedBarRight = new SpeedBarRight(state);
		shieldBarLeft = new ShieldBarLeftOld(state);
		shieldBarRight = new ShieldBarRightLocal(state);
		powerBatteryBar = new PowerBatteryBar(state);
		healthBar = new HealthBar(state);
		shipHPBar = new ShipHPBar(state);
		shipArmorHPBar = new ShipArmorHPBar(state);
		powerStabilizationBar = new PowerStabilizationBar(state);
		powerConsumptionBar = new PowerConsumptionBar(state);
		targetPanel = new TargetPanel(state);
		positiveEffectBar = new PositiveEffectBar(state);
		negativeEffectBar = new NegativeEffectBar(state);
		backgroundCrosshairHUD = new GUIOverlay(Controller.getResLoader().getSprite("crosshair-c-gui-"), state);
		backgroundCrosshair = new GUIOverlay(Controller.getResLoader().getSprite("crosshair-simple-c-gui-"), state);
		hitNotification = new GUIOverlay(Controller.getResLoader().getSprite("crosshair-hit-c-gui-"), state);
		helpManager = new HudContextHelpManager(state);
		indicator = new HudIndicatorOverlay(state);
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public void draw() {
		if(needsReOrientation()) {
			doOrientation();
		}
		GlUtil.glPushMatrix();
		//		powerBarTest.draw();
		hitNotification.orientate(ORIENTATION_HORIZONTAL_MIDDLE | ORIENTATION_VERTICAL_MIDDLE);
		hitNotification.getPos().x += hitNotification.getWidth() / 2;
		hitNotification.getPos().y += hitNotification.getHeight() / 2;
		SegmentController ship;
		if(isExternalActive() && (Controller.getCamera() instanceof InShipCamera) && (ship = getSegmentControllerFromEntered()) != null) {
			InShipCamera c = (InShipCamera) Controller.getCamera();
			Vector3f forward = new Vector3f(c.getHelperForward());
			forward.normalize();
			Vector3f camPos = new Vector3f(c.getPos());
			GameClientState state = ((GameClientState) getState());
			boolean ok = false;
			if(System.currentTimeMillis() - lastTest > 1000) {
				try {
					SlotAssignment shipConfiguration = state.getShip().getSlotAssignment();
					Vector3i absPos = shipConfiguration.get(state.getPlayer().getCurrentShipControllerSlot());
					;
					if(absPos != null) {
						SegmentPiece pointUnsave = state.getShip().getSegmentBuffer().getPointUnsave(absPos);
						if(pointUnsave != null && state.getShip() instanceof ManagedSegmentController<?>) {
							ManagerModuleCollection<?, ?, ?> t = ((ManagedSegmentController<?>) state.getShip()).getManagerContainer().getModulesControllerMap().get(pointUnsave.getType());
							if(t != null && t.getElementManager() instanceof UsableControllableElementManager<?, ?, ?>) {
								ControlBlockElementCollectionManager<?, ?, ?> o = t.getCollectionManagersMap().get(pointUnsave.getAbsoluteIndex());
								if(o != null) {
									for(int i = 0; i < o.getElementCollections().size(); i++) {
										ElementCollection<?, ?, ?> eo = o.getElementCollections().get(i);
										if(eo instanceof FireingUnit<?, ?, ?>) {
											dist = Math.max(dist, ((FireingUnit<?, ?, ?>) eo).getDistanceFull());
											ok = true;
										}
									}
								}
							}
						}
					}
				} catch(Exception e) {
					//				System.err.println("Error in selecting slot " + e.getClass());
				}
				if(!ok) {
					dist = 128;
				}
				lastTest = System.currentTimeMillis();
			}
			forward.scale(dist);
			camPos.add(forward);
			Vector3f posOnScreen = ((GameClientState) getState()).getScene().getWorldToScreenConverter().convert(camPos, new Vector3f(), false);
			backgroundCrosshair.getPos().set(posOnScreen);
			backgroundCrosshair.getPos().x += 3;
			backgroundCrosshair.getPos().y -= 3;
			drawSmallCorsair(dist, ship);
			drawInShipHud(dist, ship);
		} else {
			if(isExternalActive() && !(Controller.getCamera() instanceof InShipCamera)) {
				System.err.println("WARNING: HudBasic has wrong camera: " + Controller.getCamera().getClass());
			}
			backgroundCrosshair.orientate(ORIENTATION_HORIZONTAL_MIDDLE | ORIENTATION_VERTICAL_MIDDLE);
			backgroundCrosshair.getPos().x += backgroundCrosshair.getWidth() / 2;
			backgroundCrosshair.getPos().y += backgroundCrosshair.getHeight() / 2;
			backgroundCrosshair.draw();
			if(((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getPlayerCharacterManager().isTreeActive()) {
				radar.orientate(ORIENTATION_RIGHT | ORIENTATION_TOP);
				//				radar.getPos().y += 64;
				radar.draw();
				//				powerBar.draw();
				//				healthBar.draw();
				targetPanel.draw();
			}
		}
		if(((GameClientState) getState()).getPlayer() != null && ((GameClientState) getState()).getPlayer().getClientHitNotifaction() != 0) {
			byte cType = ((GameClientState) getState()).getPlayer().getClientHitNotifaction();
			if(cType == Damager.SHIELD) {
				hitNotification.getSprite().getTint().set(0.4f, 0.4f, 1, 0.9f);
			} else if(cType == Damager.CHARACTER) {
				hitNotification.getSprite().getTint().set(1.0f, 0.4f, 0.4f, 0.9f);
			} else {
				hitNotification.getSprite().getTint().set(1f, 1f, 1f, 0.9f);
			}
			hitNotification.draw();
		}
		drawIndications();
		if(EngineSettings.G_DRAW_LAG_OBJECTS_IN_HUD.isOn()) {
			leftBottom.setWidth(GLFrame.getWidth() - ((GLFrame.getWidth() / 2 + 354) + 21));
			leftBottom.orientate(ORIENTATION_BOTTOM | ORIENTATION_LEFT);
			leftBottom.getPos().y -= 100;
			leftBottom.draw();
		}
		if(EngineSettings.G_DRAW_NT_STATS_OVERLAY.isOn()) {
			rightBottom.setWidth(GLFrame.getWidth() - ((GLFrame.getWidth() / 2 + 354) + 21));
			rightBottom.orientate(ORIENTATION_BOTTOM | ORIENTATION_RIGHT);
			rightBottom.draw();
		}
		helpManager.draw();
		//INSERTED CODE @244
		for(GUIElement element : customElements) {
			element.draw();
		}
		///
		GlUtil.glPopMatrix();
	}

	@Override
	public void onInit() {
		powerBar.onInit();
		reactorPowerBar.onInit();
		shieldBarLeft.onInit();
		shieldBarRight.onInit();
		healthBar.onInit();
		powerBatteryBar.onInit();
		shipHPBar.onInit();
		shipArmorHPBar.onInit();
		powerStabilizationBar.onInit();
		powerConsumptionBar.onInit();
		targetPanel.onInit();
		speedBarFarRight.onInit();
		speedBarRight.onInit();
		backgroundCrosshairHUD.onInit();
		backgroundCrosshairHUD.getSprite().setTint(new Vector4f(1, 1, 1, 1));
		backgroundCrosshair.onInit();
		backgroundCrosshair.getSprite().setTint(new Vector4f(1, 1, 1, 1));
		hitNotification.onInit();
		hitNotification.getSprite().setTint(new Vector4f(1, 1, 1, 0.9f));
		positiveEffectBar.onInit();
		negativeEffectBar.onInit();
		indicator.onInit();
		radar.onInit();
		helpManager.onInit();
		targetName = new GUITextOverlay(100, 10, FontLibrary.getCourierNew12White(), getState());
		targetName.setTextSimple("");
		targetName.setPos(300, 300, 0);
		{
			rightBottom = new GUIAncor(getState(), 300, 80);
			GUIScrollablePanel sp = new GUIScrollablePanel(100, 100, rightBottom, getState());
			GUIStatisticsGraph p = new GUIStatisticsGraph(getState(), null, rightBottom, ((StateInterface) getState()).getDataStatsManager().getReceivedData(), ((StateInterface) getState()).getDataStatsManager().getSentData()) {
				@Override
				public String formatMax(long maxAmplitude, long curAplitude) {
					return "Max: " + StringTools.readableFileSize(maxAmplitude) + "; In: " + StringTools.readableFileSize(curAplitude) + (ps == null ? " (F12)" : "");
				}
			};
			p.onInit();
			sp.setContent(p);
			rightBottom.attach(sp);
		}
		{
			leftBottom = new GUIAncor(getState(), 300, 80);
			GUIScrollablePanel sp = new GUIScrollablePanel(100, 100, leftBottom, getState());
			GUIStatisticsGraph p = new GUIStatisticsGraph(getState(), null, leftBottom, ((GameClientState) getState()).lagStats) {
				@Override
				public String formatMax(long maxAmplitude, long curAmplitude) {
					return "Max: " + maxAmplitude + "ms" + (ps == null ? " (F7)" : "");
				}
			};
			p.onInit();
			sp.setContent(p);
			leftBottom.attach(sp);
			//INSERTED CODE @???
			HudCreateEvent event = new HudCreateEvent(this, (GameClientState) getState());
			StarLoader.fireEvent(HudCreateEvent.class, event, false);
			customElements.addAll(event.elements);
			for(GUIElement element : customElements) {
				element.onInit();
			}
			///
		}
	}

	private void drawBigCorsair() {
		backgroundCrosshairHUD.orientate(ORIENTATION_HORIZONTAL_MIDDLE | ORIENTATION_VERTICAL_MIDDLE);
		backgroundCrosshairHUD.getPos().x += backgroundCrosshairHUD.getWidth() / 2;
		backgroundCrosshairHUD.getPos().y += backgroundCrosshairHUD.getHeight() / 2;
		if(getExternalShipMan().getAquire().isTargetMode()) {
			if(((GameClientState) getState()).getPlayer().getAquiredTarget() != null) {
				backgroundCrosshairHUD.getSprite().getTint().set(sinusTimerUtil.getTime(), 1, sinusTimerUtil.getTime(), 1);
				backgroundCrosshairHUD.getSprite().setScale(1, 1, 1);
				updateSine = true;
			} else {
				if(getExternalShipMan().getAquire().getTarget() != null && !(getExternalShipMan().getAquire().getTarget() instanceof Ship && ((Ship) getExternalShipMan().getAquire().getTarget()).isJammingFor(getExternalShipMan().getShip()))) {
					float taTime = getExternalShipMan().getAquire().getTargetTime();
					float quTime = getExternalShipMan().getAquire().getAcquireTime(getExternalShipMan().getShip(), getExternalShipMan().getAquire().getTarget());
					float percent = Math.min(1, taTime / quTime);
					float invPercent = 1.0f - percent;
					backgroundCrosshairHUD.getSprite().setScale(1 + invPercent, 1 + invPercent, 1 + invPercent);
					backgroundCrosshairHUD.getSprite().getTint().set(invPercent, percent, 0, 1);
					backgroundCrosshairHUD.setRot(0, 0, percent * 360);
					targetName.getText().set(0, getExternalShipMan().getAquire().getTarget().toNiceString());
				} else {
					targetName.getText().set(0, "");
					backgroundCrosshairHUD.getSprite().getTint().set(1, 1, 1, 1);
					backgroundCrosshairHUD.getSprite().setScale(2, 2, 2);
					backgroundCrosshairHUD.setRot(0, 0, 0);
				}
				sinusTimerUtil.reset();
				updateSine = false;
			}
		} else {
			backgroundCrosshairHUD.getSprite().getTint().set(1, 1, 1, 1);
			backgroundCrosshairHUD.getSprite().setScale(1, 1, 1);
			backgroundCrosshairHUD.setRot(0, 0, 0);
			updateSine = false;
		}
		backgroundCrosshairHUD.draw();
		targetName.draw();
	}

	public void drawIndications() {
		indicator.draw();
	}

	private void drawInShipHud(float dist, SegmentController ship) {
		if(!ship.isUsingPowerReactors()) {
			powerBar.draw();
			shieldBarLeft.draw();
		} else {
			shieldBarRight.draw();
		}
		shipArmorHPBar.draw();
		shipHPBar.draw();
		if(ship.isUsingPowerReactors()) {
			speedBarRight.draw();
			if(ship.hasAnyReactors()) {
				powerStabilizationBar.draw();
				powerConsumptionBar.draw();
			} else {
				powerStabilizationBar.drawNoReactorText();
			}
		} else {
			speedBarFarRight.draw();
			powerBatteryBar.draw();
		}
		if(!ship.isUsingPowerReactors()) {
			powerBar.drawText();
			shieldBarLeft.drawText();
		} else {
			shieldBarRight.drawText();
		}
		shipArmorHPBar.drawText();
		shipHPBar.drawText();
		if(ship.isUsingPowerReactors()) {
			powerStabilizationBar.drawText();
			powerConsumptionBar.drawText();
			speedBarRight.drawText();
		} else {
			speedBarFarRight.drawText();
			powerBatteryBar.drawText();
		}
		targetPanel.draw();
		negativeEffectBar.draw();
		positiveEffectBar.draw();
		radar.orientate(ORIENTATION_RIGHT | ORIENTATION_TOP);
		//		radar.getPos().y += 64;
		radar.draw();
		targetName.getText().set(0, "");
		drawBigCorsair();
	}

	private void drawSmallCorsair(float dist, SegmentController ship) {
		InShipCamera cam = (InShipCamera) Controller.getCamera();
		Vector3f camPos = new Vector3f(Controller.getCamera().getPos());
		Vector3f forward = new Vector3f(cam.getHelperForward());
		PhysicsExt physics = (PhysicsExt) ((PhysicsState) getState()).getPhysics();
		forward.normalize();
		forward.scale(dist);
		forward.add(camPos);
		ClosestRayResultCallback testRayCollisionPoint = physics.testRayCollisionPoint(camPos, forward, false, ship, null, false, true, true);
		//		System.err.println("DIST ::: "+dist+" : "+testRayCollisionPoint.hasHit());
		if(testRayCollisionPoint.hasHit()) {
			backgroundCrosshair.getSprite().getTint().set(0, 1, 0, 1);
		} else {
			backgroundCrosshair.getSprite().getTint().set(1, 1, 1, 1);
		}
		//		System.err.println("CURRENT: "+backgroundCrosshair.getPos().y);
		backgroundCrosshair.draw();
		backgroundCrosshair.getSprite().getTint().set(1, 1, 1, 1);
	}

	public ShipExternalFlightController getExternalShipMan() {
		return ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController();
	}

	@Override
	public float getHeight() {
		return GLFrame.getHeight();
	}

	@Override
	public float getWidth() {
		return GLFrame.getWidth();
	}

	@Override
	public boolean isPositionCenter() {
		return false;
	}

	public HudContextHelpManager getHelpManager() {
		return helpManager;
	}

	/**
	 * @return the indicator
	 */
	public HudIndicatorOverlay getIndicator() {
		return indicator;
	}

	/**
	 * @param indicator the indicator to set
	 */
	public void setIndicator(HudIndicatorOverlay indicator) {
		this.indicator = indicator;
	}

	private PlayerInteractionControlManager getInteractionManager() {
		return ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager();
	}

	private SegmentController getSegmentControllerFromEntered() {
		if(getInteractionManager().getInShipControlManager().isActive()) {
			return getInteractionManager().getInShipControlManager().getEntered().getSegmentController();
		} else {
			if(getInteractionManager().getSegmentControlManager().getEntered() != null) {
				return getInteractionManager().getSegmentControlManager().getEntered().getSegmentController();
			} else {
				return null;
			}
		}
	}

	public WeaponCollectionManager getWeaponController() {
		GameClientState s = (GameClientState) getState();
		Ship ship = s.getShip();
		if(ship != null) {
			Vector3i v = ship.getSlotAssignment().get(s.getPlayer().getCurrentShipControllerSlot());
			for(int i = 0; i < ship.getManagerContainer().getWeapon().getCollectionManagers().size(); i++) {
				if(ship.getManagerContainer().getWeapon().getCollectionManagers().get(i).equalsControllerPos(v)) {
					return ship.getManagerContainer().getWeapon().getCollectionManagers().get(i);
				}
			}
		}
		return null;
	}

	public boolean isExternalActive() {
		return getInteractionManager().getInShipControlManager().getShipControlManager().getShipExternalFlightController().isTreeActive() || getInteractionManager().getSegmentControlManager().getSegmentExternalController().isTreeActive();
	}

	public void onSectorChange() {
		indicator.onSectorChange();
	}

	@Override
	public void update(Timer timer) {
		indicator.update(timer);
		if(updateSine) {
			sinusTimerUtil.update(timer);
		}
		testSinusTimerUtil.update(timer);
		positiveEffectBar.updateOrientation();
		negativeEffectBar.updateOrientation();
		positiveEffectBar.update(timer);
		negativeEffectBar.update(timer);
		targetPanel.update(timer);
		powerBatteryBar.update(timer);
		powerBar.update(timer);
		reactorPowerBar.update(timer);
		speedBarFarRight.update(timer);
		speedBarRight.update(timer);
		shieldBarLeft.update(timer);
		shieldBarRight.update(timer);
		healthBar.update(timer);
		shipHPBar.update(timer);
		shipArmorHPBar.update(timer);
		powerStabilizationBar.update(timer);
		powerConsumptionBar.update(timer);
		radar.update(timer);
		//INSERTED CODE @569
		for(GUIElement element : customElements) {
			element.update(timer);
		}
		///
		helpManager.update(timer);
		if(!(isExternalActive() && (Controller.getCamera() instanceof InShipCamera))) {
			resetDrawnInShip();
		}
	}

	public void resetDrawnHUDAtAll() {
		radar.resetDrawn();
	}

	public void notifyEffectHit(SimpleTransformableSendableObject obj, OffensiveEffects offensiveEffects) {
		HitIconIndex hitIconIndex = BuffDebuff.mapOffensive.get(offensiveEffects);
		if(hitIconIndex != null) {
			if(hitIconIndex.isBuff()) {
				positiveEffectBar.activate(hitIconIndex);
			} else {
				negativeEffectBar.activate(hitIconIndex);
			}
		} else {
			EffectIconIndex eee = BuffDebuff.map.get(offensiveEffects.getEffect());
			if(eee.isBuff()) {
				positiveEffectBar.activate(eee);
			} else {
				negativeEffectBar.activate(eee);
			}
		}
	}

	public void resetDrawnInShip() {
		powerBatteryBar.resetDrawn();
		powerBar.resetDrawn();
		reactorPowerBar.resetDrawn();
		speedBarRight.resetDrawn();
		speedBarFarRight.resetDrawn();
		shieldBarLeft.resetDrawn();
		shieldBarRight.resetDrawn();
		healthBar.resetDrawn();
		shipHPBar.resetDrawn();
		shipArmorHPBar.resetDrawn();
		powerStabilizationBar.resetDrawn();
		powerConsumptionBar.resetDrawn();
	}

	public Radar getRadar() {
		return radar;
	}

	public void setRadar(Radar radar) {
		this.radar = radar;
	}
}
