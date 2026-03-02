package videogoose.combattweaks.mixin;

import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.GLFW;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import videogoose.combattweaks.util.ReflectionUtils;

@Mixin(value = KeyboardMappings.class, remap = false)
public class KeyboardMappingsMixin {

	/**
	 * Adds the custom key bindings to the array of key bindings used by the game.
	 * Uses reflection to dynamically create and inject custom enum constants.
	 */
	@Inject(method = "values", at = @At("RETURN"), remap = false, cancellable = true)
	private static void addCustomKeyBindings(CallbackInfoReturnable<KeyboardMappings[]> cir) {
		try {
			KeyboardMappings[] original = cir.getReturnValue();
			KeyboardMappings[] customBindings = {
					ReflectionUtils.createEnumConstant(KeyboardMappings.class, "TOGGLE_TACTICAL_MAP", original.length, Integer.valueOf(GLFW.GLFW_KEY_BACKSLASH), Lng.str("Open/Close Tactical Map"), KeyboardContext.SHIP, (short) -1, Boolean.TRUE)
			};
			KeyboardMappings[] modified = new KeyboardMappings[original.length + 1];
			System.arraycopy(original, 0, modified, 0, original.length);
			System.arraycopy(customBindings, 0, modified, original.length, customBindings.length);
			// Return the modified array to callers of KeyboardMappings.values()
			cir.setReturnValue(modified);
		} catch(Exception e) {
			// Fallback logging (avoid compile-time dependency on mod main class inside mixin)
			System.err.println("Failed to inject custom keyboard mappings: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
