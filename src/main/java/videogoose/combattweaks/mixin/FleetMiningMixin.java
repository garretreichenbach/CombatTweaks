package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.server.ai.program.common.states.ShipGameState;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetMining;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.utils.NearestBlockPiece;

/**
 * Makes the engine's mining state aim at the block nearest the ship instead of the first block in
 * grid order.
 *
 * <p>Vanilla {@code FleetMining.findTargetBlock} returns a fixed block (the first non-empty one in
 * segment-grid order — effectively a corner of the asteroid). NPC mining fleets work because their
 * formation parks ships right at that block. Our solo miner sits at whatever surface it approached,
 * so it would aim at that far corner block and the beam misses. Here we pick the block closest to the
 * ship, so the beam hits the surface the ship is actually parked next to.</p>
 */
@Mixin(value = FleetMining.class, remap = false)
public abstract class FleetMiningMixin {

	@Shadow
	boolean found;
	@Shadow
	private boolean foundBlock;
	@Shadow
	private SegmentPiece currentTarget;

	@Inject(method = "findTargetBlock", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$targetNearestBlock(SegmentController target, CallbackInfoReturnable<Boolean> cir) {
		found = false;
		foundBlock = false;
		// getEntity() is inherited (ShipGameState); reach it via a cast rather than @Shadow.
		Ship miner = ((ShipGameState) (Object) this).getEntity();
		NearestBlockPiece finder = new NearestBlockPiece();
		finder.reset(miner.getWorldTransform().origin, target.getWorldTransform());
		target.getSegmentBuffer().iterateOverNonEmptyElement(finder, true);
		if(finder.found && finder.bestSegment != null) {
			currentTarget.setByReference(finder.bestSegment, finder.bestPos);
			foundBlock = true;
			found = true;
		}
		cir.setReturnValue(foundBlock);
	}
}
