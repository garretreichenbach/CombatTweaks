# CombatTweaks

A StarMade mod that adds real-time tactical fleet management and an armor HP defense system. Command multiple ships through an RTS-style tactical map overlay, issue fleet orders, and benefit from a passive armor durability layer that rewards building with armor blocks.

## Features

### Tactical Map

Press **Backslash** to open a real-time tactical overlay of your current sector. The map shows all ships, stations, and asteroids with faction-colored indicators, distance readouts, and active order paths.

**Camera Controls:**
- WASD to pan, Space/Ctrl+Space for vertical movement, Shift to sprint
- Right-click drag to rotate, scroll wheel to zoom
- X to reset camera, double-click an entity to focus on it

**Selection:**
- Left-click to select an entity, Shift+click to multi-select
- Click and drag to box-select
- Ctrl+A to select all friendly ships
- Ctrl+S to toggle turret selection mode

### Fleet AI Orders

Select one or more ships, then click a target to open the radial command menu. Available orders depend on the target type:

| Order | Target | Behavior |
|-------|--------|----------|
| **Attack** | Enemy ships | Engage and fire on the target |
| **Defend** | Friendly ships | Escort the target and intercept nearby threats within 2000m |
| **Move To** | Any entity | Navigate to and hold position near the target |
| **Mine** | Asteroids | Approach and extract resources from the asteroid |
| **Repair** | Friendly ships | Approach and use repair beams on the target |
| **Idle** | Own ships | Clear all standing orders, return to default AI |

Active orders are displayed as animated dotted paths on the tactical map: red for attack, green for defend, cyan for movement, orange for mining, and magenta for repair.

### Armor HP System

Every armor block on your ship contributes to a shared Armor HP pool. Incoming damage is absorbed by this pool before reaching reactor HP, with increasing bleed-through as armor integrity decreases.

- At full Armor HP, all post-armor damage is absorbed
- As Armor HP drops, an increasing fraction of damage bleeds through to the reactor
- Bleed-through scales linearly with HP lost, reaching full pass-through at low HP
- Total Armor HP scales sub-linearly with ship size, so larger ships benefit more per block than smaller ones

**Armor HP Absorption Chambers** can be installed to improve armor effectiveness by lowering the bleed-through rate. Chamber 1 provides a base improvement, and Chamber 2 stacks further reduction.

### HUD Elements

- **Ship Armor HP Bar** — Vertical bar on the left side of the screen showing your current armor integrity percentage
- **Target Armor HP Bar** — Horizontal bar on the target panel showing the selected target's armor integrity

## Configuration

The mod generates two config files on first run.

**config.yml:**

| Key | Default | Description |
|-----|---------|-------------|
| `debug_mode` | `false` | Enable debug logging |
| `tactical_map_view_distance` | `1.2` | Tactical map visibility range (in sector radii) |

**system_config.yml:**

| Key | Default | Description |
|-----|---------|-------------|
| `armor_hp_value_multiplier` | `20.0` | Base multiplier applied to raw armor value totals |
| `armor_hp_scaling_exponent` | `0.75` | Sub-linear scaling exponent (1.0 = linear, lower = more diminishing returns for small ships) |
| `armor_hp_lost_per_damage_absorbed` | `1.0` | Armor HP consumed per point of damage absorbed |
| `base_armor_hp_bleed_through_start` | `0.7` | Controls bleed-through ramp rate (higher = bleed reaches 100% sooner) |
| `armor_hp_absorption_effect_1_sub` | `-7.5` | Chamber 1 bleed-through reduction |
| `armor_hp_absorption_effect_2_sub` | `-7.5` | Chamber 2 bleed-through reduction |

## Installation

1. Download the latest release JAR
2. Place it in your StarMade `mods/` directory
3. Launch StarMade — the mod loads automatically

## Building from Source

Requires a local StarMade installation. Set `starmade_root` in `gradle.properties` to your StarMade directory (with trailing `/`).

```bash
./gradlew build
```

The JAR is output directly to your StarMade `mods/` folder.

