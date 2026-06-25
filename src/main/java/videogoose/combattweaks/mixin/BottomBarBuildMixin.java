package videogoose.combattweaks.mixin;

import org.schema.game.client.view.gui.shiphud.newhud.BottomBarBuild;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

/**
 * Hides the build hotbar while the tactical map is open (companion to {@link WeaponBottomBarMixin}); its number
 * keys are repurposed for control groups while the map is up.
 */
@Mixin(value = BottomBarBuild.class, remap = false)
public abstract class BottomBarBuildMixin {

	@Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
	private void combatTweaks$hideOnTacticalMap(CallbackInfo ci) {
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		if(drawer != null && drawer.toggleDraw) {
			ci.cancel();
		}
	}
}
