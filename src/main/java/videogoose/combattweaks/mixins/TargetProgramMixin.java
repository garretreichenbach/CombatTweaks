package videogoose.combattweaks.mixins;

import org.schema.game.common.data.SimpleGameObject;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.CombatTweaks;

/**
 * Debug mixin to track when TargetProgram targets are set/cleared.
 * Helps identify why mining/repair targets are being lost after ~10 seconds.
 */
@Mixin(value = TargetProgram.class, remap = false)
public class TargetProgramMixin {

	@Inject(method = "setTarget", at = @At("HEAD"), require = 1)
	private void onSetTarget(SimpleGameObject target, CallbackInfo ci) {
		if(target instanceof SimpleTransformableSendableObject<?>) {
			SimpleTransformableSendableObject<?> obj = (SimpleTransformableSendableObject<?>) target;
			String targetName = obj.getClass().getSimpleName() + "#" + obj.getId();
			CombatTweaks.getInstance().logInfo("TargetProgram.setTarget: " + targetName);
		}
	}

	@Inject(method = "setSpecificTargetId", at = @At("HEAD"), require = 0, remap = false)
	private void onSetSpecificTargetId(int id, CallbackInfo ci) {
		CombatTweaks.getInstance().logInfo("TargetProgram.setSpecificTargetId: " + id);
	}
}
