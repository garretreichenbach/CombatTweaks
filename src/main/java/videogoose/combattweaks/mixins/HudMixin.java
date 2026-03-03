package videogoose.combattweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.schema.game.client.view.gui.shiphud.newhud.Hud", remap = false)
public class HudMixin {

	@Inject(method = "onInit", at = @At("RETURN"))
	public void onInit(CallbackInfo ci) {
		System.err.println("FUCK");
	}
}
