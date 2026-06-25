# Warhead Pre-Charger

The Warhead Pre-Charger is a reactor chamber (offense tree) that supercharges every warhead on the ship — increasing blast radius, damage, and the chance a warhead detonates on hit. It comes in two stacking tiers.

## Tiers

- **Warhead Pre-Charger 1**
    - Blast radius `+5` (`warhead_pre_charger_effect_1_radius_add`)
    - Damage `×5` (`warhead_pre_charger_effect_1_damage_multiplier`)
    - Volatility `+0.3` — added chance to explode on hit (`warhead_pre_charger_effect_1_volatility_add`)
- **Warhead Pre-Charger 2**
    - Blast radius `+7.5` (`warhead_pre_charger_effect_2_radius_add`)
    - Damage `×7.5` (`warhead_pre_charger_effect_2_damage_multiplier`)
    - Volatility `+0.5` (`warhead_pre_charger_effect_2_volatility_add`)

## Notes

- The effect applies to the warhead blocks on the ship carrying the chamber.
- Higher volatility means warheads are more likely to detonate when struck — strong on offense, but a liability if your warhead bank takes fire.

## Configuration

All values are tunable in `system_config.yml` (server-synced). See the [Configuration](configuration.md) page.
