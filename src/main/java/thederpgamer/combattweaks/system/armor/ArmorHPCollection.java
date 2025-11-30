package thederpgamer.combattweaks.system.armor;

import api.common.GameClient;
import api.utils.game.SegmentControllerUtils;
import it.unimi.dsi.fastutil.shorts.Short2IntArrayMap;
import org.schema.common.util.StringTools;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.GUIKeyValueEntry;
import org.schema.game.client.view.gui.structurecontrol.ModuleValueEntry;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Timer;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.manager.ConfigManager;

import java.lang.reflect.Field;
import java.util.*;

import static java.lang.Math.max;

/**
 * CollectionManager for ArmorHP.
 * <p>Rather than storing each block index, this just stores the type and count for efficiency.</p>
 *
 * @author VideoGoose (TheDerpGamer)
 */
public class ArmorHPCollection extends ElementCollectionManager<ArmorHPUnit, ArmorHPCollection, VoidElementManager<ArmorHPUnit, ArmorHPCollection>> {

	private static final long UPDATE_FREQUENCY = 5000;
	private static final long REGEN_FREQUENCY = 1000;
	// Delay after the last enqueued change before processing the batch (ms)
	private static final long PROCESS_DELAY_MS = 250; // arbitrary time since last update
	// Pending changes keyed by SegmentController. WeakHashMap so controllers can be GC'd when gone.
	private static final Map<SegmentController, PendingData> pending = Collections.synchronizedMap(new WeakHashMap<SegmentController, PendingData>());
	// Track last change time per controller
	private static final Map<SegmentController, Long> lastChangeTimestamp = Collections.synchronizedMap(new WeakHashMap<SegmentController, Long>());
	private final double armorHPValueMultiplier;
	private final double armorHPLostPerDamageAbsorbed;
	private final double baseArmorHPBleedthroughStart;
	private final double minArmorHPBleedthroughStart;
	private final Short2IntArrayMap armorMap = new Short2IntArrayMap();
	private double currentHP;
	private double maxHP;
	private boolean flagCollectionChanged;
	private boolean updateMaxOnly;
	private long lastUpdate;
	private long lastRegen;
	private boolean regenEnabled;

	public ArmorHPCollection(SegmentController segmentController, VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager) {
		super(ElementKeyMap.CORE_ID, segmentController, armorHPManager);
		armorHPValueMultiplier = ConfigManager.getSystemConfig().getDouble("armor_hp_value_multiplier");
		armorHPLostPerDamageAbsorbed = ConfigManager.getSystemConfig().getDouble("armor_hp_lost_per_damage_absorbed");
		baseArmorHPBleedthroughStart = ConfigManager.getSystemConfig().getDouble("base_armor_hp_bleedthrough_start");
		minArmorHPBleedthroughStart = ConfigManager.getSystemConfig().getDouble("min_armor_hp_bleedthrough_start");
		if(armorMap.isEmpty()) {
			for(ElementInformation info : ElementKeyMap.getInfoArray()) {
				if(info != null && info.isArmor()) {
					armorMap.put(info.getId(), 0);
				}
			}
		}
	}

