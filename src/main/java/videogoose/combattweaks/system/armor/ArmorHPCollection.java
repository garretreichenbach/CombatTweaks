package videogoose.combattweaks.system.armor;

import api.common.GameClient;
import api.common.GameServer;
import api.network.packets.PacketUtil;
import it.unimi.dsi.fastutil.shorts.Short2IntArrayMap;
import org.schema.common.util.StringTools;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.GUIKeyValueEntry;
import org.schema.game.client.view.gui.structurecontrol.ModuleValueEntry;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.*;
import org.schema.game.common.controller.rails.RailRelation;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Timer;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.network.server.SendArmorHPSyncPacket;

import java.util.*;

/**
 * CollectionManager for ArmorHP.
 * <p>We rely on the game's element count map. Add/remove events only schedule a debounced recalculation.</p>
 */
public class ArmorHPCollection extends ElementCollectionManager<ArmorHPUnit, ArmorHPCollection, VoidElementManager<ArmorHPUnit, ArmorHPCollection>> {

	private static final long UPDATE_FREQUENCY = 1000;
	private static final long REGEN_FREQUENCY = 1000;
	private static final long SYNC_FREQUENCY = 500;
	// Idle re-broadcast interval. Whenever armor HP actually changes (combat, regen) the change-driven path
	// above syncs within SYNC_FREQUENCY, and a freshly-loaded ship syncs once immediately (maxHP jumps 0 ->
	// value, which counts as a change). This heartbeat only exists as a safety net so a client that MISSED
	// that change-driven sync — e.g. it entered a sector where the ships were already loaded server-side —
	// still converges eventually. It does NOT need to be fast: a low value just re-sends unchanged values to
	// every player for every armored entity, which is the bulk of armor-sync traffic. 10s keeps convergence
	// reasonable while cutting idle broadcasts ~5x versus a 2s heartbeat.
	private static final long SYNC_HEARTBEAT = 10000;
	// Delay after the last enqueued change before processing the batch (ms)
	private static final long PROCESS_DELAY_MS = 500; // arbitrary time since last update
	private static final Set<SegmentController> pending = Collections.synchronizedSet(new HashSet<SegmentController>());
	// Track last change time per controller. Using WeakHashMap so controllers can be GC'd when no longer referenced.
	private static final Map<SegmentController, Long> lastChangeTimestamp = Collections.synchronizedMap(new WeakHashMap<SegmentController, Long>());
	private final Short2IntArrayMap armorCountCacheMap = new Short2IntArrayMap();

	private final double armorHPValueMultiplier;
	private final double armorHPScalingExponent;
	private final double armorHPLostPerDamageAbsorbed;
	private final double baseArmorHPBleedThroughStart;
	private double currentHP;
	private double maxHP;
	private boolean flagCollectionChanged = true;
	private boolean updateMaxOnly;
	private long lastRegen;
	private boolean regenEnabled;
	private long lastSync;
	private double lastSyncedHP = -1;
	private double lastSyncedMaxHP = -1;

	public ArmorHPCollection(SegmentController segmentController, VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager) {
		super(ElementKeyMap.CORE_ID, segmentController, armorHPManager);
		armorHPValueMultiplier = ConfigManager.getSystemConfig().armorHpValueMultiplier.value;
		armorHPScalingExponent = ConfigManager.getSystemConfig().armorHpScalingExponent.value;
		armorHPLostPerDamageAbsorbed = ConfigManager.getSystemConfig().armorHpLostPerDamageAbsorbed.value;
		baseArmorHPBleedThroughStart = ConfigManager.getSystemConfig().baseArmorHpBleedThroughStart.value;
	}

