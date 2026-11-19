# Warhead Pre-Charger

The Warhead Pre-Charger is a reactor chamber (offense tree) that supercharges every warhead on the ship — increasing blast radius, damage, and the chance a warhead detonates on hit. It comes in two stacking tiers.

## Building it

The Pre-Charger is specified on a **Reactor Offense Chamber** connected to the reactor by **Reactor Conduit**. Upgrade tier 1 to tier 2 in the Reactor Menu (`Insert`).

```scene
{ "height": 280,
  "blocks": [ { "from": [0,0,0], "to": [2,2,2], "block": "REACTOR_POWER" } ],
  "steps": [
    { "text": "Run a Reactor Conduit from the reactor to a Reactor Offense Chamber, then specify it as Warhead Pre-Charger Chamber 1.",
      "blocks": [ { "from": [1,3,1], "to": [1,4,1], "block": "REACTOR_CONDUIT", "orient": 2 },
                  { "from": [0,5,0], "to": [2,5,2], "block": "videogoose.combattweaks~Reactor Offense Chamber" } ] },
    { "text": "Every warhead on the ship is now charged. They don't need to touch the chamber or the reactor.",
      "blocks": [ { "from": [6,0,0], "to": [8,0,2], "block": "WARHEAD" } ] } ] }
```

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
