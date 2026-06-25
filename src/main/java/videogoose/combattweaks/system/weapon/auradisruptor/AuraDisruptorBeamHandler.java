package videogoose.combattweaks.system.weapon.auradisruptor;

import api.common.GameCommon;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.view.beam.BeamColors;
import org.schema.game.common.controller.BeamHandlerContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.BeamState;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.beam.BeamHandler;
import org.schema.game.common.data.physics.CubeRayCastResult;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.Segment;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;
import java.util.Collection;

/**
 * Beam handler for the Aura Disruptor. Only hits non-friendly ships that carry an <i>active</i> aura projector of
 * either role (support or offense); on hit it calls {@link AuraProjectorAddOn#disrupt} to drain the target's aura
 * power. Ported from BetterChambers, with the YAML config/registry references re-homed onto CombatTweaks.
 */
public class AuraDisruptorBeamHandler extends BeamHandler {

	public AuraDisruptorBeamHandler(SegmentController segmentController, BeamHandlerContainer beamHandlerContainer) {
		super(segmentController, beamHandlerContainer);
	}

	@Override
	public boolean canhit(BeamState beamState, SegmentController segmentController, String[] strings, Vector3i vector3i) {
		if(getBeamShooter().getId() != segmentController.getId()) {
			int shooterFactionId = getBeamShooter().getFactionId();
			int entityId = segmentController.getFactionId();
			FactionManager factionManager = GameCommon.getGameState().getFactionManager();
			if(factionManager.getRelation(shooterFactionId, entityId) != FactionRelation.RType.FRIEND || ConfigManager.getMainConfig().debugMode.getValue()) {
				if(segmentController instanceof ManagedSegmentController<?>) {
					AuraProjectorAddOn addOn = AuraProjectorAddOn.getActiveAura((ManagedSegmentController<?>) segmentController);
					return addOn != null && addOn.isActive();
				}
			}
		}
		return false;
	}

	@Override
	public float getBeamTimeoutInSecs() {
		return 0.05f;
	}

	@Override
	public float getBeamToHitInSecs(BeamState beamState) {
		return beamState.getTickRate();
	}

	@Override
	public int onBeamHit(BeamState beamState, int hits, BeamHandlerContainer<SegmentController> beamHandlerContainer, SegmentPiece segmentPiece, Vector3f vector3f, Vector3f vector3f1, Timer timer, Collection<Segment> collection) {
		try {
			SegmentController hit = beamState.segmentHit.getSegmentController();
			if(hit instanceof ManagedSegmentController<?>) {
				AuraProjectorAddOn addOn = AuraProjectorAddOn.getActiveAura((ManagedSegmentController<?>) hit);
				if(addOn != null) {
					addOn.disrupt(getShootingEntity(), beamState.getPower());
				}
			}
		} catch(Exception ignored) {
		}
		return hits;
	}

	@Override
	protected boolean onBeamHitNonCube(BeamState beamState, int i, BeamHandlerContainer<SegmentController> beamHandlerContainer, Vector3f vector3f, Vector3f vector3f1, CubeRayCastResult cubeRayCastResult, Timer timer, Collection<Segment> collection) {
		return false;
	}

	@Override
	protected Vector4f getDefaultColor(BeamState beamState) {
		return getColorRange(BeamColors.PURPLE);
	}
}
