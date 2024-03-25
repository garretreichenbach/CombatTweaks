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
		"tactical-map-view-distance: 1.2"
	};

	//System Config
	private static FileConfiguration systemConfig;
	public static final String[] defaultSystemConfig = {
		"repair-paste-capacity-per-block: 10",
		"repair-paste-regen-per-block: 5",
		"repair-paste-power-consumed-per-block-resting: 5",
		"repair-paste-power-consumed-per-block-charging: 15",
		"armor-value-multiplier: 20.0",
		"cannon-armor-multiplier: 0.9",
		"beam-armor-multiplier: 0.35",
		"missile-armor-multiplier: 0.75"
	};

	//Keyboard Config
	private static FileConfiguration keyboardConfig;
	public static final String[] defaultKeyboardConfig = {
		"tactical-map-key: KEY_COMMA"
	};

	public static void initialize(CombatTweaks instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);

		systemConfig = instance.getConfig("system-config");
		systemConfig.saveDefault(defaultSystemConfig);

		keyboardConfig = instance.getConfig("keyboard-config");
		keyboardConfig.saveDefault(defaultKeyboardConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static FileConfiguration getSystemConfig() {
		return systemConfig;
	}

	public static FileConfiguration getKeyboardConfig() {
		return keyboardConfig;
	}
}
