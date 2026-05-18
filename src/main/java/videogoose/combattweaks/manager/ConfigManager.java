package videogoose.combattweaks.manager;

import api.mod.config.FileConfiguration;
import org.lwjgl.input.Keyboard;
import videogoose.combattweaks.CombatTweaks;

public class ConfigManager {

	public static final String[] defaultMainConfig = {
			"debug_mode: false",
			"tactical_map_view_distance: 1.2",
			"tactical_map_keybind: BACKSLASH"
	};

	public static final String[] defaultSystemConfig = {
			"armor_hp_value_multiplier: 20.0",
			"armor_hp_scaling_exponent: 0.75",
			"armor_hp_lost_per_damage_absorbed: 1.0",
			"base_armor_hp_bleed_through_start: 0.7",
			"armor_hp_absorption_effect_1_sub: -7.5",
			"armor_hp_absorption_effect_2_sub: -7.5",
	};
	
	//Main Config
	private static FileConfiguration mainConfig;

	//System Config
	private static FileConfiguration systemConfig;

	public static void initialize(CombatTweaks instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);

		systemConfig = instance.getConfig("system_config");
		systemConfig.saveDefault(defaultSystemConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static FileConfiguration getSystemConfig() {
		return systemConfig;
	}

	public static int getTacticalMapKey() {
		String keyName = mainConfig.getString("tactical_map_keybind");
		if(keyName == null || keyName.isEmpty()) return Keyboard.KEY_BACKSLASH;
		int key = Keyboard.getKeyIndex(keyName.toUpperCase().trim());
		return key != Keyboard.KEY_NONE ? key : Keyboard.KEY_BACKSLASH;
	}
}
