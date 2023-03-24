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
		"repair-paste-power-consumed-per-block-charging: 15"
	};

	//Hud Config
	private static FileConfiguration hudConfig;
	public static final String[] defaultHudConfig = {
		"target-ship-armor-hp-bar-color: #687282",
		"target-ship-armor-hp-bar-offset: 0, 0",
		"target-ship-armor-hp-bar-flipped-x: false",
		"target-ship-armor-hp-bar-flipped-y: false",
		"target-ship-armor-hp-bar-text-on-top: false",
		"target-ship-armor-hp-bar-text-pos: 0, 0",
		"target-ship-armor-hp-bar-text-desc-pos: 0, 0",

		"ship-armor-hp-bar-color: #687282",
		"ship-armor-hp-bar-offset: 0, 0",
		"ship-armor-hp-bar-flipped-x: false",
		"ship-armor-hp-bar-flipped-y: false",
		"ship-armor-hp-bar-text-on-top: false",
		"ship-armor-hp-bar-text-pos: 0, 0",
		"ship-armor-hp-bar-text-desc-pos: 0, 0"
	};

	public static void initialize(CombatTweaks instance) {
		mainConfig = instance.getConfig("config");
		mainConfig.saveDefault(defaultMainConfig);

		systemConfig = instance.getConfig("system-config");
		systemConfig.saveDefault(defaultSystemConfig);

		hudConfig = instance.getConfig("hud-config");
		hudConfig.saveDefault(defaultHudConfig);
	}

	public static FileConfiguration getMainConfig() {
		return mainConfig;
	}

	public static FileConfiguration getSystemConfig() {
		return systemConfig;
	}

	public static FileConfiguration getHudConfig() {
		return hudConfig;
	}
}
