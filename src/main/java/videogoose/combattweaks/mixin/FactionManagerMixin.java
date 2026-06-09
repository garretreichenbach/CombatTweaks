package videogoose.combattweaks.mixin;

import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.utils.AIUtils;

/**
 * Lets a commanded ship attack a target the engine would otherwise refuse to fight (a neutral).
 *
 * <p>The combat AI only engages targets it considers hostile — its target search and several checks call
 * {@code FactionManager.isEnemy(entity, entity)}, and the specific-target path can fall through to that
 * enemy-only search. So ordering an attack on a non-hostile (neutral) ship leaves the attacker idle. When
 * a player explicitly issues an attack via the tactical map (confirmed for neutrals), we want it honoured
 * regardless of faction standing. This forces {@code isEnemy} to return true for exactly the commanded
 * attacker&rarr;target pair (and only while that attack order is active), so the AI acquires, engages and
 * fires on it. All other faction relations are untouched.</p>
 */
@Mixin(value = FactionManager.class, remap = false)
public abstract class FactionManagerMixin {

	@Inject(method = "isEnemy(Lorg/schema/game/common/data/world/SimpleTransformableSendableObject;Lorg/schema/game/common/data/world/SimpleTransformableSendableObject;)Z", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$forceCommandedAttackEnemy(SimpleTransformableSendableObject a, SimpleTransformableSendableObject b, CallbackInfoReturnable<Boolean> cir) {
		try {
			if(a == null || b == null) {
				return;
			}
			// Resolve a to its rail root: a docked turret searches for targets as itself, but the attack order
			// is recorded against the root ship it fires for — so look the order up under the root's id.
			Integer target = AIUtils.getAttackTarget(AIUtils.orderId(a));
			if(target != null && target == b.getAsTargetId()) {
				cir.setReturnValue(true); // a is under a CombatTweaks order to attack b — treat b as hostile
			}
		} catch(Exception ignored) {
		}
	}
}