	public static ArmorHPCollection getCollection(SegmentController controller) {
		if(controller.railController.getRoot() instanceof ManagedUsableSegmentController<?>) {
			try {
				ManagedUsableSegmentController<?> managed = (ManagedUsableSegmentController<?>) controller.railController.getRoot();
				for(ManagerModule<?, ?, ?> module : managed.getManagerContainer().getModules()) {
					if(module instanceof ManagerModuleSingle) {
						Object sm = ((ManagerModuleSingle<?,?,?>) module).getCollectionManager();
						if(sm instanceof ArmorHPCollection) {
							return (ArmorHPCollection) sm;
						}
					}
				}
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("Error getting ArmorHPCollection for entity " + controller.getName() + " (" + controller.getUniqueIdentifier() + ")", exception);
			}
		}
		System.out.println("ArmorHPCollection not found for entity " + controller);
		return null;
	}

	/**
	 * Enqueue a full recalculation operation for the given controller.
	 */
	public static void enqueueRecalc(SegmentController controller) {
		if(controller == null) {
			return;
		}
		// Armor HP is simulated server-authoritatively. The client must never recalc
		// locally (it would fight the synced value and make the HP jump up/down), and
		// must never populate the static pending set (it would never be drained client-side).
		if(!controller.isOnServer()) {
			return;
		}
		synchronized(pending) {
			pending.add(controller);
			lastChangeTimestamp.put(controller, System.currentTimeMillis());
		}
	}

	@Override
	public int getMargin() {
		return 0;
	}

	@Override
	protected Class<ArmorHPUnit> getType() {
		return ArmorHPUnit.class;
	}

	@Override
	public boolean needsUpdate() {
		// The engine only calls update() while this returns true (ManagerModuleSingle.update gates the call,
		// and ManagerContainer keeps the module in its updateModules list via needsAnyUpdate, which AND-s in
		// needsUpdate). On the SERVER we must keep ticking whenever there's armor HP to sync: after a recalc
		// both flagCollectionChanged and the pending stamp clear, so without this update() would stop forever
		// the instant the first recalc finishes. The server's sync burst fires within a few ms of the ship
		// loading — before a client is tracking the entity — so that client misses it, and with update() dead
		// the SYNC_HEARTBEAT re-broadcast never runs, leaving the client stuck showing 0 HP (empty bar). An
		// armorless ship (maxHP==0) still settles; a later block event re-flags a change and revives it.
		if(isOnServer() && maxHP > 0) {
			return true;
		}
		return flagCollectionChanged || lastChangeTimestamp.containsKey(getSegmentController());
	}

	@Override
	public ArmorHPUnit getInstance() {
		return new ArmorHPUnit();
	}

	@Override
	protected void onChangedCollection() {
		if(!getSegmentController().isOnServer()) {
			((GameClientState) getSegmentController().getState()).getWorldDrawer().getGuiDrawer().managerChanged(this);
		}
		flagCollectionChanged = true;
	}

	@Override
	public void update(Timer timer) {
		// The client is display-only: armor HP is authoritative on the server and delivered
		// via SendArmorHPSyncPacket -> applySync(). Running recalc/regen here would fight the
		// synced value and make the displayed HP jump up and down.
		if(!isOnServer()) {
			return;
		}

		try {
			processPendingIfReady();
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error processing pending armor changes", e);
		}

		if(currentHP < maxHP && maxHP > 0) {
			flagCollectionChanged = false; // prevent placing blocks to reset HP unintentionally
		} else {
			if(System.currentTimeMillis() - lastUpdate > UPDATE_FREQUENCY && maxHP <= 0 && hasAnyArmorBlocks() && getSegmentController().isFullyLoadedWithDock() && flagCollectionChanged) {
				lastUpdate = System.currentTimeMillis();
				enqueueRecalc(getSegmentController());
			}
		}

		regenEnabled = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) > 1.0f;
		if(System.currentTimeMillis() - lastRegen >= REGEN_FREQUENCY && regenEnabled && currentHP < maxHP) {
			lastRegen = System.currentTimeMillis();
			doRegen();
		}

