package videogoose.combattweaks.manager;

import org.lwjgl.input.Keyboard;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.config.MainConfig;
import videogoose.combattweaks.config.SystemConfig;

public class ConfigManager {

	private static MainConfig mainConfig;
	private static SystemConfig systemConfig;

	public static void initialize(CombatTweaks instance) {
		mainConfig = new MainConfig(instance);
		systemConfig = new SystemConfig(instance);
	}

	public static MainConfig getMainConfig() {
		return mainConfig;
	}

	public static SystemConfig getSystemConfig() {
		return systemConfig;
	}

	public static int getTacticalMapKey() {
		String keyName = mainConfig.tacticalMapKeybind.value;
		if(keyName == null || keyName.isEmpty()) return Keyboard.KEY_BACKSLASH;
		int key = Keyboard.getKeyIndex(keyName.toUpperCase().trim());
		return key != Keyboard.KEY_NONE ? key : Keyboard.KEY_BACKSLASH;
	}
}
