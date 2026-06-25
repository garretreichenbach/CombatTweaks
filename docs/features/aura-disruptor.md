# Aura Disruptor

The Aura Disruptor is a support-beam weapon that drains an enemy projector's [aura](auras.md) power directly, collapsing the aura without having to break the projecting ship's hull. It is the precise counter to aura ships — the alternative being slower [attrition](auras.md#shutting-an-aura-down) from regular weapons fire.

## Building one

The Aura Disruptor is a computer + module weapon group, like other beam weapons:

- **Aura Disruptor Computer** — the control block; bind a group of modules to it.
- **Aura Disruptor Module** — the beam emitters. More modules = more disruptor power per shot.

It fires as a support beam (it deals no block damage) and consumes reactor power while firing (`aura_disruptor_beam_power_consumption_per_unit`, default `12` per module).

## What it does

When the beam hits a ship that is currently projecting an **active** aura — of either role — it drains that aura's power pool. The drain per shot scales with beam power (`aura_disruptor_beam_power_per_unit`, default `1.0` per module) times the disruptor multiplier (`aura_disruptor_power_multiplier`, default `1.5`). Drain enough and the aura collapses until it regenerates.

The beam only "hits" valid targets: it passes over friendlies and over ships that aren't projecting an active aura, so you can fire it into a furball and only affect aura projectors.

## AI usage

Fleet ships armed with an Aura Disruptor use it automatically: when commanded against an enemy that is running an active aura, the ship's AI fires the disruptor at it and breaks off if the target's aura goes down or the target is no longer valid. This is handled alongside the normal fleet AI fire logic.

## Configuration

Disruptor balance values live in `system_config.yml` (server-synced). See the [Configuration](configuration.md) page.