		if(maxHP > 0) {
			long now = System.currentTimeMillis();
			boolean changed = currentHP != lastSyncedHP || maxHP != lastSyncedMaxHP;
			boolean heartbeatDue = now - lastSync >= SYNC_HEARTBEAT;
			if((changed && now - lastSync >= SYNC_FREQUENCY) || heartbeatDue) {
				lastSync = now;
				lastSyncedHP = currentHP;
				lastSyncedMaxHP = maxHP;
				broadcastSync();
			}
		}
	}

	private void processPendingIfReady() {
		SegmentController sc = getSegmentController();
		Long last = lastChangeTimestamp.get(sc);
		if(last == null) {
			return;
		}
		if(System.currentTimeMillis() - last < PROCESS_DELAY_MS) {
			return; // debounce
		}
		try {
			if(currentHP < maxHP) {
				updateMaxOnly = true;
			}
			recalcHP();
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Failed to recalc armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", e);
		} finally {
			lastChangeTimestamp.remove(sc);
			pending.remove(sc);
		}
	}

	public void doRegen() {
		if(regenEnabled) {
			// apply() returns the base 1.0 scaled by active regen effects (e.g. 1.05 == +5%).
			// Regen per tick is the fraction above 1.0, so a 1.05 multiplier restores 5% of maxHP,
			// not a full maxHP (which would snap armor back to full in a single tick).
			double regenFraction = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) - 1.0;
			if(regenFraction > 0) {
				double regen = regenFraction * maxHP;
				currentHP = Math.min(maxHP, currentHP + regen);
			}
		}
	}

	private void broadcastSync() {
		try {
			SendArmorHPSyncPacket packet = new SendArmorHPSyncPacket(getSegmentController().getId(), currentHP, maxHP);
			for(PlayerState playerState : GameServer.getServerState().getPlayerStatesByName().values()) {
				PacketUtil.sendPacket(playerState, packet);
			}
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error broadcasting armor HP sync", e);
		}
	}

	public void applySync(double syncedCurrentHP, double syncedMaxHP) {
		if(isOnServer()) return;
		maxHP = syncedMaxHP;
		currentHP = Math.max(0, Math.min(syncedMaxHP, syncedCurrentHP));
	}

	@Override
	public GUIKeyValueEntry[] getGUICollectionStats() {
		return new GUIKeyValueEntry[]{new ModuleValueEntry(Lng.str("HP Status"), StringTools.formatPointZero(currentHP) + "/" + StringTools.formatPointZero(maxHP) + " [" + getHPPercent() * 100 + "%]")};
	}

	@Override
	public String getModuleName() {
		return Lng.str("Armor HP System");
	}

	private boolean hasAnyArmorBlocks() {
		// Query the live engine count map directly, NOT armorCountCacheMap. The cache is only
		// populated by recalcHP(), so a ship loaded from disk (e.g. on a dedicated server, which
		// fires no block-add events) would have an empty cache, hasAnyArmorBlocks() would return
		// false, and the bootstrap recalc in update() would never fire — leaving maxHP stuck at 0
		// for every loaded ship. Reading getElementClassCountMap() (the same source getArmorCounts
		// uses) breaks that chicken-and-egg.
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && !info.isDeprecated() && info.isArmor()) {
				short id = info.getId();
				if(id != 0) {
					try {
						if(getSegmentController().getElementClassCountMap().get(id) > 0) {
							return true;
						}
					} catch(Exception ignored) {
						//ignore
					}
				}
			}
		}
		return false;
	}

	private void recalcHP() {
		long start = System.currentTimeMillis();
		double previousHP = currentHP;
		currentHP = 0;
		maxHP = 0;

		armorCountCacheMap.clear();
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && !info.isDeprecated() && info.isArmor()) {
				short type = info.getId();
				armorCountCacheMap.put(type, 0);
			}
		}
		getArmorCounts(getSegmentController(), armorCountCacheMap, true);

		double rawSum = 0;
		for(short type : armorCountCacheMap.keySet()) {
			int count = armorCountCacheMap.get(type);
			if(count > 0) {
				ElementInformation info = ElementKeyMap.getInfo(type);
				if(info != null) {
					rawSum += info.getArmorValue() * count;
				}
			}
		}

		maxHP = armorHPValueMultiplier * Math.pow(rawSum, armorHPScalingExponent);
		if(!updateMaxOnly) {
			currentHP = maxHP;
		}

		if(updateMaxOnly) {
			currentHP = Math.min(previousHP, maxHP);
		}

		if(currentHP < 0) {
			currentHP = 0;
		}
		if(maxHP < 0) {
			maxHP = 0;
		}
		if(currentHP > maxHP) {
			currentHP = maxHP;
		}

		flagCollectionChanged = false;
		updateMaxOnly = false;
		lastUpdate = System.currentTimeMillis();

		if(getSegmentController().railController.isRoot()) { //We only log for the root controller to avoid log spam
			CombatTweaks.getInstance().logInfo("Recalculated armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ") in " + (System.currentTimeMillis() - start) + " ms. Current HP: " + currentHP + ", Max HP: " + maxHP);
		}
	}

	private static void getArmorCounts(SegmentController controller, Map<Short, Integer> armorCounts, boolean includingDocked) {
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && !info.isDeprecated() && info.isArmor()) {
				short type = info.getId();
				int count = 0;
				try {
					count = controller.getElementClassCountMap().get(type);
				} catch(Exception exception) {
					//ignore
				}
				if(count > 0) {
					armorCounts.put(type, armorCounts.get(type) + count);
				}
			}
		}
		if(!includingDocked) {
			return;
		}
		// Recurse into docked entities
		List<RailRelation> docked = controller.railController.next;
		if(docked != null) {
			for(RailRelation relation : docked) {
				SegmentController dockedController = relation.docked.getSegmentController();
				getArmorCounts(dockedController, armorCounts, true);
			}
		}
	}

	public double getCurrentHP() {
		return currentHP;
	}

	public void setCurrentHP(double hp) {
		double prev = currentHP;
		currentHP = Math.max(0, Math.min(maxHP, hp));
		if(!isOnServer() && currentHP == 0 && prev > 0) {
			if(getSegmentController().isClientOwnObject()) {
				GameClient.getClientState().message(Lng.astr("Armor integrity is fully compromised!"), 2);
			}
		}
	}

	public double getMaxHP() {
		return maxHP;
	}

	public double getHPPercent() {
		return (maxHP == 0 || currentHP == 0) ? 0 : Math.max(currentHP / maxHP, 0);
	}

	public float processDamageToArmor(float dmgIn, float dmgAfterBaseArmorProtection) {
		if(maxHP <= 0 || currentHP <= 0) {
			return dmgAfterBaseArmorProtection;
		}
		if(Float.isNaN(dmgIn)) {
			return dmgAfterBaseArmorProtection;
		}
		double hpPercent = getHPPercent();
		double bleedThreshold = getBleedThroughThreshold();
		double bleedFrac;
		if(bleedThreshold <= 0) {
			bleedFrac = 0;
		} else {
			bleedFrac = Math.min(1.0, (1.0 - hpPercent) / bleedThreshold);
		}
		bleedFrac = Math.max(0.0, bleedFrac);
		double desiredAbsorbed = dmgAfterBaseArmorProtection * (1.0 - bleedFrac);
		double absorbCapacity = currentHP / armorHPLostPerDamageAbsorbed;
		double actualAbsorbed = Math.min(desiredAbsorbed, absorbCapacity);
		double hpLoss = actualAbsorbed * armorHPLostPerDamageAbsorbed;
		setCurrentHP(currentHP - hpLoss);
		double finalDamage = dmgAfterBaseArmorProtection - actualAbsorbed;
		return (float) Math.max(0.0, finalDamage);
	}

	public double getBleedThroughThreshold() {
		return Math.max(Math.min(getConfigManager().apply(StatusEffectType.ARMOR_HP_ABSORPTION, 1.0f), baseArmorHPBleedThroughStart), 0.0f);
	}

	public boolean canBleedThrough() {
		return getBleedThroughThreshold() > 0 && getHPPercent() < 1.0;
	}
}
