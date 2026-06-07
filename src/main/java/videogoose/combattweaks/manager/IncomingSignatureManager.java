package videogoose.combattweaks.manager;

import api.common.GameCommon;
import api.common.GameServer;
import api.network.packets.PacketUtil;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.Sector;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.ServerConfig;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.network.server.SendIncomingSignaturesPacket;
import videogoose.combattweaks.system.signature.IncomingSignature;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Detects ships approaching a player's sector from nearby sectors — by sublight (heading inward) or by a
 * fresh FTL jump — and streams them to that player's tactical map as "Incoming Signatures".
 *
 * <p>This must run server-side: the client only loads its immediate surroundings, so it can't see contacts
 * a sector or two away. Detection is always-on (everyone gets at least a vague blip); a viewer's scanner
 * (recon) strength sharpens the reading and sees through a contact's cloak/jam, while distance blurs it —
 * so the relation and mass shown scale with how good a look you actually have.</p>
 */
public class IncomingSignatureManager {

	private static final int TICK_INTERVAL_MS = 1000;
	/** Minimum speed (world units/tick-ish) for a sublight ship to count as "approaching". */
	private static final float MIN_APPROACH_SPEED = 4.0f;
	/** How long a jump arrival keeps its dramatic "FTL" flavour / forced visibility after the jump. */
	private static final long JUMP_RECORD_TTL_MS = 6000;

	private static IncomingSignatureManager instance;

	/** entityId -> last jump arrival (sector it jumped to + when), so recent jumps show even while stationary. */
	private final ConcurrentHashMap<Integer, JumpRecord> jumpRecords = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;

