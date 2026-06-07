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

	public final SimpleConfigBool tacticalMapSectorGrid =
			new SimpleConfigBool(this, "tactical_map_sector_grid", true, "Draw a faint grid on the tactical map marking sector boundaries.");

	public final SimpleConfigDouble tacticalMapSectorGridRange =
			new SimpleConfigDouble(this, "tactical_map_sector_grid_range", 1.0, "How many sectors out from the camera the sector grid extends in each direction (clamped 0-3). Higher shows more boundaries but adds clutter.");

	public final SimpleConfigBool tacticalMapIncomingSignatures =
			new SimpleConfigBool(this, "tactical_map_incoming_signatures", true, "Show 'Incoming Signature' contacts on the tactical map for ships approaching (or jumping in) from nearby sectors.");

	public final SimpleConfigDouble tacticalMapSignatureRange =
			new SimpleConfigDouble(this, "tactical_map_signature_range", 2.0, "How many sectors out (clamped 1-4) to detect incoming signatures. Larger = earlier warning, more server scanning.");

	public final SimpleConfigDouble tacticalMapSubsectorDivisions =
			new SimpleConfigDouble(this, "tactical_map_subsector_divisions", 8.0, "Subsector divisions per axis within each sector (clamped 1-8; 1 = off). Drives the dotted sub-grid, the A1/B2 axis labels on the camera's sector, and the subsector shown in entity labels.");

	public MainConfig(StarMod mod) {
		super(mod, "config", true);
	}
}
