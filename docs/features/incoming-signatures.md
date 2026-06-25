# Incoming Signatures

The tactical map gives you early warning of ships approaching from neighboring sectors — both those flying in at sublight and those jumping in. Each contact is drawn as a **travel line** showing its approximate approach vector, capped with an **Incoming Signature** label.

## What a Signature Shows

A signature label estimates:

- **Mass** — a rough size estimate for the inbound contact.
- **Relation** — friendly, neutral, hostile, or unknown.
- **Count** — co-located contacts (such as a fleet) are merged into a single label that summarizes how many are inbound and their overall relation.

The travel line animates along the contact's heading — a moving streak rather than a static mark — and the position is smoothly extrapolated client-side between server updates so motion looks continuous.

## Accuracy

Signature detail is not perfect intelligence. How much you can read depends on:

- **Proximity** — the closer the contact, the sharper the estimate.
- **Recon vs. stealth** — your scanning strength weighed against the contact's jamming/cloaking. A baseline of detection is always available; better recon (and weaker enemy stealth) tightens the mass and relation estimates.

## Configuration

Incoming signatures are controlled from the in-map [settings panel](tactical-map.md#settings-panel) and the client [configuration](configuration.md):

- **Signatures** toggle — show or hide the system.
- **Signature range** — how many sectors out to scan for inbound contacts. A larger range gives earlier warning but means more scanning work on the server.
