package thederpgamer.combattweaks.gui.hud;

import api.common.GameCommon;
import api.listener.events.gui.HudCreateEvent;
import api.utils.game.PlayerUtils;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.data.PlayerControllable;
import org.schema.game.client.view.gui.shiphud.newhud.Hud;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.SendableSegmentController;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;

import java.util.ArrayList;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class GUIJumpMarkerHolder extends GUIElement {

	private final Hud hud;
	private final ArrayList<JumpMarkerInfo> incoming = new ArrayList<>();

	public GUIJumpMarkerHolder(HudCreateEvent event) {
		super(event.getInputState());
		this.hud = event.getHud();
		event.addElement(this);
	}

	@Override
	public void onInit() {

	}

	@Override
	public void draw() {
		ArrayList<JumpMarkerInfo> toRemove = new ArrayList<>();
		for(JumpMarkerInfo info : incoming) {
			if(info.controller.getSector(new Vector3i()).equals(GameClientState.instance.getPlayer().getCurrentSector())) toRemove.add(info);
			else hud.getIndicator().drawFor(info.transform, info.displayedName, -300, true, false);
		}
		incoming.removeAll(toRemove);
	}

	@Override
	public void cleanUp() {

	}

	@Override
	public float getWidth() {
		return 0;
	}

	@Override
	public float getHeight() {
		return 0;
	}

	public void addNewIncomingJump(SendableSegmentController controller) {
		String displayedName = "INCOMING CONTACT ";
		PlayerControllable controlled = PlayerUtils.getCurrentControl(((GameClientState) getState()).getPlayer());
		if(controlled instanceof ManagedUsableSegmentController<?>) {
			IntelLevel intelLevel = getIntelLevel((ManagedUsableSegmentController<?>) controlled, controller);
			switch(intelLevel) {
				case LOW:
				case MEDIUM:
					displayedName += " [" + getRelation(controlled, controller) + "]\n" + StringTools.massFormat(intelLevel.getMassEstimate(controller));
					break;
				case HIGH:
					displayedName += " [" + FactionManager.getFactionName(controller) + "]\n" + StringTools.massFormat(intelLevel.getMassEstimate(controller));
					break;
			}
		} else displayedName += "[UNKNOWN]\nUnknown Mass";
		incoming.add(new JumpMarkerInfo(getIncomingTransform(controller, controlled), controller, displayedName));
	}

	public void removeIncomingJump(SendableSegmentController controller) {
		for(JumpMarkerInfo info : incoming) {
			if(info.controller.equals(controller)) {
				incoming.remove(info);
				break;
			}
		}
	}


	private String getRelation(PlayerControllable controlled, SendableSegmentController controller) {
		FactionRelation.RType relation;
		if(controlled instanceof SegmentController) relation = GameCommon.getGameState().getFactionManager().getRelation(((SegmentController) controlled).getFactionId(), controller.getFactionId());
		else relation = GameCommon.getGameState().getFactionManager().getRelation(controlled.getAttachedPlayers().get(0).getFactionId(), controller.getFactionId());
		switch(relation) {
			case NEUTRAL:
				return "NEUTRAL";
			case FRIEND:
				return "FRIENDLY";
			case ENEMY:
				return "HOSTILE";
		}
		return "UNKNOWN";
	}

	private IntelLevel getIntelLevel(ManagedUsableSegmentController<?> controlled, SendableSegmentController controller) {
		float ownRecon = controlled.getReconStrength();
		float enemyStealth = controller.getStealthStrength();
		float diff = Math.max(0, ownRecon - enemyStealth);
		if(diff > 0.5f) return IntelLevel.HIGH;
		else if(diff > 0.2f) return IntelLevel.MEDIUM;
		else return IntelLevel.LOW;
	}

	/**
	 * Calculates a transform for the incoming jump marker.
	 * <p>The transform is calculated based on the position of the incoming jump and the position of the player's ship
	 * and is put onto the players screen relative to where the incoming ship would appear after the jump is completed.</p>
	 *
	 * @param controller The controller of the incoming ship.
	 * @param controlled The controller of the player's ship.
	 * @return The transform for the incoming jump marker.
	 */
	private Transform getIncomingTransform(SendableSegmentController controller, PlayerControllable controlled) {
		Transform transform = new Transform();
		transform.setIdentity();
		transform.origin.set(controller.getWorldTransform().origin);
		/* Not even sure if the below is needed, but it's here just in case I do.
		if(controlled instanceof SegmentController) transform.origin.sub(((SegmentController) controlled).getWorldTransform().origin);
		else {
			Transform playerTransform = new Transform();
			controlled.getAttachedPlayers().get(0).getWordTransform(playerTransform);
			transform.origin.sub(playerTransform.origin);
		}
		transform.origin.scale(1f / 100f);
		 */
		return transform;
	}

	private static final int UNKNOWN = 0;
	private static final int SHOW_ALLEGIANCE = 1;
	private static final int SHOW_FACTION = 2;

	public enum IntelLevel {
		LOW(UNKNOWN, 0.30, false), //Name type, How accurate the mass estimation will be (lower number = better), If the faction name should be visible
		MEDIUM(SHOW_ALLEGIANCE, 0.15, true),
		HIGH(SHOW_FACTION, 0.05, true);

		public final int nameType;
		public final double massAccuracy;
		public final boolean showFaction;

		IntelLevel(int nameType, double massAccuracy, boolean showFaction) {
			this.nameType = nameType;
			this.massAccuracy = massAccuracy;
			this.showFaction = showFaction;
		}

		public double getMassEstimate(SendableSegmentController controller) {
			return controller.getMass() * (1 + (Math.random() * massAccuracy * 2 - massAccuracy));
		}
	}

	public static class JumpMarkerInfo {

		public final Transform transform;
		private final SendableSegmentController controller;
		public final String displayedName;

		public JumpMarkerInfo(Transform transform, SendableSegmentController controller, String displayedName) {
			this.transform = transform;
			this.controller = controller;
			this.displayedName = displayedName;
		}
	}
}