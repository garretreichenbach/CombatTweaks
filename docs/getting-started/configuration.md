# Configuration

CombatTweaks generates its config files on first run, located in the mod's data directory.

## config.yml (Client)

Client-side settings that are not synced over the network — each player customizes these independently. Most of the tactical-map display options can also be changed live from the in-map [settings panel](../features/tactical-map.md#settings-panel), which writes back to this file.

- `debug_mode` — default `false`. Enable debug logging.
- `tactical_map_keybind` — default `BACKSLASH`. Keyboard key name for toggling the tactical map.
- `tactical_map_view_distance` — default `4.0`. Tactical map range, in sector sizes; governs how far entities are drawn/selectable and how far the camera can pan.
- `tactical_map_sector_grid` — default `true`. Draw the grid marking sector boundaries.
- `tactical_map_sector_grid_range` — default `1.0`. How many sectors out from the camera the grid extends (clamped 0–3).
- `tactical_map_subsector_divisions` — default `4.0`. Subsector divisions per axis within each sector (clamped 1–8; 1 = off). Drives the dotted sub-grid, the A1/B2 axis labels, and the subsector shown in labels.
- `tactical_map_incoming_signatures` — default `true`. Show [Incoming Signatures](../features/incoming-signatures.md) for ships approaching from nearby sectors.
- `tactical_map_signature_range` — default `2.0`. How many sectors out (clamped 1–4) to detect incoming signatures. Larger = earlier warning, more server scanning.
- `tactical_map_show_hp_bars` — default `true`. Show status rings on markers and HP bars in the selection panel.
- `tactical_map_show_heading` — default `true`. Show velocity/heading lines for entities.
- `tactical_map_labels_all` — default `true`. Show labels for all entities (`true`) or only own/allied ones (`false`).
- `tactical_map_label_detail` — default `2.0`. Label detail: `0` minimal (name+faction), `1` normal (+distance/order), `2` full (+mass/speed/sector).
- `tactical_map_pinned` — default empty. Pinned selection-panel entities (managed via the map's Pin buttons — not normally hand-edited).

## system_config.yml (Server-synced)

Gameplay balance values synced from server to clients. On a dedicated server, only the server's copy is used — clients receive the values automatically on login.

- `armor_hp_value_multiplier` — default `20.0`. Base multiplier applied to raw armor value totals.
- `armor_hp_scaling_exponent` — default `0.75`. Sub-linear scaling exponent (1.0 = linear, lower = more diminishing returns for small ships).
- `armor_hp_lost_per_damage_absorbed` — default `1.0`. Armor HP consumed per point of damage absorbed.
- `base_armor_hp_bleed_through_start` — default `0.7`. Controls bleed-through ramp rate (higher = bleed reaches 100% sooner).
- `armor_hp_absorption_effect_1_sub` — default `-7.5`. Chamber 1 bleed-through reduction.
- `armor_hp_absorption_effect_2_sub` — default `-7.5`. Chamber 2 bleed-through reduction.

> **Note:** Changes to `system_config.yml` on the server are broadcast to all connected clients automatically. You do not need to restart the server for changes to take effect.
