# CombatTweaks

A StarMade mod that adds real-time, RTS-style tactical fleet command and a passive armor HP defense layer. Command multiple ships — and individual turrets — through a tactical map overlay, read the battlefield at a glance, and build with armor that actually soaks damage.

Full documentation is available on the [docs site](https://garretreichenbach.github.io/CombatTweaks/), and in-game through StarMade's **Guides** viewer (the mod registers its guides automatically).

## Features

### Tactical Map

Press **Backslash** (configurable) to open a real-time tactical overlay of your current sector, with faction-colored markers, a sector/subsector grid, status rings, rich labels, and animated order paths.

**Camera:**
- WASD to pan, Space/Ctrl+Space for vertical movement, Shift to sprint
- Right-click drag to rotate, scroll wheel to adjust range, X to reset
- Double-click an entity to focus and follow it (tracks across sectors until you pan)

**Selection & orders:**
- Left-click to select (drag to box-select); Shift/Ctrl to add to the selection
- Ctrl+A to select all friendly ships (or all turrets in turret mode); press again to deselect
- Middle-click a target — or empty space — to open the radial order menu
- Hold Shift while ordering to queue orders and keep the selection

**Map readouts:**
- Status rings on markers (green reactor / grey armor / amber shield), recon-gated for enemies
- Labels with name, faction, mass, speed, distance, and sector/subsector (detail level configurable)
- Sector/subsector grid with A1/B2 axis labels, plus a camera/player sector readout top-right
- Heading lines and colored order paths
- A pinnable, scrollable selection panel with HP bars and live order readouts
- A top-left settings panel of display toggles, persisted between sessions

### Turret Command

Press **Ctrl+S** to enter turret mode. The map shows your turrets — each as a single unit with combined mass (a turret's base and gun are folded together; single docked guns are handled too). Select turrets, leave turret mode to pick a target, and **Order Attack**: orders cascade to the turret's guns and engage with **no fleet setup required**.

### Fleet AI Orders

Select ships, then middle-click a target to open the radial menu. Available orders depend on the target:

| Order | Target | Behavior |
|-------|--------|----------|
| **Attack** | Enemy ships | Engage and fire on the target (neutral targets require confirmation) |
| **Defend** | Friendly ships | Escort the target and intercept nearby threats within ~2000m |
| **Move To** | Any entity | Navigate to and hold position near the target |
| **Mine** | Asteroids | Approach and extract resources from the asteroid |
| **Repair** | Friendly ships | Approach and use repair beams on the target |
| **Idle** | Own ships | Clear all standing orders, return to default AI |

Active orders are drawn as animated dotted paths: red for attack, green for defend, cyan for movement, orange for mining, magenta for repair. Moving/mining/defending ships hold their weapons so escorts don't fire on friendlies.

### Incoming Signatures

Early-warning travel lines and labels for ships approaching or jumping in from nearby sectors. Each contact shows an estimated mass and relation (friendly/neutral/hostile/unknown); co-located contacts merge into one label. Accuracy scales with proximity and your recon advantage over the contact's stealth.

### Armor HP System

Every armor block contributes to a shared Armor HP pool that absorbs damage before it reaches reactor HP, with increasing bleed-through as armor integrity drops.

- At full Armor HP, all post-armor damage is absorbed
- As Armor HP drops, more damage bleeds through to the reactor
- Total Armor HP scales sub-linearly with ship size, so larger ships benefit more per block
- **Armor HP Absorption Chambers** lower bleed-through; Chamber 1 provides a base improvement and Chamber 2 stacks further

**HUD bars** (always available): a vertical ship armor bar on the left, and a target armor bar on the target panel.

## Configuration

The mod generates its config files on first run. See the [Configuration guide](docs/features/configuration.md) for the full list of keys, including the client `config.yml` tactical-map options (grid, signatures, status rings, label detail, view distance, …) and the server-synced `system_config.yml` armor-balance values.

## Installation

1. Download the latest release JAR
2. Place it in your StarMade `mods/` directory
3. Launch StarMade — the mod loads automatically

## Building from Source

Requires JDK 21 and a local StarMade installation. Set `starmade_root` in `gradle.properties` to your StarMade directory (with trailing `/`), then:

```bash
./gradlew build
```

The JAR is output directly to your StarMade `mods/` folder. See [Building from Source](docs/development/building.md) for details.
