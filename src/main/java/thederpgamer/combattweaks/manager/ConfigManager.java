package thederpgamer.combattweaks.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.combattweaks.CombatTweaks;

public class ConfigManager {

	public static final String[] defaultMainConfig = {
			"debug_mode: false",
			"tactical_map_view_distance: 1.2"
	};

	public static final String[] defaultSystemConfig = {
			"armor_hp_value_multiplier: 30.0",
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
}
