package videogoose.combattweaks.config;

import api.mod.StarMod;
import api.utils.simpleconfig.SimpleConfigBool;
import api.utils.simpleconfig.SimpleConfigContainer;
import api.utils.simpleconfig.SimpleConfigDouble;
import api.utils.simpleconfig.SimpleConfigString;

public class MainConfig extends SimpleConfigContainer {

	public final SimpleConfigBool debugMode =
			new SimpleConfigBool(this, "debug_mode", false, "Enable debug logging");

	public final SimpleConfigDouble tacticalMapViewDistance =
			new SimpleConfigDouble(this, "tactical_map_view_distance", 4.0, "Tactical map range, in sector sizes. Governs how far entities are drawn/selectable AND how far the camera can pan, so any visible entity is always reachable.");

	public final SimpleConfigString tacticalMapKeybind =
			new SimpleConfigString(this, "tactical_map_keybind", "BACKSLASH", "Keyboard key name for toggling the tactical map");

	public MainConfig(StarMod mod) {
		super(mod, "config", true);
	}
}
