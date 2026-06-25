# Armor HP System

Every armor block on your ship contributes to a shared Armor HP pool. Incoming damage is absorbed by this pool before reaching reactor HP, with increasing bleed-through as armor integrity decreases.

## How It Works

- At full Armor HP, all post-armor damage is absorbed
- As Armor HP drops, an increasing fraction of damage bleeds through to the reactor
- Bleed-through scales linearly with HP lost, reaching full pass-through at low HP
- Total Armor HP scales sub-linearly with ship size, so larger ships benefit more per block than smaller ones

The sub-linear scaling is controlled by the `armor_hp_scaling_exponent` config value (default `0.75`). At `1.0`, scaling is linear. Lower values give diminishing returns for smaller ships while rewarding larger builds.

## Bleed-Through

The bleed-through mechanic ensures that armor is most effective when intact and progressively less effective as it takes damage. The `base_armor_hp_bleed_through_start` config value (default `0.7`) controls how aggressively bleed-through ramps up — higher values mean bleed-through reaches 100% sooner as armor is lost.

## Absorption Chambers

**Armor HP Absorption Chambers** can be installed to improve armor effectiveness by lowering the bleed-through rate:

- **Chamber 1** — base bleed-through reduction (config `armor_hp_absorption_effect_1_sub`, default `-7.5`).
- **Chamber 2** — stacking bleed-through reduction (config `armor_hp_absorption_effect_2_sub`, default `-7.5`).

Both chambers stack additively, providing a significant improvement to armor durability when combined.

## Configuration

All armor HP values can be tuned in `system_config.yml`. See the [Configuration](configuration.md) page for the full list of options.
