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

	public ArmorHPCollection(SegmentController segmentController, VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager) {
		super(ElementKeyMap.CORE_ID, segmentController, armorHPManager);
		armorHPValueMultiplier = ConfigManager.getSystemConfig().getDouble("armor_hp_value_multiplier");
		armorHPScalingExponent = ConfigManager.getSystemConfig().getDouble("armor_hp_scaling_exponent");
		armorHPLostPerDamageAbsorbed = ConfigManager.getSystemConfig().getDouble("armor_hp_lost_per_damage_absorbed");
		baseArmorHPBleedThroughStart = ConfigManager.getSystemConfig().getDouble("base_armor_hp_bleed_through_start");
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

		if(isOnServer() && maxHP > 0 && System.currentTimeMillis() - lastSync >= SYNC_FREQUENCY && currentHP != lastSyncedHP) {
			lastSync = System.currentTimeMillis();
			lastSyncedHP = currentHP;
			broadcastSync();
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
			double regen = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) * maxHP;
			currentHP = Math.min(maxHP, currentHP + regen);
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

	private int getCount(short type) {
		return armorCountCacheMap.get(type);
	}

	private boolean hasAnyArmorBlocks() {
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && info.isArmor()) {
				short id = info.getId();
				if(id != 0 && getCount(id) > 0) {
					return true;
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
