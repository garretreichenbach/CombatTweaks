# Configuration

CombatTweaks generates two config files on first run. Both are located in the mod's data directory.

## config.yml (Client)

Client-side settings that are not synced over the network. Each player can customize these independently.

| Key | Default | Description |
|-----|---------|-------------|
| `debug_mode` | `false` | Enable debug logging |
| `tactical_map_view_distance` | `1.2` | Tactical map visibility range (in sector radii) |
| `tactical_map_keybind` | `BACKSLASH` | Keyboard key name for toggling the tactical map |

## system_config.yml (Server-synced)

Gameplay balance values that are synced from server to clients. On a dedicated server, only the server's copy of this file is used — clients receive the values automatically on login.

| Key | Default | Description |
|-----|---------|-------------|
| `armor_hp_value_multiplier` | `20.0` | Base multiplier applied to raw armor value totals |
| `armor_hp_scaling_exponent` | `0.75` | Sub-linear scaling exponent (1.0 = linear, lower = more diminishing returns for small ships) |
| `armor_hp_lost_per_damage_absorbed` | `1.0` | Armor HP consumed per point of damage absorbed |
| `base_armor_hp_bleed_through_start` | `0.7` | Controls bleed-through ramp rate (higher = bleed reaches 100% sooner) |
| `armor_hp_absorption_effect_1_sub` | `-7.5` | Chamber 1 bleed-through reduction |
| `armor_hp_absorption_effect_2_sub` | `-7.5` | Chamber 2 bleed-through reduction |

!!! note
    Changes to `system_config.yml` on the server are broadcast to all connected clients automatically. You do not need to restart the server for changes to take effect.
