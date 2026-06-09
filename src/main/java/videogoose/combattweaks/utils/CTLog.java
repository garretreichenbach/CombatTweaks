package videogoose.combattweaks.utils;

import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin wrapper over StarMod's per-mod file logger ({@code logInfo}/{@code logWarning}/{@code logException}),
 * which writes to {@code StarMade/moddata/CombatTweaks/logs/combat_tweaks_log.0.log} rather than into
 * StarMade's already very noisy main log. {@link #debug} output is gated behind the {@code debug_mode}
 * config flag so verbose tracing can be left in the code and switched on only when diagnosing an issue.
 */
public final class CTLog {

	private CTLog() {
	}

	/** Whether {@code debug_mode} is enabled in config (false if config isn't loaded yet). */
	public static boolean debugEnabled() {
		try {
			return ConfigManager.getMainConfig() != null && ConfigManager.getMainConfig().debugMode.getValue();
		} catch(Exception ignored) {
			return false;
		}
	}

	/** Debug-level trace — only written when {@code debug_mode} is on. */
	public static void debug(String message) {
		if(debugEnabled()) {
			write(message);
		}
	}

	private static final Map<String, Long> lastThrottled = new ConcurrentHashMap<>();

	/**
	 * Debug trace for hot paths: writes at most once per {@code minIntervalMs} per {@code key}, so a message
	 * emitted every AI tick doesn't flood the log. No-op unless {@code debug_mode} is on. Note: uses
	 * {@code System.currentTimeMillis()}, which is fine for log throttling.
	 */
	public static void debugThrottled(String key, long minIntervalMs, String message) {
		if(!debugEnabled()) {
			return;
		}
		long now = System.currentTimeMillis();
		Long last = lastThrottled.get(key);
		if(last == null || now - last >= minIntervalMs) {
			lastThrottled.put(key, now);
			write(message);
		}
	}

	/** Always written to the mod log. */
	public static void info(String message) {
		write(message);
	}

	public static void warn(String message) {
		CombatTweaks mod = CombatTweaks.getInstance();
		if(mod != null) {
			mod.logWarning(message);
		}
	}

	public static void error(String message, Exception exception) {
		CombatTweaks mod = CombatTweaks.getInstance();
		if(mod != null) {
			mod.logException(message, exception);
		}
	}

	private static void write(String message) {
		CombatTweaks mod = CombatTweaks.getInstance();
		if(mod != null) {
			mod.logInfo(message);
		}
	}
}
