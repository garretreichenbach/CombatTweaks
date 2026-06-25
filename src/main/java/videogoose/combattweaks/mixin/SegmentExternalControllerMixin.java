package videogoose.combattweaks.mixin;

import org.schema.game.client.controller.manager.ingame.SegmentExternalController;
import org.schema.schine.input.KeyEventInterface;
import org.schema.schine.input.KeyboardMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

/**
 * Build-mode counterpart to {@link ShipExternalFlightControllerMixin}: the build hotbar also selects a slot for
 * number keys 2–11 (1…0) in {@code handleKeyEvent}. While the tactical map is open those keys belong to control
 * groups, so we cancel the build-hotbar selection for them. Other keys pass through unchanged.
 */
@Mixin(value = SegmentExternalController.class, remap = false)
public abstract class SegmentExternalControllerMixin {

	@Inject(method = "handleKeyEvent", at = @At("HEAD"), cancellable = true, remap = false)
	private void combatTweaks$blockHotbarKeysOnTacticalMap(KeyEventInterface e, CallbackInfo ci) {
		try {
			TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
			if(drawer != null && drawer.toggleDraw) {
				int raw = KeyboardMappings.getEventKeyRaw(e);
				if(raw >= 2 && raw <= 11) {
					ci.cancel();
				}
			}
		} catch(Exception ignored) {
		}
	}
}
