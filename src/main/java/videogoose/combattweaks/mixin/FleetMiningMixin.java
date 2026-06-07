package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.elements.FocusableUsableModule;
import org.schema.game.common.controller.elements.beam.harvest.SalvageBeamCollectionManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.server.ai.program.common.states.ShipGameState;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetMining;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
 *
 * <p>Finding the true nearest block means scanning every block of the asteroid, which is too expensive
 * to run on every AI tick (it froze the server). So the scan is throttled: we re-find the nearest block
 * a few times a second and reuse it in between, re-scanning immediately if the cached block has been
 * mined out.</p>
 */
@Mixin(value = FleetMining.class, remap = false)
public abstract class FleetMiningMixin {

	/**
	 * How long an aim target is held before re-finding the nearest block (ms). This both bounds the
	 * per-tick scan cost AND stabilises the aim: with FOCUSED firing a block dies almost instantly, so
	 * re-scanning the moment it's mined would pick a new (often opposite-side) nearest block every tick
	 * and the ship would whip around chasing it. Instead we hold the aim direction for this interval —
	 * the FOCUSED beam simply drills through to the block behind the mined cell — so the ship aims
	 * steadily and only steps to a new spot a few times a second.
	 */
	private static final long COMBATTWEAKS$SCAN_INTERVAL_MS = 350;

	@Shadow
	boolean found;
	@Shadow
	private boolean foundBlock;
	@Shadow
	private SegmentPiece currentTarget;

	/** Reused across calls (single-threaded AI tick) so we don't allocate a finder every scan. */
	private final NearestBlockPiece combatTweaks$finder = new NearestBlockPiece();
	private long combatTweaks$nextScan;

	@Inject(method = "findTargetBlock", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$targetNearestBlock(SegmentController target, CallbackInfoReturnable<Boolean> cir) {
		long now = System.currentTimeMillis();

		// Hold the current aim for the whole interval, even once its block is mined out — re-aiming the
		// instant a block dies makes the ship flicker/spin under fast FOCUSED firing. We only require that
		// the target still references a loaded segment; the beam drills on through the emptied cell.
		if(now < combatTweaks$nextScan && combatTweaks$currentTargetLoaded()) {
			found = true;
			foundBlock = true;
			cir.setReturnValue(true);
			return;
		}
		combatTweaks$nextScan = now + COMBATTWEAKS$SCAN_INTERVAL_MS;

		found = false;
		foundBlock = false;
		// getEntity() is inherited (ShipGameState); reach it via a cast rather than @Shadow.
		Ship miner = ((ShipGameState) (Object) this).getEntity();
		combatTweaks$finder.reset(miner.getWorldTransform().origin, target.getWorldTransform());
		target.getSegmentBuffer().iterateOverNonEmptyElement(combatTweaks$finder, true);
		if(combatTweaks$finder.found && combatTweaks$finder.bestSegment != null) {
			currentTarget.setByReference(combatTweaks$finder.bestSegment, combatTweaks$finder.bestPos);
			foundBlock = true;
			found = true;
		}
		cir.setReturnValue(foundBlock);
	}

	/**
	 * Forces salvage beams to FOCUSED while mining instead of the engine's hard-coded UNFOCUSED.
	 *
	 * <p>{@code FleetMining.updateAI} sets every salvage group to {@link FocusableUsableModule.FireMode#UNFOCUSED}
	 * right before firing. UNFOCUSED rakes the array's beams across the ship's face as a parallel spread —
	 * and because a salvage-only ship has no weapons, the AI's aim reference falls back to the ship centre,
	 * so the spread lands all around the target block rather than on it (poor accuracy / "shooting near
	 * it"). FOCUSED concentrates the array on the aim point, so the beams actually hit the targeted
	 * surface. We redirect that one call to set FOCUSED instead.</p>
	 */
	@Redirect(method = "updateAI", at = @At(value = "INVOKE",
			target = "Lorg/schema/game/common/controller/elements/beam/harvest/SalvageBeamCollectionManager;setFireMode(Lorg/schema/game/common/controller/elements/FocusableUsableModule$FireMode;)V"),
			remap = false)
	private void combatTweaks$forceFocusedSalvage(SalvageBeamCollectionManager group, FocusableUsableModule.FireMode mode) {
		group.setFireMode(FocusableUsableModule.FireMode.FOCUSED);
	}

	/**
	 * Whether the cached {@link #currentTarget} still references a loaded segment. We deliberately do NOT
	 * require the block itself to be un-mined — holding the aim on an emptied cell (the beam drills through
	 * to the block behind it) is what keeps the ship from flickering under fast FOCUSED firing.
	 */
	private boolean combatTweaks$currentTargetLoaded() {
		try {
			return currentTarget != null && currentTarget.getSegment() != null;
		} catch(Exception e) {
			return false;
		}
	}
}
