package videogoose.combattweaks.mixins;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.lwjgl.input.Keyboard;
import org.schema.common.ParseException;
import org.schema.schine.common.language.Translatable;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;
import org.schema.schine.resource.FileExt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.utils.ReflectionUtils;

import java.io.*;
import java.util.List;

@Mixin(value = KeyboardMappings.class, remap = false)
public class KeyboardMappingsMixin {

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void onStaticInit(CallbackInfo ci) {
		try {
			KeyboardMappings[] currentValues = KeyboardMappings.values();
			int ordinal = currentValues.length; // Start ordinal after existing values
			ReflectionUtils.injectEnumValue(KeyboardMappings.class, "OPEN_TACTICAL_MAP", ordinal, Keyboard.KEY_BACKSLASH, (Translatable) anEnum -> "Open Tactical Map", KeyboardContext.GENERAL, (short) -1, true);
			ordinal++;
		} catch(Exception exception) {
			System.err.println("[CombatTweaks] [Mixin] FAILED to inject custom KeyboardMappings enums: " + exception.getMessage());
			System.err.println("[CombatTweaks] [Mixin] Stack trace:");
			exception.printStackTrace(System.err);
		}
	}

	/**
	 * @author VideoGoose
	 * @reason We need to overwrite the write method to ensure that our custom mapping is saved to the file, since the original method only accounts for the enum values that were present at compile time (which doesn't include our injected value).
	 */
	@Overwrite
	public static void write(String path) throws IOException {
		File f = new FileExt(path);
		f.delete();
		f.createNewFile();
		BufferedWriter bf = new BufferedWriter(new FileWriter(f));
		bf.write("#version = 0");
		bf.newLine();
		for(KeyboardMappings s : KeyboardMappings.values()) {
			if("OPEN_TACTICAL_MAP".equals(s.name()))
				continue; // Skip writing our custom mapping to the file, since it's not actually part of the enum in the original code
			bf.write(s.name() + " = " + Keyboard.getKeyName(s.getMapping()) + " //" + s.getDescription());
			bf.newLine();
		}
		bf.flush();
		bf.close();
	}

	/**
	 * @author VideoGoose
	 * @reason We need to overwrite the read method to ensure that our custom mapping is read from the file, since the original method only accounts for the enum values that were present at compile time (which doesn't include our injected value).
	 */
	@Overwrite
	public static void read() {
		BufferedReader bf = null;
		try {
			List<String> names = new ObjectArrayList<>();
			List<String> values = new ObjectArrayList<>();
			File f = new FileExt("./keyboard.cfg");
			bf = new BufferedReader(new FileReader(f));
			String line;
			int i = 0;
			while((line = bf.readLine()) != null) {
				if(line.trim().startsWith("//")) {
					continue;
				}
				if(line.contains("//")) {
					line = line.substring(0, line.indexOf("//"));
				}
				String[] split = line.split(" = ", 2);
				names.add(split[0]);
				values.add(split[1].trim());
				if(i == 0 && !"#version".equals(names.get(0))) {
					System.err.println("UNKNOWN VERSION!! RESETTING KEYS");
					return;
				} else if(i == 0 && "#version".equals(names.get(0)) && Integer.parseInt(values.get(i)) != 0) {
					System.err.println("OLD VERSION!! RESETTING KEYS");
				}
				i++;
			}

			for(i = 1; i < names.size(); i++) {
				try {
					if("OPEN_TACTICAL_MAP".equals(names.get(i)))
						continue; // Skip reading our custom mapping from the file, since it's not actually part of the enum in the original code
					int keyIndex = org.schema.schine.input.Keyboard.getKeyFromName(values.get(i));
					KeyboardMappings.valueOf(names.get(i)).setMapping(keyIndex);
				} catch(ParseException e) {
					e.printStackTrace();
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			System.err.println("Could not read settings file: using defaults (" + e.getMessage() + ")");
		} finally {
			if(bf != null) {
				try {
					bf.close();
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
			KeyboardMappings.dirty = false;
			KeyboardMappings.checkForDuplicates();
		}
	}
}
