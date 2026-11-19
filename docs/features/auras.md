# Auras

Auras are spherical fields projected by a reactor chamber that affect every qualifying ship inside them. They are drawn on the [Tactical Map](tactical-map.md) as bounding spheres so both sides can see them, and they can be shut down — by sustained fire on the projecting ship or with a dedicated [Aura Disruptor](aura-disruptor.md) weapon.

There are two roles, and a ship can run only **one** of them (the base chambers are mutually exclusive):

- **Support Aura** — projected from the reactor **support** tree; buffs allied ships inside the sphere.
- **Offense Aura** — projected from the reactor **offense** tree; an electronic-warfare field that jams enemy ships inside the sphere.

## Building a projector

Aura chambers hang off two placeable general chambers this mod adds: the **Reactor Support Chamber** and the **Reactor Offense Chamber**. Like vanilla chambers, each one needs a **Reactor Conduit** path back to the reactor; you then pick what it becomes in the **Reactor Menu** (`Insert`). A chamber whose conduit runs from another chamber, instead of from the reactor, is a child of that chamber and can only be specified from its branch of the tree.

**Support Aura** (Reactor Support Chamber tree)

- **Support Aura** — the base chamber. Required; projects the field and sets its base range.
    - **Shield Aura Capacity 1 → 2** — a child of Support Aura: raises the shield capacity of allies inside the sphere. Tier 2 is an upgrade of tier 1.
- **Support Aura Range Boost 1 → 2** — a separate branch off the Reactor Support Chamber: enlarges the sphere.

```scene
{ "height": 300,
  "blocks": [ { "from": [0,0,0], "to": [2,2,2], "block": "REACTOR_POWER" } ],
  "steps": [
    { "text": "Start from your reactor.", "blocks": [] },
    { "text": "Run a Reactor Conduit out of the reactor to a Reactor Support Chamber, then specify it as Support Aura in the reactor menu. This is the projector.",
      "blocks": [ { "from": [1,3,1], "to": [1,4,1], "block": "REACTOR_CONDUIT", "orient": 2 },
                  { "from": [0,5,0], "to": [2,5,2], "block": "videogoose.combattweaks~Reactor Support Chamber" } ] },
    { "text": "A conduit from the Support Aura chamber to a second Reactor Support Chamber makes it a child of the aura: specify it as Shield Aura Capacity 1, and upgrade it to 2 later.",
      "blocks": [ { "from": [1,6,1], "to": [1,7,1], "block": "REACTOR_CONDUIT", "orient": 2 },
                  { "from": [0,8,0], "to": [2,8,2], "block": "videogoose.combattweaks~Reactor Support Chamber" } ] },
    { "text": "Range Boost branches off the Reactor Support Chamber itself, so give it its own conduit line from the reactor.",
      "blocks": [ { "from": [3,1,1], "to": [4,1,1], "block": "REACTOR_CONDUIT", "orient": 4 },
                  { "from": [5,0,0], "to": [5,2,2], "block": "videogoose.combattweaks~Reactor Support Chamber" } ] } ] }
```

**Offense Aura** (Reactor Offense Chamber tree)

- **Offense Aura** — the base chamber. Required; projects the field and sets its base range.
    - **Targeting Jammer Aura 1 → 2** — children of Offense Aura: degrade the AI targeting accuracy of enemies inside the sphere (see [ECW](#electronic-warfare-ecw)).
- **Offense Aura Range Boost 1 → 2** — a separate branch off the Reactor Offense Chamber: enlarges the sphere.

```scene
{ "height": 300,
  "blocks": [ { "from": [0,0,0], "to": [2,2,2], "block": "REACTOR_POWER" } ],
  "steps": [
    { "text": "The Offense Aura is built the same way, from Reactor Offense Chambers. Conduit to one and specify it as Offense Aura.",
      "blocks": [ { "from": [1,3,1], "to": [1,4,1], "block": "REACTOR_CONDUIT", "orient": 2 },
                  { "from": [0,5,0], "to": [2,5,2], "block": "videogoose.combattweaks~Reactor Offense Chamber" } ] },
    { "text": "Chain a child chamber off the aura and specify it as Targeting Jammer Aura 1 (upgrade to 2 later).",
      "blocks": [ { "from": [1,6,1], "to": [1,7,1], "block": "REACTOR_CONDUIT", "orient": 2 },
                  { "from": [0,8,0], "to": [2,8,2], "block": "videogoose.combattweaks~Reactor Offense Chamber" } ] },
    { "text": "Offense Aura Range Boost gets its own line from the reactor, like the support version.",
      "blocks": [ { "from": [3,1,1], "to": [4,1,1], "block": "REACTOR_CONDUIT", "orient": 4 },
                  { "from": [5,0,0], "to": [5,2,2], "block": "videogoose.combattweaks~Reactor Offense Chamber" } ] } ] }
```

Support Aura and Offense Aura are **mutually exclusive**: a ship can project one or the other, never both.

Once built, the aura is a player-activatable ability like other reactor chamber effects — activate it to project the field, deactivate to drop it. AI-controlled ships turn theirs on by themselves when something in range qualifies.

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

Every projector maintains an **aura power** pool equal to its aura chamber's HP. When it runs out, the aura **collapses**: its effects drop immediately and it can't be re-activated until the pool has regenerated to full (`aura_regen_percent_per_update`, default `5%` of max per update, whether or not the aura is up). Reactor changes don't refill the pool. There are two ways to drain it:

- **Attrition** — damaging the projecting ship bleeds aura power in proportion to the hit (`aura_damage_attrition_factor`, default `0.05`). Focus fire on the projector and its aura collapses.
- **Aura Disruptor** — a dedicated support beam that drains aura power directly without having to break the hull. See the [Aura Disruptor](aura-disruptor.md) page.

## On the tactical map

Active auras render as translucent bounding spheres around their projector, so you can spot an enemy projector and prioritize it. Support auras are colored by faction relation (friendly/neutral/hostile); Offense auras read as a distinct orange "danger" sphere regardless of who casts them. The sphere fades as the aura's power is drained, giving a visual read on how close it is to collapsing. Each aura also emits a pulse — a shell expanding from the projector to the sphere's edge every couple of seconds — both on the map and in the normal flight view, so you can see an aura you're flying into. Toggle the map spheres with `tactical_map_show_auras` and the in-world pulse with `aura_show_in_world`.

## HUD indicator

While you are piloting a ship that sits inside an aura field, an icon appears at the top of your HUD: a green hexagon when you are inside a friendly **support** aura, and an orange marker when you are inside a hostile **offense (ECW)** aura. Both can show at once. This is a quick "you are buffed / being jammed" read without opening the tactical map.

## Configuration

All aura balance values live in `system_config.yml` (server-synced); the display toggle and `aura_affects_root` are in `config.yml`. See the [Configuration](configuration.md) page for the full list.
