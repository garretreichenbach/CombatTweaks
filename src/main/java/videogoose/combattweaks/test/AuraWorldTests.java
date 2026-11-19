package videogoose.combattweaks.test;

import api.event.faction.FactionRelationQueryListener;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.server.test.world.WorldTestExtension;
import org.schema.game.server.test.world.WorldTestSpec;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Aura support for the in-world tests ({@code /run_tests aura/*}). Setup key {@code "aura"}, on a projector
 * ship, at any of its blocks:
 * <pre>{ "at": [0,0,0], "activate": true, "collapseWhenCovering": true, "damageWhenActive": 200, "warWith": 900002 }</pre>
 * <ul>
 *   <li>{@code activate} — turn the ship's aura on as soon as it has a charge (retried every tick).</li>
 *   <li>{@code collapseWhenCovering} — once the aura covers a ship, drain its whole power pool.</li>
 *   <li>{@code damageWhenActive} — once the aura is up, report that much damage to the ship through the normal
 *       server damage hook (exercises attrition).</li>
 *   <li>{@code warWith} — make this ship's faction and the given faction enemies for the run. Not saved: it is
 *       answered by a relation listener, so test factions don't need to exist.</li>
 * </ul>
 * Expect key {@code "aura"}, at any block of the ship measured, with a {@code "measure"}:
 * {@code effect} (1 while the ship has the {@code ConfigGroupRegistry} group named by {@code "group"}),
 * {@code active} / {@code recovering} (1/0, on a projector), {@code charge} (charge 0–1 plus whole charges held, for diagnosing activation), {@code power_pct} (projector's pool, 0–100),
 * {@code covered_by} (how many test projectors currently apply their effects to this ship) and {@code cleared}
 * (1 once some test projector has collapsed and this ship no longer has {@code "group"}).
 */
public final class AuraWorldTests implements WorldTestExtension {

	/** Faction pairs (lower id in the high half) that are at war for the tests. */
	private static final LongSet wars = LongSets.synchronize(new LongOpenHashSet());

	private final Map<SegmentController, Run> runs = new WeakHashMap<>();

	/** Relation override for {@code warWith}; registered once by the mod. Allocation-free, as the listener requires. */
	public static final FactionRelationQueryListener RELATIONS = (a, b, current, isServer) -> wars.contains(pair(a, b)) ? FactionRelation.RType.ENEMY : null;

	private static long pair(int a, int b) {
		return ((long) Math.min(a, b) << 32) | (Math.max(a, b) & 0xFFFFFFFFL);
	}

	@Override
	public String setupKey() {
		return "aura";
	}

	@Override
	public String expectKey() {
		return "aura";
	}

	/** Wait until the ship's reactor is up, so the projector has recalibrated against its chambers. */
	@Override
	public boolean ready(SegmentController ship, long index) {
		return ship instanceof ManagedUsableSegmentController<?> m && m.getManagerContainer().hasActiveReactors();
	}

	@Override
	public void apply(SegmentController ship, long index, WorldTestSpec.Extra entry) {
		var json = entry.json();
		Run run = new Run();
		run.activate = flag(json, "activate");
		run.collapseWhenCovering = flag(json, "collapseWhenCovering");
		run.damageWhenActive = json.has("damageWhenActive") ? json.get("damageWhenActive").getAsFloat() : 0;
		if(json.has("warWith")) {
			wars.add(pair(ship.getFactionId(), json.get("warWith").getAsInt()));
		}
		synchronized(runs) {
			runs.put(ship, run);
		}
	}

	private static boolean flag(JsonObject json, String key) {
		return json.has(key) && json.get(key).getAsBoolean();
	}

	@Override
	public double measure(SegmentController ship, long index, WorldTestSpec.Expect expect) {
		List<Map.Entry<SegmentController, Run>> all;
		synchronized(runs) {
			all = new ArrayList<>(runs.entrySet());
		}
		// Measures run every tick; use them to drive each projector's scripted steps.
		for(Map.Entry<SegmentController, Run> e : all) {
			e.getValue().advance(e.getKey());
		}
		var json = expect.json();
		String measure = json.get("measure").getAsString();
		AuraProjectorAddOn aura = ship instanceof ManagedSegmentController<?> m ? AuraProjectorAddOn.getActiveAura(m) : null;
		return switch(measure) {
			case "effect" -> hasGroup(ship, json) ? 1 : 0;
			case "active" -> aura != null && aura.isActive() ? 1 : 0;
			case "recovering" -> aura != null && aura.isRecovering() ? 1 : 0;
			case "charge" -> aura == null ? Double.NaN : aura.getCharge() + aura.getCharges();
			case "power_pct" -> aura == null ? Double.NaN : aura.getPowerFraction() * 100.0;
			case "covered_by" -> {
				int n = 0;
				for(Map.Entry<SegmentController, Run> e : all) {
					AuraProjectorAddOn p = projector(e.getKey());
					if(p != null && p.isTargeting(ship)) n++;
				}
				yield n;
			}
			case "cleared" -> {
				boolean collapsed = false;
				for(Map.Entry<SegmentController, Run> e : all) {
					collapsed |= e.getValue().collapsed;
				}
				yield collapsed && !hasGroup(ship, json) ? 1 : 0;
			}
			default -> throw new IllegalArgumentException("unknown aura measure '" + measure + "'");
		};
	}

	private static boolean hasGroup(SegmentController ship, JsonObject json) {
		return ship.getConfigManager().isActive(ConfigGroupRegistry.valueOf(json.get("group").getAsString()).configEffectGroup);
	}

	private static AuraProjectorAddOn projector(SegmentController ship) {
		return ship instanceof ManagedSegmentController<?> m ? AuraProjectorAddOn.getActiveAura(m) : null;
	}

	/** One projector's scripted steps. */
	private static final class Run {
		boolean activate, collapseWhenCovering, collapsed, damaged;
		float damageWhenActive;

		void advance(SegmentController ship) {
			AuraProjectorAddOn aura = projector(ship);
			if(aura == null) return;
			if(activate && !collapsed && !aura.isActive()) {
				// Charging only runs with auto-charge on; a player turns it on from the hotbar.
				if(!aura.isAutoChargeOn()) aura.setAutoChargeOn(true);
				if(aura.getCharges() > 0) aura.executeModule();
			}
			if(!aura.isActive()) return;
			if(damageWhenActive > 0 && !damaged && ship instanceof Ship s) {
				damaged = true;
				s.onDamageServerRootObject(damageWhenActive, null);
			}
			if(collapseWhenCovering && !collapsed && covering(aura)) {
				collapsed = true;
				aura.disrupt(null, Float.MAX_VALUE);
			}
		}

		private static boolean covering(AuraProjectorAddOn aura) {
			for(var s : aura.getSegmentController().getState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
				if(s instanceof SegmentController sc && aura.isTargeting(sc)) return true;
			}
			return false;
		}
	}
}
