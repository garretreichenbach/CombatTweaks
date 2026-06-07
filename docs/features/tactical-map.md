# Tactical Map

Press **Backslash** (configurable) to open a real-time tactical overlay of your current sector. The map shows ships, stations, and asteroids with faction-colored markers, a sector grid, status rings, rich labels, and animated order paths. Most of what's drawn can be tuned from the in-map [Settings Panel](#settings-panel).

## Camera Controls

| Input | Action |
|-------|--------|
| **WASD** | Pan camera |
| **Space / Ctrl+Space** | Move camera up / down |
| **Shift** | Sprint (faster camera movement) |
| **Right-click drag** | Rotate camera |
| **Scroll wheel** | Adjust viewing range |
| **X** | Reset camera to default position |
| **Double-click entity** | Focus the camera on that entity and follow it |

### Focus & Tracking

Double-clicking an entity frames the camera on it and then **tracks** it — the view follows the entity as it moves, even across sector boundaries. Tracking continues until you pan manually with WASD, which hands control back to you.

## Selection

- **Left-click entity** — select it (replaces the current selection).
- **Shift / Ctrl + click** — add the entity to the current selection.
- **Click and drag** — box-select entities (a plain drag replaces the selection).
- **Shift / Ctrl + drag** — add the box-selected entities to the current selection.
- **Ctrl+A** — select all friendly ships (or, in turret mode, all your turrets); press again to deselect all.
- **Ctrl+S** — toggle turret mode.
- **Middle-click** — open the order radial, on the entity under the cursor (or on the current selection if pointing at empty space).
- **Shift** (held while ordering) — queue the order instead of replacing standing orders.

## Entity Labels

Each marker carries a label whose detail level is set in the settings panel:

- **Minimal** — name and faction.
- **Normal** — adds distance from the camera and the entity's current order/engagement.
- **Full** — adds total mass (including docked turrets), current speed, and the absolute sector plus the subsector cell (e.g. `[B2]`) the entity occupies.

Labels can be shown for **all** entities or only your **own/allied** ones, depending on the settings panel toggle. Jammed or cloaked enemies show obscured readouts (e.g. `???km`) until you have the recon advantage.

## Status Rings

When status display is enabled, markers carry up to three colored arcs showing live status:

| Ring | Color | Meaning |
|------|-------|---------|
| Reactor HP | Green | Reactor hit points |
| Armor HP | Grey | Shared armor HP pool |
| Shield | Amber | Shield strength |

Your own ships always show their rings. Other ships only show rings while hovered, and enemy readouts are **recon-gated** — own and allied ships are always legible, while a jamming or cloaked enemy stays hidden until your scanners overcome it.

## Sector Grid

A faint grid marks sector boundaries so you can see where one sector ends and the next begins. Within each sector, a dotted **subsector** grid divides space into labeled cells — letters along one axis and numbers along another (chessboard style, e.g. `A1`, `B2`) — drawn onto the sector "walls" like the galaxy map. The number of subsector divisions, the grid's reach, and whether it's drawn at all are configurable.

A readout in the **top-right** shows the current sector and subsector of both the camera and your piloted ship.

## Turret Mode

Press **Ctrl+S** to toggle turret mode. The map swaps from showing main ships to showing **turrets**, so you can command them directly.

- Each turret is shown as a **single unit**. A turret is usually two entities — a rotating base plus the gun docked to it — and these are folded into one marker showing the **combined mass**, with per-ship stats and HP hidden (those belong to the parent ship). Single-entity "docked gun" turrets are handled too.
- **Select** turrets by clicking them, or grab them all with **Ctrl+A**.
- Your **selection persists** when you toggle the mode off. Because targets (enemy main ships) are only visible in normal mode, the flow is: enter turret mode → pick turrets → leave turret mode → middle-click the enemy → **Order Attack**.
- Orders cascade from the turret unit down to its gun, which engages on its own. **No fleet membership is required** — the parent ship simply needs to be commandable.
- While turret mode is active, target lines are drawn from turrets to whatever their parent ship is attacking.

## Settings Panel

A compact settings panel in the **top-left** toggles what the map draws. Changes are saved to your client config and persist between sessions.

- **HP Bars** — show status rings on markers and HP bars in the selection panel.
- **Heading** — show velocity/heading lines for entities.
- **Grid** — draw the sector / subsector grid.
- **Signatures** — show [Incoming Signatures](incoming-signatures.md).
- **Labels** — show labels for **All** entities or only **Own** / allied ones.
- **Label Detail** — cycle Minimal / Normal / Full label detail.

## Selection Panel

A panel along the left edge lists your selected ships with shield/armor/reactor bars (numeric values included) and a live order readout (Moving, Attacking, Defending, Mining, Repairing, Idle, …).

- **Pin / unpin** ships with the per-row button to keep them on the panel even when not selected. Allied ships can be pinned too; your own ships always sort above allies. Pins persist across restarts, and any pin that can no longer be found is dropped automatically.
- When the list grows past the screen, a **scrollbar** appears — scroll with the mouse wheel over the panel or drag the thumb.

## Heading & Order Paths

- **Heading lines** (pale blue) show an entity's actual velocity vector. They're drawn for other/neutral factions only when the entity is selected or hovered, and are suppressed for your own ships (whose command paths already show intent).
- **Order paths** are animated dotted lines colored by order type — see [Fleet Orders](fleet-orders.md).

## Other HUD Elements

While the tactical map is open, context-sensitive keybind hints are shown on the right side of the screen.

Two armor HP bars are available at all times (not just in tactical-map mode):

- **Ship Armor HP Bar** — a vertical bar on the left showing your current armor integrity.
- **Target Armor HP Bar** — a bar on the target panel showing the selected target's armor integrity.
