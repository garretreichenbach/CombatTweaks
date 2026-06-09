package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.server.ai.SegmentControllerAIEntity;
import org.schema.game.server.ai.program.common.states.ShipGameState;
import org.schema.schine.ai.stateMachines.AIConfigurationInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import videogoose.combattweaks.utils.AIUtils;
import videogoose.combattweaks.utils.CTLog;

/**
 * Lets a commanded ship close on (and then fire at) an attack target the engine's {@code findTarget} would
 * otherwise refuse to acquire — most importantly a <em>neutral, unmanned</em> ship, which nothing but our
 * order will ever engage.
 *
 * <p>{@code findTarget} has a dedicated branch for a <em>specific</em> target id (which our attack order
 * sets): it re-acquires that exact entity instead of picking a random enemy. Two gates in that branch block
 * a commanded attack on a neutral derelict, and we relax both — but only for a ship that is actually under a
 * CombatTweaks attack order (checked via {@code this}, the searching ship's own game-state):</p>
 *
 * <ul>
 *   <li><b>Range</b> ({@code getShootingRange}, ordinal 0): the branch bails the moment the target is farther
 *   than weapon range, so the ship never acquires it and so never transitions into getting-to-target / never
 *   approaches. We report an effectively infinite range there; the engine's getting-to-target then flies the
 *   ship in using the <em>real</em> range (a separate, untouched call) and hands off to the shoot state at the
 *   normal weapon distance.</li>
 *   <li><b>"Inactive AI" rejection</b> ({@code isActiveAI}): the branch discards a target ship that has no
 *   attached players <em>and</em> an inactive AI — i.e. an unmanned derelict/neutral. We force that check to
 *   read "active" so the commanded target is accepted.</li>
 * </ul>
 *
 * <p>Non-commanded ships, and every other range/AI check in the game, are untouched.</p>
 */
@Mixin(value = ShipGameState.class, remap = false)
public abstract class ShipGameStateMixin {

	/** The attack-target id of the ship doing this search, or null if it isn't under a CombatTweaks attack order. */
	private Integer combatTweaks$selfAttackTarget() {
		try {
			SegmentController self = ((ShipGameState) (Object) this).getEntityState().getEntity();
			return self != null ? AIUtils.getAttackTarget(self.getId()) : null;
		} catch(Exception ignored) {
			return null;
		}
	}

	@Redirect(method = "findTarget(ZZ[Lorg/schema/game/common/data/world/SimpleTransformableSendableObject$EntityType;)V", at = @At(value = "INVOKE", target = "Lorg/schema/game/server/ai/SegmentControllerAIEntity;getShootingRange()F", ordinal = 0), remap = false)
	private float combatTweaks$reachCommandedTargetAtAnyRange(SegmentControllerAIEntity<?> state) {
		if(combatTweaks$selfAttackTarget() != null) {
			// Under a commanded attack: acquire the specific target no matter how far, so the ship approaches
			// it. Real weapon-range gating still happens later in getting-to-target/engaging.
			CTLog.debugThrottled("rangeBypass:" + state.getEntity().getId(), 2000, "[ATTACK] findTarget range bypass active for ship=" + state.getEntity().getId()
					+ " (real shootingRange=" + state.getShootingRange() + ")");
			return Float.MAX_VALUE;
		}
		return state.getShootingRange();
	}

	@Redirect(method = "findTarget(ZZ[Lorg/schema/game/common/data/world/SimpleTransformableSendableObject$EntityType;)V", at = @At(value = "INVOKE", target = "Lorg/schema/schine/ai/stateMachines/AIConfigurationInterface;isActiveAI()Z"), remap = false)
	private boolean combatTweaks$acceptUnmannedCommandedTarget(AIConfigurationInterface config) {
		if(combatTweaks$selfAttackTarget() != null) {
			// Under a commanded attack: don't let the specific target be discarded just because it's an unmanned
			// ship with an inactive AI (a neutral derelict). The player explicitly ordered the attack.
			CTLog.debugThrottled("aiActiveBypass:" + System.identityHashCode(this), 2000, "[ATTACK] findTarget treating commanded target as active-AI so it isn't rejected as unmanned");
			return true;
		}
		return config.isActiveAI();
	}
}