	private IncomingSignatureManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-IncomingSignatureManager");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	public static IncomingSignatureManager getInstance() {
		if(instance == null) {
			synchronized(IncomingSignatureManager.class) {
				if(instance == null) {
					instance = new IncomingSignatureManager();
				}
			}
		}
		return instance;
	}

	/** Record that an entity has just jumped into {@code newSector} (called from the jump event listener). */
	public void recordJump(int entityId, Vector3i newSector) {
		jumpRecords.put(entityId, new JumpRecord(new Vector3i(newSector), System.currentTimeMillis()));
	}

	// -------------------------------------------------------------------------

	private void tick() {
		try {
			if(!ConfigManager.getMainConfig().tacticalMapIncomingSignatures.getValue()) {
				return;
			}
			long now = System.currentTimeMillis();
			// Prune stale jump records.
			jumpRecords.entrySet().removeIf(e -> now - e.getValue().time > JUMP_RECORD_TTL_MS);

			int range = (int) Math.round(ConfigManager.getMainConfig().tacticalMapSignatureRange.getValue());
			range = Math.max(1, Math.min(4, range));
			float s = (int) ServerConfig.SECTOR_SIZE.getCurrentState(); // live server sector size

			// Gather viewers (online players controlling an entity in a known sector).
			List<Viewer> viewers = new ArrayList<>();
			for(PlayerState p : GameServer.getServerState().getPlayerStatesByName().values()) {
				SimpleTransformableSendableObject<?> controlled = p.getFirstControlledTransformableWOExc();
				if(!(controlled instanceof SegmentController)) {
					continue;
				}
				Vector3i sec = p.getCurrentSector();
				if(sec == null) {
					continue;
				}
				viewers.add(new Viewer(p, (SegmentController) controlled, new Vector3i(sec), p.getFactionId()));
			}
			if(viewers.isEmpty()) {
				return;
			}
			for(Viewer v : viewers) {
				v.signatures = new ArrayList<>();
			}

			// One sweep over all active (loaded) sectors; assign contacts to any viewer in range.
			for(Sector sector : GameServer.getServerState().getUniverse().getSectorSet()) {
				Vector3i sPos = sector.pos;
				for(SimpleTransformableSendableObject<?> obj : sector.getEntities()) {
					if(!(obj instanceof Ship)) {
						continue;
					}
					SegmentController ship = (SegmentController) obj;
					if(ship.isDocked()) {
						continue;
					}
					Vector3f shipLocal = ship.getWorldTransform() != null ? ship.getWorldTransform().origin : null;
					if(shipLocal == null) {
						continue;
					}
					boolean recentJump = isRecentJump(ship.getId(), now);
					for(Viewer v : viewers) {
						if(v.ship == ship) {
							continue;
						}
						int dist = chebyshev(sPos, v.sector);
						if(dist < 1 || dist > range) {
							continue; // same sector (handled by normal indicators) or out of range
						}
						// relPos: contact position relative to the viewer's sector centre (== tactical-map frame).
						float rx = (sPos.x - v.sector.x) * s + shipLocal.x;
						float ry = (sPos.y - v.sector.y) * s + shipLocal.y;
						float rz = (sPos.z - v.sector.z) * s + shipLocal.z;

						boolean approaching = recentJump || isApproaching(ship, rx, ry, rz);
						if(!approaching) {
							continue;
						}
						v.signatures.add(buildSignature(v, ship, dist, range, rx, ry, rz, recentJump));
					}
				}
			}

			for(Viewer v : viewers) {
				PacketUtil.sendPacket(v.player, new SendIncomingSignaturesPacket(v.signatures));
			}
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("IncomingSignatureManager tick error", e);
		}
	}

	private boolean isRecentJump(int id, long now) {
		JumpRecord jr = jumpRecords.get(id);
		return jr != null && now - jr.time <= JUMP_RECORD_TTL_MS;
	}

	/** A sublight contact counts as incoming if it's moving fast enough generally toward the viewer's sector. */
	private boolean isApproaching(SegmentController ship, float rx, float ry, float rz) {
		Vector3f vel = ship.getLinearVelocity(new Vector3f());
		if(vel.length() < MIN_APPROACH_SPEED) {
			return false;
		}
		// Direction from the contact toward the viewer's sector centre is -relPos; approaching => velocity
		// has a positive component along it.
		return vel.x * (-rx) + vel.y * (-ry) + vel.z * (-rz) > 0;
	}

	private IncomingSignature buildSignature(Viewer v, SegmentController ship, int dist, int range, float rx, float ry, float rz, boolean jump) {
		IncomingSignature sig = new IncomingSignature(ship.getId());
		sig.relPos.set(rx, ry, rz);
		ship.getLinearVelocity(sig.vel);
		sig.kind = jump ? IncomingSignature.KIND_JUMP : IncomingSignature.KIND_SUBLIGHT;

		// Distance blurs the reading; recon vs the contact's stealth sharpens it.
		float base = clamp01(1.0f - (dist - 1.0f) / Math.max(1, range));
		boolean hidden = ship.isCloakedFor(v.ship) || ship.isJammingFor(v.ship);
		float fidelity = hidden ? base * 0.2f : base;
		float recon = safeRecon(v.ship);
		if(recon > 0) {
			float stealth = safeStealth(ship);
			fidelity = Math.min(1.0f, fidelity + base * 0.2f * (recon > stealth ? 1.0f : 0.3f));
		}
		sig.fidelity = fidelity;

		// Relation — only revealed at decent fidelity.
		if(fidelity >= 0.5f) {
			FactionRelation.RType rel = GameCommon.getGameState().getFactionManager().getRelation(v.factionId, ship.getFactionId());
			sig.relation = rel == FactionRelation.RType.FRIEND ? IncomingSignature.REL_FRIENDLY
					: rel == FactionRelation.RType.ENEMY ? IncomingSignature.REL_HOSTILE
					: IncomingSignature.REL_NEUTRAL;
		} else {
			sig.relation = IncomingSignature.REL_UNKNOWN;
		}

		// Mass — exact when we have a good look, a coarse bucket at medium fidelity, nothing when faint.
		if(fidelity >= 0.66f) {
			sig.massDetail = IncomingSignature.MASS_EXACT;
		} else if(fidelity >= 0.33f) {
			sig.massDetail = IncomingSignature.MASS_BUCKET;
		} else {
			sig.massDetail = IncomingSignature.MASS_NONE;
		}
		sig.mass = ship.getMassWithDocks();
		return sig;
	}

	private static float safeRecon(SegmentController sc) {
		try {
			return sc.getReconStrength();
		} catch(Exception e) {
			return 0;
		}
	}

	private static float safeStealth(SegmentController sc) {
		try {
			return sc.getStealthStrength();
		} catch(Exception e) {
			return 0;
		}
	}

	private static int chebyshev(Vector3i a, Vector3i b) {
		return Math.max(Math.abs(a.x - b.x), Math.max(Math.abs(a.y - b.y), Math.abs(a.z - b.z)));
	}

	private static float clamp01(float f) {
		return Math.max(0.0f, Math.min(1.0f, f));
	}

	private static final class JumpRecord {
		final Vector3i sector;
		final long time;

		JumpRecord(Vector3i sector, long time) {
			this.sector = sector;
			this.time = time;
		}
	}

	private static final class Viewer {
		final PlayerState player;
		final SegmentController ship;
		final Vector3i sector;
		final int factionId;
		List<IncomingSignature> signatures;

		Viewer(PlayerState player, SegmentController ship, Vector3i sector, int factionId) {
			this.player = player;
			this.ship = ship;
			this.sector = sector;
			this.factionId = factionId;
		}
	}
}
