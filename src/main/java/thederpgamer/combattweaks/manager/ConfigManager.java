package thederpgamer.combattweaks.manager;

import api.mod.config.FileConfiguration;
import thederpgamer.combattweaks.CombatTweaks;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ConfigManager {

	// Main Config
	private static FileConfiguration mainConfig;
	public static final String[] defaultMainConfig = {
			"debug-mode: false",
			"max-world-logs: 5"
	};

	public static void initialize(CombatTweaks instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}
}
