# Fleet AI Orders

Select one or more ships on the [Tactical Map](tactical-map.md), then open the radial command menu on a target. Available orders depend on the target's type and relation.

## Orders

- **Attack** (enemy ships) — engage and fire on the target. Attacking a neutral target requires a confirmation step.
- **Defend** (friendly ships) — escort the target and intercept nearby threats within ~2,000m.
- **Move To** (any entity) — navigate to and hold position near the target.
- **Mine** (asteroids) — approach and extract resources from the asteroid.
- **Repair** (friendly ships) — approach and use repair beams on the target.
- **Idle** (own ships) — clear all standing orders and return to default AI.

## Issuing Orders

1. Select one or more friendly ships — click, box-select, or **Ctrl+A**.
2. **Middle-click** a target entity to open the radial menu (the available options depend on whether the target is hostile, neutral, allied, or minable).
3. Choose an order.

To issue an order to a group without a specific target — for example **Idle** to cancel standing orders — select your ships and **middle-click empty space** to open the radial on the selection itself.

### Queueing Orders

Hold **Shift** while choosing an order to **queue** it instead of replacing the current one, and to keep your selection so you can chain several orders in a row. Without Shift, issuing an order replaces standing orders and clears the selection.

## Weapon Discipline

Ships only fire when their order calls for it. A ship that is **moving**, **mining**, or **defending in transit** holds its weapons (and its turrets hold theirs) rather than firing on movement targets or friendlies — so escorts won't accidentally shoot the ship they're protecting. Only attacking (and actively defending) ships open fire.

## Reading Orders

Each ship's current activity is shown in its tactical-map label and in the [selection panel](tactical-map.md#selection-panel): **Moving**, **Attacking**, **Defending**, **Mining**, **Repairing**, **Engaging**, or **Idle**.

> Commanded orders are tracked by the server, so the order readout and order paths are most complete in single-player or on the integrated server. On a remote server, autonomously-acquired engagements may show in place of a commanded label.

## Order Paths

Active orders are drawn as animated dotted paths on the tactical map, colored by order type:

| Color | Order |
|-------|-------|
| Red | Attack / Engagement |
| Green | Defend |
| Cyan | Move To |
| Orange | Mine |
| Magenta | Repair |

Paths are shown for your own faction's ships, plus any selected or hovered entity.
