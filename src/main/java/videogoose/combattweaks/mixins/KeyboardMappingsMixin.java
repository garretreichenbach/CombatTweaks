package videogoose.combattweaks.mixins;

import org.lwjgl.input.Keyboard;
import org.schema.schine.common.language.Translatable;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.utils.ReflectionUtils;

@Mixin(value = KeyboardMappings.class, remap = false)
public class KeyboardMappingsMixin {

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void onStaticInit(CallbackInfo ci) {
		try {
			KeyboardMappings[] currentValues = KeyboardMappings.values();
			int ordinal = currentValues.length; // Start ordinal after existing values
			ReflectionUtils.injectEnumValue(KeyboardMappings.class, "OPEN_TACTICAL_MAP", ordinal++, Keyboard.KEY_BACKSLASH, (Translatable) anEnum -> "Open Tactical Map", KeyboardContext.GENERAL, (short) -1, true);
		} catch(Exception exception) {
			System.err.println("[CombatTweaks] [Mixin] FAILED to inject custom KeyboardMappings enums: " + exception.getMessage());
			System.err.println("[CombatTweaks] [Mixin] Stack trace:");
			exception.printStackTrace(System.err);
		}
	}
}
