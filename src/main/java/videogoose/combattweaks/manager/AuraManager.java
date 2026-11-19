package videogoose.combattweaks.manager;

import api.common.GameServer;
import api.network.packets.PacketUtil;
import org.schema.game.common.controller.SegmentController;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.world.Sector;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.network.objects.Sendable;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.network.server.SendAuraSyncPacket;
import videogoose.combattweaks.system.aura.AuraState;
import videogoose.combattweaks.utils.ServerTicker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry + broadcaster of active auras. Each {@link videogoose.combattweaks.system.aura.AuraProjectorAddOn}
 * reports its current sphere here while active and clears it when it deactivates; this manager streams, per
 * tick, the auras whose sphere can reach each player's sector to that player so the tactical map can draw them as bounding spheres.
 *
 * <p>Kept deliberately cheap: projectors push their own state (no full entity sweep), and we only prune entries
 * whose projector has unloaded. When no auras exist anywhere we send one clearing broadcast and then go quiet.</p>
 */
public class AuraManager {

	private static final int TICK_INTERVAL_MS = 500;

	private static AuraManager instance;

	/** entityId -> latest reported aura (includes the projector's sectorId for scoping the broadcast). */
	private final ConcurrentHashMap<Integer, AuraState> active = new ConcurrentHashMap<>();
	/**
	 * No-stacking registry: key = (affected ship id, aura kind), value = the projector ship id currently affecting
	 * it. A ship can be claimed by at most one aura <em>per kind</em>, so it carries at most one support aura and
	 * one offense aura at a time. Projectors claim a target before applying effects and release it when the target
	 * leaves range or the aura stops; stale claims (owner unloaded) are pruned each tick.
	 */
	private final ConcurrentHashMap<Long, Integer> claims = new ConcurrentHashMap<>();
	/** True once we've broadcast the empty state to all players, so we don't spam empty packets while idle. */
	private boolean clearedClients;

	private AuraManager() {
		// Server thread, not a private executor: the tick reads the live object/player maps and sends packets.
		ServerTicker.every(TICK_INTERVAL_MS, this::tick);
	}

	public static AuraManager getInstance() {
		if(instance == null) {
			synchronized(AuraManager.class) {
				if(instance == null) {
					instance = new AuraManager();
				}
			}
		}
		return instance;
	}

	/** Report (add or refresh) an active aura. Called by the projector addon on the server. */
	public void report(AuraState state) {
		active.put(state.entityId, state);
	}

	/** Remove an aura that is no longer active. Called by the projector addon on the server. */
	public void clear(int entityId) {
		active.remove(entityId);
	}

	private static long claimKey(int targetId, int kind) {
		return ((long) targetId << 2) | (kind & 0x3L);
	}

	/**
	 * Try to claim {@code targetId} for an aura of {@code kind} on behalf of {@code projectorId}. Succeeds (and
	 * records/refreshes the claim) if the target isn't already claimed for that kind by a <em>different</em>
	 * projector — enforcing one aura of each kind per ship (no stacking).
	 *
	 * @return true if this projector may apply its effects to the target, false if another aura already owns it
	 */
	public boolean tryClaim(int targetId, int kind, int projectorId) {
		Integer owner = claims.putIfAbsent(claimKey(targetId, kind), projectorId);
		return owner == null || owner == projectorId;
	}

	/** Release one target's claim, but only if this projector currently owns it. */
	public void release(int targetId, int kind, int projectorId) {
		claims.remove(claimKey(targetId, kind), projectorId);
	}

	/** Forget all auras and claims. Called when a server (world) starts, since entity ids are reused across worlds. */
	public void reset() {
		active.clear();
		claims.clear();
		clearedClients = false;
	}

	// -------------------------------------------------------------------------

	/**
	 * Whether an aura's sphere can reach into {@code playerSector}: its projector is within (radius / sector size,
	 * rounded up, at least 1) sectors on every axis. Lets players see and feel auras straddling a sector border.
	 */
	private static boolean reaches(AuraState a, Vector3i playerSector) {
		Sector sector = GameServer.getServerState().getUniverse().getSector(a.sectorId);
		if(sector == null || playerSector == null) {
			return false;
		}
		int sectorSize = (Integer) ServerConfig.SECTOR_SIZE.getCurrentState();
		int reach = Math.max(1, (int) Math.ceil(a.radius / sectorSize));
		return Math.abs(sector.pos.x - playerSector.x) <= reach
				&& Math.abs(sector.pos.y - playerSector.y) <= reach
				&& Math.abs(sector.pos.z - playerSector.z) <= reach;
	}

	private void tick() {
		try {
			if(GameServer.getServerState() == null) {
				return;
			}
			// Drop auras whose projector has unloaded/despawned without an explicit clear, and refresh the rest's
			// sector live: projectors only report every few seconds and may have crossed a border since.
			active.values().removeIf(a -> {
				if(GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().get(a.entityId) instanceof SegmentController sc) {
					a.sectorId = sc.getSectorId();
					return false;
				}
				return true;
			});
			// Drop no-stacking claims whose owning projector has unloaded, so its targets free up for other auras.
			claims.values().removeIf(ownerId -> {
				Sendable s = GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().get(ownerId);
				return !(s instanceof SegmentController);
			});

			if(active.isEmpty()) {
				if(!clearedClients) {
					for(PlayerState p : GameServer.getServerState().getPlayerStatesByName().values()) {
						if(GameServer.getServerClient(p) != null) {
							PacketUtil.sendPacket(p, new SendAuraSyncPacket(new ArrayList<>()));
						}
					}
					clearedClients = true;
				}
				return;
			}
			clearedClients = false;

			for(PlayerState p : GameServer.getServerState().getPlayerStatesByName().values()) {
				// Players still logging in have no server client; sendPacket would throw and abort the whole tick.
				if(GameServer.getServerClient(p) == null) {
					continue;
				}
				Vector3i playerSector = p.getCurrentSector();
				List<AuraState> nearby = new ArrayList<>();
				for(AuraState a : active.values()) {
					if(a.sectorId == p.getCurrentSectorId() || reaches(a, playerSector)) {
						nearby.add(a);
					}
				}
				PacketUtil.sendPacket(p, new SendAuraSyncPacket(nearby));
			}
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("AuraManager tick error", e);
		}
	}
}
