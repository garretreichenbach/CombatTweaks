# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**CombatTweaks** is a mod for the game StarMade that adds combat-related features and gameplay improvements. It's built as a Java mod using Gradle and integrates with the StarMade API using SpongePowered Mixins.

- **Game**: StarMade (https://starmadedock.net/)
- **Mod Version**: 2.1.0 (see `gradle.properties`)
- **Author**: VideoGoose (TheDerpGamer)

## Build & Development Commands

### Build
```bash
./gradlew build
```
Compiles the mod and packages it as a JAR. Output goes to the StarMade mods directory (configured in `gradle.properties` as `starmade_root`).

### Clean Build
```bash
./gradlew clean build
```

### Update Version
```bash
./gradlew updateVersion
```
Automatically syncs the version in `src/main/resources/mod.json` with the version in `gradle.properties`.

### Build & Package JAR
```bash
./gradlew jar
```
Creates the JAR file with embedded dependencies and places it in the StarMade mods directory.

### Configuration
Edit `gradle.properties` to configure:
- `starmade_root`: Path to your StarMade installation (required for builds)
- `mod_version`: Version number (also updates in mod.json)
- `mod_name`: Display name
- `mod_org_id`: Reverse domain (e.g., `thederpgamer.combattweaks`)

## Architecture

### Directory Structure
```
src/main/java/videogoose/combattweaks/
├── CombatTweaks.java                 # Main mod entry point
├── effect/                           # Game effects (armor effects, etc.)
│   ├── ConfigEffectGroup.java
│   ├── ConfigGroupRegistry.java
│   └── defense/armor/                # Armor-specific effects
├── element/block/                    # Block definitions and chambers
│   ├── Block.java
│   ├── BlockRegistry.java            # Block registration
│   ├── ElementInterface.java
│   └── chamber/                      # Chamber implementations
├── gui/                              # UI and display
│   ├── elements/                     # GUI element customization
│   ├── hud/                          # Heads-up display (ship armor HP bar)
│   └── tacticalmap/                  # Tactical map features
├── listener/                         # Event listeners for game events
├── manager/                          # Core managers
│   ├── ConfigManager.java            # Configuration handling
│   ├── EventManager.java             # Event registration & handling
│   └── ResourceManager.java          # Resource loading
├── mixins/                           # SpongePowered Mixins for base game modifications
├── network/                          # Network packet handling
│   └── client/                       # Client-side packets
├── system/armor/                     # Armor system implementation
└── utils/                            # Utility classes

src/main/resources/
├── mod.json                          # Mod metadata (version, author, dependencies)
├── combattweaks.mixins.json          # Mixin configuration
├── shaders/                          # GLSL shader files
└── sprites/                          # Sprite/texture assets
```

### Key Classes & Patterns

**CombatTweaks.java** (line 18)
- Main mod class extending `StarMod`
- Entry points: `onEnable()`, `onClientCreated()`, `onResourceLoad()`, `onBlockConfigLoad()`
- Initializes managers (Config, Event, Resource) and registers network packets

**Manager Classes** (manager/)
- **ConfigManager**: Loads and manages mod configuration
- **EventManager**: Registers and dispatches event listeners
- **ResourceManager**: Loads custom resources (shaders, sprites, etc.)

**Block Registry** (element/block/)
- `BlockRegistry.registerBlocks()` registers custom blocks during `onBlockConfigLoad()`
- Blocks can be chambers (e.g., `ArmorHPAbsorptionChamber1/2`) with special effects

**Mixins** (mixins/)
- Uses SpongePowered Mixins to inject code into base game classes
- Configuration in `combattweaks.mixins.json` (currently only `KeyboardMappingsMixin`)
- Minimal mixin footprint; mostly extends base API

**Network Packets** (network/client/)
- `SendAttackPacket`, `SendDefensePacket`, `SendIdlePacket`
- Registered in `CombatTweaks.onEnable()`
- Sync mod state between server and client

### Adding New Features

**Adding a new effect** (effect/)
1. Create class extending appropriate effect base class
2. Register in `ConfigGroupRegistry`
3. Implemented by corresponding block chambers

**Adding a new block/chamber** (element/block/)
1. Create `Block` subclass with metadata
2. Create `ChamberBlock` subclass with behavior
3. Register in `BlockRegistry.registerBlocks()`

**Adding a GUI element** (gui/)
- HUD elements: extend appropriate HUD class and register with event system
- Tactical map: add to `TacticalMapGUIDrawer` or create new drawer

**Adding event listeners** (listener/)
- Create listener class, register in `EventManager.initialize()`

## Mod Metadata

**mod.json** (src/main/resources/)
- Version is auto-updated by `updateVersion` task
- Mixin configuration points to `combattweaks.mixins.json`
- Both client-side and server-side mod

**combattweaks.mixins.json**
- Mixin version requirement: 0.8.7+
- Java 8 compatibility level
- Currently contains `KeyboardMappingsMixin`
- Add new mixins to `mixins` array and update `client`/`server` arrays as needed

## Dependencies

- **StarMade.jar**: Game engine (compileOnly, from `starmade_root`)
- **StarMade lib/*.jar**: Game libraries
- **SpongePowered Mixins 0.8.5**: For code injection (compileOnly + annotationProcessor)
- **Local lib/*.jar**: Custom/bundled dependencies

## Gradle Build Notes

- Gradle memory: `-Xmx4G` (see `gradle.properties`)
- Duplicate strategy: EXCLUDE (prevents conflicts with nested JARs)
- JAR output: Named as `CombatTweaksv{version}.jar` in StarMade mods folder
- Manifest: Sets Main-Class for mod launcher
- Runtime classpath: Automatically embeds non-StarMade dependencies

## StarMade Source API Reference

The StarMade source code is available locally and via documentation for API lookups and development reference.

**Local source path** (in `gradle.properties`): `/home/garret/Documents/Projects/StarMade-Release/src/main/java/`

**Online documentation**: https://starmade-community.github.io/StarMade-Open/index.html
- WIP but actively maintained
- Useful for browsing API structure and class hierarchy
- Cross-reference with local source for detailed implementation

**To look up a StarMade API**:
- Ask Claude Code to look up a class, method, or interface
- Example: "What does the `StarMod` class provide?" or "Show me the `BlockConfig` API"
- Claude will search the local source and provide relevant code samples
- Use the online docs to browse the overall API structure

**Source structure** (typical StarMade-Open layout):
- `src/` - Main source code
- API classes referenced in imports (e.g., `api.mod.StarMod`, `api.listener.events.*)
- Common references: `api.listener.events.*` (event system), `api.config.*` (configuration), `api.network.*` (networking)

## Common Issues & Solutions

**StarMade path not found**
- Ensure `gradle.properties` has correct `starmade_root` with trailing `/`
- Can override: `./gradlew build -Pstarmade_root=my/path/`

**Compilation errors referencing game classes**
- Verify StarMade.jar is in the configured `starmade_root`
- Run `./gradlew clean` and rebuild
- If referencing classes from StarMade source, verify `starmade_source` path is configured

**Mixin injection failures**
- Check `combattweaks.mixins.json` syntax
- Verify target class names match actual game classes
- Ensure mixin version requirement is met
- Cross-reference class names with StarMade source