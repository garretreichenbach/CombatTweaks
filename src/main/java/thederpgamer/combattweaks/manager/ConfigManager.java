package thederpgamer.combattweaks.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.combattweaks.CombatTweaks;

public class ConfigManager {

	public static final String[] defaultMainConfig = {
			"debug_mode: false",
			"tactical_map_view_distance: 1.2"
	};

	public static final String[] defaultSystemConfig = {
			"repair_paste_capacity_per_block: 10",
			"repair_paste_regen_per_block: 5",
			"repair_paste_power_consumed_per_block_resting: 5",
			"repair_paste_power_consumed_per_block_charging: 15",
			"armor_hp_value_multiplier: 20.0",
			"armor_hp_lost_per_damage_absorbed: 1.0",
			"base_armor_hp_bleedthrough_start: 0.75",
			"min_armor_hp_bleedthrough_start: 0.5",
			"cannon_armor_multiplier: 0.85",
			"beam_armor_multiplier: 0.35",
			"missile_armor_multiplier: 0.75"
	};
	
	//Main Config
	private static FileConfiguration mainConfig;
	//System Config
	private static FileConfiguration systemConfig;
	//Keyboard Config
	private static FileConfiguration keyboardConfig;

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
