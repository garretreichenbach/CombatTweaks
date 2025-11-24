package thederpgamer.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import api.common.GameCommon;
import api.network.packets.PacketUtil;
import api.utils.game.SegmentControllerUtils;
import com.bulletphysics.linearmath.Transform;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.gamemap.entry.SelectableMapEntry;
import org.schema.game.client.view.effects.ConstantIndication;
import org.schema.game.client.view.effects.Indication;
import org.schema.game.client.view.gamemap.GameMapDrawer;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.SpaceStation;
import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.common.data.world.VoidSystem;
import org.schema.game.server.ai.SegmentControllerAIEntity;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.core.settings.EngineSettings;
import org.schema.schine.graphicsengine.forms.PositionableSubColorSprite;
import org.schema.schine.graphicsengine.forms.PositionableSubSprite;
import org.schema.schine.graphicsengine.forms.SelectableSprite;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.network.client.SendAttackPacket;
import thederpgamer.combattweaks.utils.SectorUtils;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Random;

public class TacticalMapEntityIndicator implements PositionableSubColorSprite, SelectableSprite, SelectableMapEntry {
	// Shared RNG to avoid per-call allocation
	private static final Random RNG = new Random();
	// Shared empty transform to avoid allocations when current entity is null
	private static final Transform EMPTY_TRANSFORM = new Transform();
	// Cached tint/color constants to avoid allocations
	private static final Vector4f TINT_SELECTED = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f);
	private static final Vector4f TINT_NORMAL = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
	private static final Vector4f PATH_RED = new Vector4f(Color.RED.getColorComponents(new float[4]));
	private static final Vector4f PATH_CYAN = new Vector4f(Color.CYAN.getColorComponents(new float[4]));
	public final Transform entityTransform = new Transform();
	private final SegmentController entity;
	// Reusable temporaries to reduce per-frame allocations
	private final Vector3f tmpDir = new Vector3f();
	private final Vector3f tmpDirN = new Vector3f();
	private final Vector3f tmpA = new Vector3f();
	private final Vector3f tmpB = new Vector3f();
	private final Vector3f tmpVec = new Vector3f();
	// Reusable sector temporaries
	private final Vector3i tmpSector = new Vector3i();
	private final Vector3i tmpSectorOther = new Vector3i();
	public Sprite sprite;
	public GUITextOverlay labelOverlay;
	public boolean selected;
	private SegmentController targetData;
	private Indication indication;
	private boolean drawIndication;
	private float selectDepth;
	private float timer;
	// Cache last label text to avoid unnecessary GUI updates
	private String lastLabelText;

	public TacticalMapEntityIndicator(SegmentController entity) {
		this.entity = entity;
	}

	private static Camera getCamera() {
		return TacticalMapGUIDrawer.getInstance().camera;
	}

	public void drawSprite() {
		if(entity.isCloakedFor(getCurrentEntity())) return;
		if(sprite == null) {
			sprite = TacticalMapIndicatorPool.getInstance().acquireSprite();
		}
		entityTransform.set(entity.getWorldTransform());
		SegmentController current = getCurrentEntity();
		if(current != null) {
			Vector3i curSector = current.getSector(tmpSectorOther);
			if(!getSector().equals(curSector)) SectorUtils.transformToSector(entityTransform, curSector, getSector());
		}
		if(sprite != null && !entity.isCoreOverheating()) {
			sprite.setSelectedMultiSprite(getSpriteIndex());
			sprite.setTransform(entityTransform);
			if(selected || getDrawer().selectedEntities.contains(entity)) {
				sprite.setTint(TINT_SELECTED);
			} else {
				sprite.setTint(TINT_NORMAL);
			}
			Sprite.draw3D(sprite, new PositionableSubSprite[]{this}, getCamera());
		}
	}

	private SegmentController getCurrentEntity() {
		if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
			return (SegmentController) GameClient.getCurrentControl();
		} else {
			return null;
		}
	}

	public Vector3i getSector() {
		return entity.getSector(tmpSector);
	}

	public int getSpriteIndex() {
		int entityFaction = entity.getFactionId();
		int playerFactionId = Objects.requireNonNull(getCurrentEntity()).getFactionId();
		FactionRelation.RType relation = Objects.requireNonNull(GameCommon.getGameState()).getFactionManager().getRelation(entityFaction, playerFactionId);
		if((entity.isJammingFor(getCurrentEntity()) || entity.isCloakedFor(getCurrentEntity())) && relation != FactionRelation.RType.FRIEND) {
			return SpriteTypes.UNKNOWN.ordinal();
		} else {
			try {
				if(entity.getType() == SimpleTransformableSendableObject.EntityType.SHIP) {
					switch(relation) {
						case NEUTRAL:
							return SpriteTypes.SHIP_NEUTRAL.ordinal();
						case FRIEND:
							return SpriteTypes.SHIP_FRIENDLY.ordinal();
						case ENEMY:
							return SpriteTypes.SHIP_ENEMY.ordinal();
					}
				} else if(entity.getType() == SimpleTransformableSendableObject.EntityType.SPACE_STATION) {
					if(entity.getFactionId() == FactionManager.PIRATES_ID) {
						if(!selected) {
							return SpriteTypes.STATION_PIRATE.ordinal();
						} else {
							throw new IllegalStateException("Pirate stations should never be selectable!");
						}
					} else if(entity.getFactionId() == FactionManager.TRAIDING_GUILD_ID) {
						if(!selected) {
							return SpriteTypes.STATION_TRADE.ordinal();
						} else {
							throw new IllegalStateException("Trade stations should never be selectable!");
						}
					} else {
						switch(relation) {
							case NEUTRAL:
								return SpriteTypes.STATION_NEUTRAL.ordinal();
							case FRIEND:
								return SpriteTypes.STATION_FRIENDLY.ordinal();
							case ENEMY:
								return SpriteTypes.STATION_ENEMY.ordinal();
						}
					}
				} else if(entity.getType() == SimpleTransformableSendableObject.EntityType.SHOP) {
					return SpriteTypes.SHOP.ordinal();
				}
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("Encountered an exception while trying to pick a map sprite for entity \"" + entity.getName() + "\"", exception);
				return SpriteTypes.UNKNOWN.ordinal();
			}
		}
		return SpriteTypes.UNKNOWN.ordinal();
	}

	private TacticalMapGUIDrawer getDrawer() {
		return TacticalMapGUIDrawer.getInstance();
	}

	public SegmentController getEntity() {
		return entity;
	}

	public void drawLabel(Transform transform) {
		if(labelOverlay == null) {
			labelOverlay = TacticalMapIndicatorPool.getInstance().acquireLabelOverlay();
			// ensure overlay state matches current HUD state if necessary
			try {
				labelOverlay.getScale().y *= -1;
			} catch(Exception ignored) {
			}
		}
		if(entity.isCloakedFor(getCurrentEntity())) {
			return;
		}
		transform.basis.set(getCamera().lookAt(false).basis);
		transform.basis.invert();
		String newText = getEntityDisplay(getCurrentEntity());
		if(!newText.equals(lastLabelText)) {
			labelOverlay.setTextSimple(newText);
			labelOverlay.updateTextSize();
			lastLabelText = newText;
		}
		entityTransform.set(entity.getWorldTransform());
		SegmentController current = getCurrentEntity();
		if(current != null) {
			Vector3i curSector = current.getSector(tmpSectorOther);
			if(!getSector().equals(curSector)) {
				SectorUtils.transformToSector(entityTransform, curSector, getSector());
			}
		}
		labelOverlay.getTransform().set(entityTransform);
		labelOverlay.getTransform().basis.set(transform.basis);
		Vector3f upVector = GlUtil.getUpVector(tmpA, labelOverlay.getTransform());
		upVector.scale((labelOverlay.getText().size() * 10) + 20.0f);
		labelOverlay.getTransform().origin.add(upVector);
		Vector3f rightVector = GlUtil.getRightVector(tmpB, labelOverlay.getTransform());
		rightVector.scale(25.0f);
		labelOverlay.getTransform().origin.add(rightVector);
		labelOverlay.draw();
	}

	private HudIndicatorOverlay getHudOverlay() {
		return GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getIndicator();
	}

	private String getEntityDisplay(SegmentController playerEntity) {
		StringBuilder builder = new StringBuilder();
		if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
			builder.append(distortString(entity.getRealName()));
		} else {
			builder.append(entity.getRealName());
		}
		builder.append("\n");
		ArrayList<PlayerState> attachedPlayers = SegmentControllerUtils.getAttachedPlayers(entity);
		if(!attachedPlayers.isEmpty() && !entity.isJammingFor(playerEntity) && !entity.isCloakedFor(playerEntity)) {
			builder.append(" <").append(attachedPlayers.get(0).getName());
			if(attachedPlayers.size() > 1) {
				builder.append(" + ").append(attachedPlayers.size() - 1).append(" others");
			}
			builder.append(">\n");
		}
		if(entity.getFaction() != null) {
			if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
				builder.append("[").append(distortString(entity.getFaction().getName())).append("]\n");
			} else {
				builder.append("[").append(entity.getFaction().getName()).append("]\n");
			}
		}
		if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
			builder.append("??? mass\n");
		} else {
			builder.append(StringTools.massFormat(entity.getTotalPhysicalMass())).append(" mass\n");
		}
		if(!entity.equals(getCurrentEntity())) {
			if(entity.isJammingFor(playerEntity) || entity.isCloakedFor(playerEntity)) {
				builder.append("???km\n");
			} else {
				builder.append(StringTools.formatDistance(getDistance())).append("\n");
			}
		}
		if(targetData != null) builder.append("Engaging ").append(targetData.getName());
		return builder.toString().trim();
	}

	public String distortString(String s) {
		char[] chars = s.toCharArray();
		for(int i = 0; i < chars.length; i++) {
			int r = RNG.nextInt(2);
			if(r == 0) {
				chars[i] = StringTools.randomString(1).charAt(0);
			}
		}
		return new String(chars);
	}

	public float getDistance() {
		Vector3f currentPos = getCurrentEntityTransform().origin;
		Vector3f entityPos = entity.getWorldTransform().origin;
		return Math.abs(Vector3fTools.distance(currentPos.x, currentPos.y, currentPos.z, entityPos.x, entityPos.y, entityPos.z));
	}

	private Transform getCurrentEntityTransform() {
		SegmentController entity = getCurrentEntity();
		if(entity != null) {
			return entity.getWorldTransform();
		} else {
			return EMPTY_TRANSFORM;
		}
	}

	public SegmentController getCurrentTarget() {
		if(targetData == null && getAIEntity() != null && getAIEntity().getCurrentProgram() instanceof TargetProgram<?> && ((TargetProgram<?>) getAIEntity().getCurrentProgram()).getTarget() != null) {
			SimpleGameObject obj = ((TargetProgram<?>) getAIEntity().getCurrentProgram()).getTarget();
			if(obj instanceof SegmentController) {
				targetData = (SegmentController) obj;
			}
		}
		return targetData;
	}

	public void setCurrentTarget(SegmentController targetData) {
		this.targetData = targetData;
	}

	public void drawTargetingPath(Camera camera) {
		if(getCurrentTarget() != null) {
			Vector3f start = tmpVec;
			start.set(entityTransform.origin);
			Vector3f end = getCurrentTarget().getWorldTransform().origin;
			try {
				end.set(TacticalMapGUIDrawer.getInstance().drawMap.get(getCurrentTarget().getId()).sprite.getPos());
			} catch(Exception exception) {
				exception.printStackTrace();
			}
			if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
				startDrawDottedLine(camera);
				drawDottedLine(start, end, PATH_RED);
				endDrawDottedLine();
			}
		}
	}

	public SegmentControllerAIEntity<?> getAIEntity() {
		switch(entity.getType()) {
			case SHIP:
				return ((Ship) entity).getAiConfiguration().getAiEntityState();
			case SPACE_STATION:
				return ((SpaceStation) entity).getAiConfiguration().getAiEntityState();
			default:
				return null; //Only support Ship or Station AIs
		}
	}

	private void startDrawDottedLine(Camera camera) {
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
	 * Draws a dotted line between the specified points. Use for drawing lines in local scale.
	 *
	 * @param from  The point to start at
	 * @param to    The point to end at
	 * @param color The line's color
	 */
	public void drawDottedLine(Vector3f from, Vector3f to, Vector4f color) {
		// Reuse temporaries to reduce allocations
		tmpDir.sub(to, from);
		tmpDirN.set(tmpDir);
		tmpDirN.normalize();
		float len = tmpDir.length();
		Vector3f a = tmpA;
		Vector3f b = tmpB;
		float dottedSize = Math.min(Math.max(2, len * 0.1f), 40);
		GlUtil.glColor4f(color);
		boolean first = true;
		float f = ((timer % 1.0f) * dottedSize * 2);
		for(; f < len; f += (dottedSize * 2)) {
			a.set(tmpDirN);
			a.scale(f);
			if(first) {
				a.set(0, 0, 0);
				first = false;
			}
			b.set(tmpDirN);
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

	private void endDrawDottedLine() {
		GlUtil.glEnd();
		GlUtil.glPopMatrix();
		GlUtil.glColor4f(1, 1, 1, 1);
		GlUtil.glMatrixMode(GL11.GL_PROJECTION);
		GlUtil.glPopMatrix();
		GlUtil.glMatrixMode(GL11.GL_MODELVIEW);
	}

	public void drawMovementPath(Camera camera) {
		Vector3f start = tmpVec;
		start.set(entityTransform.origin);
		Vector3f end = GlUtil.getForwardVector(tmpA, entity.getWorldTransform());
		end.scale(entity.getSpeedCurrent());
		if(end.length() != 0 && Math.abs(Vector3fTools.sub(start, end).length()) > 1.0f) {
			startDrawDottedLine(camera);
			drawDottedLine(start, end, PATH_CYAN);
			endDrawDottedLine();
		}
	}

	private Transform randomizeTransform(Transform transform) {
		Transform randomizedTransform = new Transform(transform);
		// Use shared RNG and avoid allocating a new Vector3f
		int rx = RNG.nextInt(100);
		int ry = RNG.nextInt(100);
		int rz = RNG.nextInt(100);
		randomizedTransform.origin.x += rx;
		randomizedTransform.origin.y += ry;
		randomizedTransform.origin.z += rz;
		return randomizedTransform;
	}

	public Indication getIndication(Vector3i system) {
		tmpVec.set(entityTransform.origin);
		if(indication == null) {
			Transform transform = new Transform(entityTransform);
			transform.basis.set(getCamera().lookAt(false).basis);
			transform.basis.invert();
			indication = new ConstantIndication(transform, getEntityDisplay((SegmentController) GameClient.getCurrentControl()));
		}
		indication.setText(getEntityDisplay((SegmentController) GameClient.getCurrentControl()));
		indication.getCurrentTransform().origin.set(tmpVec.x - GameMapDrawer.halfsize, tmpVec.y - GameMapDrawer.halfsize, tmpVec.z - GameMapDrawer.halfsize);
		return indication;
	}

	/**
	 * Draws a dotted line between the specified points and scales it by the sector size.
	 * Use for drawing lines on a sector-wide scale.
	 *
	 * @param from       The point to start at
	 * @param to         The point to end at
	 * @param color      The line's color
	 * @param sectorSize The world's sector size
	 */
	public void drawDottedLine(Vector3f from, Vector3f to, Vector4f color, float sectorSize) {
		float sectorSizeHalf = sectorSize * 0.5f;
		tmpA.set((from.x) * sectorSize + sectorSizeHalf, (from.y) * sectorSize + sectorSizeHalf, (from.z) * sectorSize + sectorSizeHalf);
		tmpB.set((to.x) * sectorSize + sectorSizeHalf, (to.y) * sectorSize + sectorSizeHalf, (to.z) * sectorSize + sectorSizeHalf);
		if(!tmpA.equals(tmpB)) {
			drawDottedLine(tmpA, tmpB, color);
		}
	}

	public void update(Timer timer) {
		this.timer += timer.getDelta();
	}

	@Override
	public Vector4f getColor() {
		return sprite.getTint();
	}

	@Override
	public float getScale(long time) {
		return 1.15f;
	}

	@Override
	public int getSubSprite(Sprite sprite) {
		return getSpriteIndex();
	}

	@Override
	public boolean canDraw() {
		return true;
	}

	@Override
	public Vector3f getPos() {
		return entityTransform.origin;
	}

	@Override
	public boolean isDrawIndication() {
		return drawIndication;
	}

	@Override
	public void setDrawIndication(boolean drawIndication) {
		this.drawIndication = drawIndication;
	}

	@Override
	public float getSelectionDepth() {
		return selectDepth;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public void onSelect(float depth) {
		drawIndication = true;
		selectDepth = depth;
		selected = true;
		if(Mouse.getEventButtonState()) {
			if(Mouse.getEventButton() == 0) {
				if(entity.getFactionId() == GameClient.getClientPlayerState().getFactionId()) {
					if(!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
						getDrawer().removeAll();
					}
					getDrawer().addSelection(this);
				} else if(!getDrawer().selectedEntities.isEmpty()) {
					for(SegmentController segmentController : getDrawer().selectedEntities) {
						if(!entity.equals(segmentController) && segmentController instanceof Ship) {
							Ship ship = (Ship) segmentController;
							PacketUtil.sendPacketToServer(new SendAttackPacket(ship, entity));
						}
					}
					getDrawer().removeAll();
				}
			} else {

			}
		}
	}

	@Override
	public void onUnSelect() {
		drawIndication = true;
		selected = false;
		if(Mouse.getEventButtonState()) {

		}
	}

	/**
	 * Release pooled resources for this indicator. After calling this the indicator's sprite/overlay
	 * references are cleared and the resources are returned to the pool for reuse.
	 */
	public void releaseResources() {
		if(sprite != null) {
			TacticalMapIndicatorPool.getInstance().releaseSprite(sprite);
			sprite = null;
		}
		if(labelOverlay != null) {
			TacticalMapIndicatorPool.getInstance().releaseLabelOverlay(labelOverlay);
			labelOverlay = null;
		}
	}

	@Override
	public int hashCode() {
		return getEntityId();
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof TacticalMapEntityIndicator) {
			return ((TacticalMapEntityIndicator) obj).getEntityId() == getEntityId();
		}
		return false;
	}

	public int getEntityId() {
		return entity.getId();
	}

	public Vector3i getSystem() {
		return VoidSystem.getContainingSystem(getSector(), tmpSectorOther);
	}

	public enum SpriteTypes {
		UNKNOWN,
		SHIP_NEUTRAL,
		SHIP_FRIENDLY,
		SHIP_ENEMY,
		STATION_NEUTRAL,
		STATION_FRIENDLY,
		STATION_ENEMY,
		STATION_TRADE,
		STATION_PIRATE,
		SHOP
	}
}
