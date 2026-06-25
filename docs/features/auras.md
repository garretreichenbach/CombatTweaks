# Auras

Auras are spherical fields projected by a reactor chamber that affect every qualifying ship inside them. They are drawn on the [Tactical Map](tactical-map.md) as bounding spheres so both sides can see them, and they can be shut down — by sustained fire on the projecting ship or with a dedicated [Aura Disruptor](aura-disruptor.md) weapon.

There are two roles, and a ship can run only **one** of them (the base chambers are mutually exclusive):

- **Support Aura** — projected from the reactor **support** tree; buffs allied ships inside the sphere.
- **Offense Aura** — projected from the reactor **offense** tree; an electronic-warfare field that jams enemy ships inside the sphere.

## Building a projector

Install the base chamber for the role you want, then add its sub-chambers:

**Support Aura** (support tree)

- **Support Aura** — the base chamber. Required; sets the aura's base range.
- **Shield Aura Capacity 1 / 2** — increase the shield capacity of allies inside the sphere.
- **Support Aura Range Boost 1 / 2** — enlarge the sphere.

**Offense Aura** (offense tree)

- **Offense Aura** — the base chamber. Required; sets the aura's base range.
- **Targeting Jammer 1 / 2** — degrade the AI targeting accuracy of enemies inside the sphere (see [ECW](#electronic-warfare-ecw)).
- **Offense Aura Range Boost 1 / 2** — enlarge the sphere.

Once built, the aura is a player-activatable ability like other reactor chamber effects — activate it to project the field, deactivate to drop it.

## Range

An aura's radius comes from an **Aura Range** value carried on the reactor: the base chamber grants a starting range and the Aura Range Boost chambers add to it. The value is expressed as a fraction of sector size (`aura_base_chamber_range_set`, default `0.2`), so larger sectors give physically larger spheres.

## Who an aura affects

- A **Support Aura** affects **allied** ships in range; an **Offense Aura** affects **enemy** ships in range.
- A projector does not affect its own ship by default. Set `aura_affects_root` to `true` to include the projector's own rail structure.
- A ship that is much smaller than its target cannot affect it: the projector's reactor level must be at least `aura_min_size_percent` (default `0.15`) of the target's. This stops a tiny ship from auraing a capital.

### No stacking

A ship can be under **at most one Support aura and one Offense aura at a time**. If two friendly Support projectors overlap an ally, only the first to reach it applies — the buff never stacks. The same holds for Offense jammers on an enemy. (A ship can still sit inside one friendly Support sphere *and* one enemy Offense sphere simultaneously — those are different roles.)

## Electronic warfare (ECW)

The Offense Aura is deliberately **not** a flat stat debuff. Its Targeting Jammer chambers reduce the AI shooting precision of affected enemies (`targeting_jammer_aura_accuracy_mult_1` / `_2`, defaults `0.6` / `0.4`), widening their shot spread so AI-controlled turrets, drones, and point-defense miss more often.

Because it only touches **AI-controlled** fire, a manually-piloted ship is immune — jamming shreds NPC fleets and turret screens but rewards a skilled pilot, so it never becomes a mandatory must-counter debuff.

## Shutting an aura down

Every active projector maintains an **aura power** pool. When it runs out, the aura drops until it regenerates (`aura_regen_percent_per_update`, default `5%` per update). There are two ways to drain it:

- **Attrition** — damaging the projecting ship bleeds aura power in proportion to the hit (`aura_damage_attrition_factor`, default `0.05`). Focus fire on the projector and its aura collapses.
- **Aura Disruptor** — a dedicated support beam that drains aura power directly without having to break the hull. See the [Aura Disruptor](aura-disruptor.md) page.

## On the tactical map

Active auras render as translucent bounding spheres around their projector, so you can spot an enemy projector and prioritize it. Support auras are colored by faction relation (friendly/neutral/hostile); Offense auras read as a distinct orange "danger" sphere regardless of who casts them. The sphere fades as the aura's power is drained, giving a visual read on how close it is to collapsing. Toggle them with `tactical_map_show_auras`.

## HUD indicator

While you are piloting a ship that sits inside an aura field, an icon appears at the top of your HUD: a green hexagon when you are inside a friendly **support** aura, and an orange marker when you are inside a hostile **offense (ECW)** aura. Both can show at once. This is a quick "you are buffed / being jammed" read without opening the tactical map.

## Configuration

All aura balance values live in `system_config.yml` (server-synced); the display toggle and `aura_affects_root` are in `config.yml`. See the [Configuration](configuration.md) page for the full list.
