package thederpgamer.combattweaks.listener;

import api.listener.fastevents.DamageBeamHitListener;
import org.schema.game.common.controller.BeamHandlerContainer;
import org.schema.game.common.controller.damage.beam.DamageBeamHitHandlerSegmentController;
import org.schema.game.common.controller.elements.BeamState;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.world.Segment;
import org.schema.schine.graphicsengine.core.Timer;
import thederpgamer.combattweaks.calculator.BeamCalculator;

import javax.vecmath.Vector3f;
import java.util.Collection;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class BeamListener implements DamageBeamHitListener {
	@Override
	public void handle(BeamState beamState, int hits, BeamHandlerContainer<?> beamHandlerContainer, SegmentPiece segmentPiece, Vector3f from, Vector3f to, Timer timer, Collection<Segment> updatedSegments, DamageBeamHitHandlerSegmentController damageBeamHitHandlerSegmentController) {
		if(segmentPiece.getInfo().isArmor()) BeamCalculator.calculate(beamState, segmentPiece);
	}
}