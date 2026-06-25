package videogoose.combattweaks.mixin;

import org.schema.game.client.controller.manager.ingame.ship.ShipExternalFlightController;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.KeyboardMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

/**
 * While the tactical map is open, the number keys drive CombatTweaks control groups, so they must not also change
 * the ship's weapon hotbar. {@code handleKeyEvent} selects a weapon bottom-bar slot for key codes 2–11 (1…0) and
 * doesn't check the map state, so we cancel it for those keys whenever the map is open. Other keys pass through.
 */
@Mixin(value = ShipExternalFlightController.class, remap = false)
public abstract class ShipExternalFlightControllerMixin {

	@Inject(method = "handleKeyEvent", at = @At("HEAD"), cancellable = true, remap = false)
	private void combatTweaks$blockHotbarKeysOnTacticalMap(KeyEventInterface e, CallbackInfo ci) {
		try {
			TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
			if(drawer != null && drawer.toggleDraw) {
				int raw = KeyboardMappings.getEventKeyRaw(e);
				if(raw >= 2 && raw <= 11) { // number keys 1…0 — reserved for control groups while the map is open
					ci.cancel();
				}
			}
		} catch(Exception ignored) {
		}
	}
}
