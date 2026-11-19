package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.damage.Damager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

/**
 * Aura attrition: damage to a ship bleeds its active Aura Projector's power. {@code onDamageServerRootObject} is
 * the server-side funnel every hit on a ship (or anything docked to it) reports to on the rail root.
 */
@Mixin(value = Ship.class, remap = false)
public abstract class ShipAuraAttritionMixin {

	@Inject(method = "onDamageServerRootObject", at = @At("HEAD"))
	private void combatTweaks$auraAttrition(float actualDamage, Damager from, CallbackInfo ci) {
		try {
			AuraProjectorAddOn.onProjectorDamaged((Ship) (Object) this, actualDamage, from);
		} catch(Exception ignored) {
		}
	}
}