	public static ArmorHPCollection getCollection(SegmentController controller) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> managed = (ManagedUsableSegmentController<?>) controller;
			ArrayList<ElementCollectionManager<?, ?, ?>> cms = SegmentControllerUtils.getCollectionManagers(managed, ArmorHPCollection.class);
			for(ElementCollectionManager<?, ?, ?> cm : cms) {
				if(cm instanceof ArmorHPCollection) {
					return (ArmorHPCollection) cm;
				}
			}
		}
		return null;
	}

	/**
	 * Enqueue an add operation for the given controller. This avoids doing expensive collection lookups
	 * and allows batch processing once no more changes occur for a short time.
	 */
	public static void enqueueAdd(SegmentController controller, long index, short type) {
		if(controller == null) {
			return;
		}
		ArmorHPCollection collection = getCollection(controller);
		synchronized(pending) {
			PendingData pd = pending.get(controller);
			if(pd == null) {
				pd = new PendingData();
				pending.put(controller, pd);
			}
			// If a full recalc is scheduled, don't record individual changes - recalc will rebuild from the raw collection
			if(pd.recalcRequested) {
				lastChangeTimestamp.put(controller, System.currentTimeMillis());
				return;
			}
			// aggregate per-type delta
			int prev = pd.typeDeltas.get(type);
			pd.typeDeltas.put(type, prev + 1);
			// Only record per-index changes if the live collection requires it (rawCollection == null)
			if(collection != null) {
				try {
					Field rawField = ArmorHPCollection.class.getSuperclass().getDeclaredField("rawCollection");
					rawField.setAccessible(true);
					Object raw = rawField.get(collection);
					if(raw == null && type != 0) {
						pd.indexChanges.add(new Change(Change.Op.ADD, index, type));
					}
				} catch(Throwable t) {
					// reflection fallback: if we can't access rawCollection, conservatively record the index change
					if(type != 0) {
						pd.indexChanges.add(new Change(Change.Op.ADD, index, type));
					}
				}
			}
			lastChangeTimestamp.put(controller, System.currentTimeMillis());
		}
	}

	/**
	 * Enqueue a remove operation for the given controller.
	 */
	public static void enqueueRemove(SegmentController controller, long index, short type) {
		if(controller == null) {
			return;
		}
		ArmorHPCollection collection = getCollection(controller);
		synchronized(pending) {
			PendingData pd = pending.get(controller);
			if(pd == null) {
				pd = new PendingData();
				pending.put(controller, pd);
			}
			// If a full recalc is scheduled, don't record individual changes - recalc will rebuild from the raw collection
			if(pd.recalcRequested) {
				lastChangeTimestamp.put(controller, System.currentTimeMillis());
				return;
			}
			// aggregate per-type delta
			int prev = pd.typeDeltas.get(type);
			pd.typeDeltas.put(type, prev - 1);
			// Only record per-index changes if the live collection requires it (rawCollection == null)
			if(collection != null) {
				try {
					Field rawField = ArmorHPCollection.class.getSuperclass().getDeclaredField("rawCollection");
					rawField.setAccessible(true);
					Object raw = rawField.get(collection);
					if(raw == null && type != 0) {
						pd.indexChanges.add(new Change(Change.Op.REMOVE, index, type));
					}
				} catch(Throwable t) {
					// reflection fallback: if we can't access rawCollection, conservatively record the index change
					if(type != 0) {
						pd.indexChanges.add(new Change(Change.Op.REMOVE, index, type));
					}
				}
			}
			lastChangeTimestamp.put(controller, System.currentTimeMillis());
		}
	}

	/**
	 * Enqueue a full recalculation operation for the given controller.
	 * This allows recalculation to be deferred and batched with other pending changes.
	 */
	public static void enqueueRecalc(SegmentController controller) {
		if(controller == null) {
			return;
		}
		synchronized(pending) {
			PendingData pd = pending.get(controller);
			if(pd == null) {
				pd = new PendingData();
				pending.put(controller, pd);
			}
			// If already requested, move on but update timestamp (debounce)
			if(pd.recalcRequested) {
				lastChangeTimestamp.put(controller, System.currentTimeMillis());
				return;
			}
			// Mark recalc requested and prune any stored per-index changes (they are redundant)
			pd.recalcRequested = true;
			pd.indexChanges.clear();
			pd.typeDeltas.clear(); // optional: since recalc will rebuild from raw, drop type deltas to save work
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
		return true;
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
		// First, process any pending queued changes for this collection (if enough idle time passed)
		try {
			processPendingIfReady();
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Error processing pending armor changes", e);
		}

		if(currentHP < maxHP && maxHP > 0) {
			flagCollectionChanged = false; //This should prevent people from just placing blocks to get their HP back
			return;
		}

		if(System.currentTimeMillis() - lastUpdate >= UPDATE_FREQUENCY || flagCollectionChanged) {
			if(maxHP <= 0 && hasAnyArmorBlocks() && getSegmentController().isFullyLoadedWithDock()) {
				// enqueue a recalculation instead of performing it immediately so multiple triggers are batched
				enqueueRecalc(getSegmentController());
			}
			lastUpdate = System.currentTimeMillis();
			regenEnabled = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) > 1.0f;
		}
		if(System.currentTimeMillis() - lastRegen >= REGEN_FREQUENCY && regenEnabled && currentHP < maxHP) {
			lastRegen = System.currentTimeMillis();
			doRegen();
		}
	}

	private void processPendingIfReady() {
		SegmentController sc = getSegmentController();
		Long last = lastChangeTimestamp.get(sc);
		if(last == null) {
			return;
		}
		if(System.currentTimeMillis() - last < PROCESS_DELAY_MS) {
			return; // still waiting for more changes
		}

		PendingData pd;
		synchronized(pending) {
			pd = pending.remove(sc);
			lastChangeTimestamp.remove(sc);
		}
		if(pd == null) {
			return;
		}
		ArmorHPCollection collection = getCollection(sc);

		// Apply aggregated per-type deltas first
		for(short type : pd.typeDeltas.keySet()) {
			int delta = pd.typeDeltas.get(type);
			if(delta == 0) {
				continue;
			}
			setCount(type, getCount(type) + delta);
			if(currentHP < maxHP) {
				updateMaxOnly = true;
			}
			flagCollectionChanged = true;
			flagDirty();
		}

		// If we have any index-level changes, apply them (only for cases where rawCollection == null)
		if(!pd.indexChanges.isEmpty() && collection != null) {
			for(Change c : pd.indexChanges) {
				try {
					if(collection.rawCollection == null && c.type != 0) {
						if(c.op == Change.Op.ADD) {
							collection.doAdd(c.index, c.type);
						} else if(c.op == Change.Op.REMOVE) {
							collection.doRemove(c.index);
						}
					}
				} catch(Exception exception) {
					CombatTweaks.getInstance().logException("Failed to apply index-level change to entity " + collection.getSegmentController().getName() + " (" + collection.getSegmentController().getUniqueIdentifier() + ")", exception);
				}
			}
		}

		// If a recalc was requested for this batch, do it now (after add/remove)
		if(pd.recalcRequested) {
			try {
				recalcHP();
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("Failed to recalc armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", exception);
			}
		}
	}

	public void doRegen() {
		double regen = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) * maxHP;
		currentHP = Math.min(maxHP, currentHP + regen);
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
		return getSegmentController().getElementClassCountMap().get(type);
		/*if(!armorMap.containsKey(type)) {
			armorMap.put(type, 0);
		}
		return armorMap.get(type);*/
	}

	private void setCount(short type, int count) {
		if(count < 0) {
			CombatTweaks.getInstance().logWarning("Negative armor count detected, resetting to 0.\nThis is likely a sign of a deeper issue with the entity, and should not be ignored!\nEntity: " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")");
			count = 0;
		}
		armorMap.put(type, count);
	}

	private boolean hasAnyArmorBlocks() {
		Iterable<Short> types = armorMap.keySet();
		for(short type : types) {
			if(type != 0 && getCount(type) > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Recalculates the current and maximum HP (Hit Points) of the armor.
	 * <p>
	 * This method resets the current and maximum HP to zero, then iterates through all armor types
	 * to calculate the new HP values based on the armor's multiplier
	 * </p>
	 * <p>
	 * If the updateMaxOnly flag is set, only the maximum HP is updated. The method ensures that
	 * the current HP does not exceed the maximum HP and that neither value is negative.
	 * </p>
	 */
	private void recalcHP() {
		// Reset current and maximum HP to zero
		currentHP = 0;
		maxHP = 0;

		// Iterate through all armor types to calculate HP values
		Iterable<Short> types = armorMap.keySet();
		for(short type : types) {
			if(type != 0) {
				int count = getCount(type);
				if(count > 0) {
					if(!updateMaxOnly) {
						// Update current HP based on armor value and count
						currentHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorHPValueMultiplier) * count;
					}
					// Update maximum HP based on armor value and count
					maxHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorHPValueMultiplier) * count;
				} else {
					armorMap.put(type, 0); //clean up any negative counts
				}
			}
		}

		// Ensure current HP and maximum HP are not negative
		if(currentHP < 0) {
			currentHP = 0;
		}
		if(maxHP < 0) {
			maxHP = 0;
		}

		// Ensure current HP does not exceed maximum HP
		if(currentHP > maxHP) {
			currentHP = maxHP;
		}

		// Reset flags and update the last update time
		flagCollectionChanged = true;
		updateMaxOnly = false;
		lastUpdate = System.currentTimeMillis();
		flagDirty();
	}

	public double getCurrentHP() {
		return currentHP;
	}

	public void setCurrentHP(double hp) {
		double prev = currentHP;
		currentHP = max(0, Math.min(maxHP, hp));
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
		if(maxHP == 0 || currentHP == 0) {
			return 0;
		} else {
			return max(currentHP / maxHP, 0);
		}
	}

	/**
	 * Calculate reduced damage to armor based on AHP level and native armor protection.
	 *
	 * @param dmgIn                       The original damage to the impacted armor block
	 * @param dmgAfterBaseArmorProtection The remaining damage from the shot after standard armor calculations (without AHP)
	 * @return The resulting damage value after AHP protection is factored in
	 */
	public float processDamageToArmor(float dmgIn, float dmgAfterBaseArmorProtection) {
		// If there is no armor HP pool, all damage passes through
		if(maxHP <= 0 || currentHP <= 0) {
			return dmgAfterBaseArmorProtection;
		}

		// reference dmgIn to avoid unused-parameter warning (NaN check is harmless)
		if(Float.isNaN(dmgIn)) {
			return dmgAfterBaseArmorProtection;
		}

		double hpPercent = getHPPercent(); // 0..1
		double bleedThreshold = getBleedthroughThreshold();

		// If we're above the bleedthrough threshold, AHP fully absorbs damage (entity takes 0)
		if(hpPercent >= bleedThreshold) {
			// reduce AHP by the full post-armor damage amount (scaled by configured loss)
			double hpLoss = dmgAfterBaseArmorProtection * armorHPLostPerDamageAbsorbed;
			setCurrentHP(currentHP - hpLoss);
			return 0.0f;
		}

		// Below the threshold: a portion bleeds through and a portion is absorbed by AHP.
		// Bleed fraction increases as hpPercent goes from bleedThreshold -> 0
		double bleedFrac = (bleedThreshold - hpPercent) / (bleedThreshold <= 0 ? 1.0 : bleedThreshold);
		bleedFrac = max(0.0, Math.min(1.0, bleedFrac));

		// Desired damage absorbed by AHP (the rest bleeds through)
		double desiredAbsorbed = dmgAfterBaseArmorProtection * (1.0 - bleedFrac);

		// How much damage the current AHP can actually absorb
		double absorbCapacity = currentHP / armorHPLostPerDamageAbsorbed; // damage-per-AHP conversion

		double actualAbsorbed = Math.min(desiredAbsorbed, absorbCapacity);

		// Deduct HP based on actual absorbed damage
		double hpLoss = actualAbsorbed * armorHPLostPerDamageAbsorbed;
		setCurrentHP(currentHP - hpLoss);

		// Any damage not actually absorbed (either because it was intended to bleed or because AHP was exhausted) bleeds through
		double finalDamage = dmgAfterBaseArmorProtection - actualAbsorbed;
		return (float) max(0.0, finalDamage);
	}

	private double getBleedthroughThreshold() {
		return max(minArmorHPBleedthroughStart, getConfigManager().apply(StatusEffectType.ARMOR_HP_ABSORPTION, baseArmorHPBleedthroughStart));
	}

	// struct to hold aggregated pending changes per controller
	private static class PendingData {
		final Short2IntArrayMap typeDeltas = new Short2IntArrayMap(); // aggregated per-type net change
		final List<Change> indexChanges = new ArrayList<>(); // only used when we need per-index doAdd/doRemove calls
		boolean recalcRequested; // whether a full recalculation was requested
	}

	private static class Change {

		final Op op;
		final long index;
		final short type;

		Change(Op op, long index, short type) {
			this.op = op;
			this.index = index;
			this.type = type;
		}

		enum Op {
			ADD,
			REMOVE,
			RECALC
		}
	}
}
