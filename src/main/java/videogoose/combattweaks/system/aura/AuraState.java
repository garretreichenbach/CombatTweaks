package videogoose.combattweaks.system.aura;

/**
 * A single active aura, as projected by one ship's Aura Projector. Built server-side by
 * {@link videogoose.combattweaks.system.aura.AuraProjectorAddOn}, cached/broadcast by
 * {@code AuraManager}, streamed to clients via {@code SendAuraSyncPacket}, and drawn as a bounding sphere
 * on the tactical map.
 */
public class AuraState {

	/** Friendly buff aura (range/shield). */
	public static final int KIND_SUPPORT = 0;
	/** Hostile debuff aura applied to enemies inside the sphere (Phase 3). */
	public static final int KIND_OFFENSE = 1;

	/** Projecting entity id — the tactical-map lookup key. */
	public final int entityId;
	/** Sector the projector is in. Server-side only (used to scope the broadcast); not serialized. */
	public int sectorId;
	/** Sphere radius in world units. */
	public float radius;
	/** One of KIND_*. */
	public int auraKind = KIND_SUPPORT;
	/** currentPower/maxPower (0..1) — drives draw intensity so disrupted auras render fainter. */
	public float powerFraction = 1.0f;

	public AuraState(int entityId) {
		this.entityId = entityId;
	}

	public AuraState(int entityId, int sectorId, float radius, int auraKind, float powerFraction) {
		this.entityId = entityId;
		this.sectorId = sectorId;
		this.radius = radius;
		this.auraKind = auraKind;
		this.powerFraction = powerFraction;
	}
}
