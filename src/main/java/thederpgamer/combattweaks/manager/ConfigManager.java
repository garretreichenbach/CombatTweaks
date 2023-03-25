package thederpgamer.combattweaks.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.combattweaks.CombatTweaks;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ConfigManager {

	//Main Config
	private static FileConfiguration mainConfig;
	public static final String[] defaultMainConfig = {
		"debug-mode: false",
		"max-world-logs: 5"
	};

	//System Config
	private static FileConfiguration systemConfig;
	public static final String[] defaultSystemConfig = {
		"repair-paste-capacity-per-block: 10",
		"repair-paste-regen-per-block: 5",
		"repair-paste-power-consumed-per-block-resting: 5",
		"repair-paste-power-consumed-per-block-charging: 15",
		"armor-value-multiplier: 50.0"
	};

	public static void initialize(CombatTweaks instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);

		systemConfig = instance.getConfig("system-config");
		systemConfig.saveDefault(defaultSystemConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static FileConfiguration getSystemConfig() {
		return systemConfig;
	}
}
