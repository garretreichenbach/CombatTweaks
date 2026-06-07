package videogoose.combattweaks.system.signature;

import javax.vecmath.Vector3f;

/**
 * One detected out-of-sector contact heading for (or freshly jumped near) a player's sector — what the
 * tactical map draws as an "Incoming Signature". Built server-side by {@code IncomingSignatureManager},
 * streamed to the owning client, and rendered by the tactical map.
 *
 * <p>Fidelity-dependent fields are masked on the SERVER before sending, so a low-fidelity contact never
 * leaks its true relation/mass to the client (that's the point of the stealth/recon model).</p>
 */
public class IncomingSignature {

	public static final int REL_FRIENDLY = 0;
	public static final int REL_NEUTRAL = 1;
	public static final int REL_HOSTILE = 2;
	public static final int REL_UNKNOWN = 3;

	public static final int KIND_SUBLIGHT = 0;
	public static final int KIND_JUMP = 1;

	/** Mass not known at all (0), known only as a coarse bucket (1), or known exactly (2). */
	public static final int MASS_NONE = 0;
	public static final int MASS_BUCKET = 1;
	public static final int MASS_EXACT = 2;

	/** Source entity id — used as the map key and so the client can suppress a signature once the entity is locally visible. */
	public int id;
	/** Contact position relative to the viewer's sector centre (world units), i.e. directly in the tactical-map frame. */
	public final Vector3f relPos = new Vector3f();
	/** Contact world velocity, used to draw a heading/approach streak. */
	public final Vector3f vel = new Vector3f();
	/** 0..1 confidence; drives how much detail the label shows and how solid the line is drawn. */
	public float fidelity;
	/** One of REL_*. Already masked to REL_UNKNOWN server-side when fidelity is too low. */
	public int relation = REL_UNKNOWN;
	/** One of MASS_* — how precisely mass is known. */
	public int massDetail = MASS_NONE;
	/** Raw mass; only meaningful when {@link #massDetail} > MASS_NONE. */
	public float mass;
	/** One of KIND_*. */
	public int kind = KIND_SUBLIGHT;
	/** Client-side receipt timestamp, for expiry if updates stop. */
	public long clientReceived;

	public IncomingSignature() {
	}

	public IncomingSignature(int id) {
		this.id = id;
	}
}
