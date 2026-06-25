package videogoose.combattweaks.mixin;

import org.schema.game.client.view.gui.weapon.WeaponBottomBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

/**
 * Hides the weapon hotbar while the tactical map is open — its number keys are repurposed for control groups
 * (see {@link ShipExternalFlightControllerMixin}) and the bar would otherwise clutter the map view.
 */
@Mixin(value = WeaponBottomBar.class, remap = false)
public abstract class WeaponBottomBarMixin {

	@Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
	private void combatTweaks$hideOnTacticalMap(CallbackInfo ci) {
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		if(drawer != null && drawer.toggleDraw) {
			ci.cancel();
		}
	}
}
