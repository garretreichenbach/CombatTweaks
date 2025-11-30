package thederpgamer.combattweaks.system.armor;

import api.common.GameClient;
import api.utils.game.SegmentControllerUtils;
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

import java.util.*;

/**
 * CollectionManager for ArmorHP.
 * <p>We rely on the game's element count map. Add/remove events only schedule a debounced recalculation.</p>
 */
public class ArmorHPCollection extends ElementCollectionManager<ArmorHPUnit, ArmorHPCollection, VoidElementManager<ArmorHPUnit, ArmorHPCollection>> {

	private static final long REGEN_FREQUENCY = 1000;
	// Delay after the last enqueued change before processing the batch (ms)
	private static final long PROCESS_DELAY_MS = 250; // arbitrary time since last update
	// Pending recalculation requests keyed by SegmentController. WeakHashMap so controllers can be GC'd when gone.
	// Use a synchronized LinkedList as a queue for pending requests and a Set to avoid duplicates.
	// Note: queued controllers are held strongly while present in the queue.
	private static final LinkedList<Pending> pending = new LinkedList<>();
	private static final Set<SegmentController> pendingSet = Collections.synchronizedSet(new HashSet<SegmentController>());
	// Track last change time per controller. Keep WeakHashMap so controllers can be GC'd when no longer referenced.
	private static final Map<SegmentController, Long> lastChangeTimestamp = Collections.synchronizedMap(new WeakHashMap<SegmentController, Long>());
	private final double armorHPValueMultiplier;
	private final double armorHPLostPerDamageAbsorbed;
	private final double baseArmorHPBleedThroughStart;
	private double currentHP;
	private double maxHP;
	private boolean flagCollectionChanged = true;
	private boolean updateMaxOnly;
	private long lastRegen;
	private boolean regenEnabled;
	public ArmorHPCollection(SegmentController segmentController, VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager) {
		super(ElementKeyMap.CORE_ID, segmentController, armorHPManager);
		armorHPValueMultiplier = ConfigManager.getSystemConfig().getDouble("armor_hp_value_multiplier");
		armorHPLostPerDamageAbsorbed = ConfigManager.getSystemConfig().getDouble("armor_hp_lost_per_damage_absorbed");
		baseArmorHPBleedThroughStart = ConfigManager.getSystemConfig().getDouble("base_armor_hp_bleed_through_start");
	}

	public static ArmorHPCollection getCollection(SegmentController controller) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			try {
				ManagedUsableSegmentController<?> managed = (ManagedUsableSegmentController<?>) controller;
				ArrayList<ElementCollectionManager<?, ?, ?>> cms = SegmentControllerUtils.getCollectionManagers(managed, ArmorHPCollection.class);
				for(ElementCollectionManager<?, ?, ?> cm : cms) {
					if(cm instanceof ArmorHPCollection) {
						return (ArmorHPCollection) cm;
					}
				}
			} catch(Exception exception) {
				CombatTweaks.getInstance().logException("Error getting ArmorHPCollection for entity " + controller.getName() + " (" + controller.getUniqueIdentifier() + ")", exception);
			}
		}
		return null;
	}

	/**
	 * Enqueue a full recalculation operation for the given controller.
	 */
	public static void enqueueRecalc(SegmentController controller, boolean fullRecalc) {
		if(controller == null) {
			return;
		}
		synchronized(pending) {
			// avoid duplicate entries
			if(!pendingSet.contains(controller)) {
				pending.add(new Pending(controller, false));
				pendingSet.add(controller);
			}
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
		return flagCollectionChanged;
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
			return;
		}

		regenEnabled = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) > 1.0f;

		if(maxHP <= 0 && hasAnyArmorBlocks() && getSegmentController().isFullyLoadedWithDock() && flagCollectionChanged) {
			lastUpdate = System.currentTimeMillis();
			enqueueRecalc(getSegmentController());
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
			return; // debounce
		}

		Pending pd = null;
		synchronized(pending) {
			Iterator<Pending> it = pending.iterator();
			while(it.hasNext()) {
				pd = it.next();
				if(pd.controller == sc) {
					it.remove();
					pendingSet.remove(sc);
					break;
				}
			}
		}
		if(pd == null) {
			return;
		}

		// If a recalc was requested, just do it. We no longer apply per-index adds/removes here.
		if(pd.fullRecalc) {
			try {
				if(currentHP < maxHP) {
					updateMaxOnly = true;
				}
				recalcHP();
			} catch(Exception e) {
				CombatTweaks.getInstance().logException("Failed to recalc armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", e);
			}
		}
	}

	public void doRegen() {
		if(regenEnabled) {
			double regen = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) * maxHP;
			currentHP = Math.min(maxHP, currentHP + regen);
		}
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
		try {
			return getSegmentController().getElementClassCountMap().get(type);
		} catch(Throwable t) {
			return 0;
		}
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
		currentHP = 0;
		maxHP = 0;
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && info.isArmor()) {
				short type = info.getId();
				int count = getCount(type);
				if(count > 0) {
					if(!updateMaxOnly) {
						currentHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorHPValueMultiplier) * count;
					}
					maxHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorHPValueMultiplier) * count;
				}
			}
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
		if(hpPercent >= bleedThreshold) {
			double hpLoss = dmgAfterBaseArmorProtection * armorHPLostPerDamageAbsorbed;
			setCurrentHP(currentHP - hpLoss);
			return 0.0f;
		}
		double bleedFrac = (bleedThreshold - hpPercent) / (bleedThreshold <= 0 ? 1.0 : bleedThreshold);
		bleedFrac = Math.max(0.0, Math.min(1.0, bleedFrac));
		double desiredAbsorbed = dmgAfterBaseArmorProtection * (1.0 - bleedFrac);
		double absorbCapacity = currentHP / armorHPLostPerDamageAbsorbed;
		double actualAbsorbed = Math.min(desiredAbsorbed, absorbCapacity);
		double hpLoss = actualAbsorbed * armorHPLostPerDamageAbsorbed;
		setCurrentHP(currentHP - hpLoss);
		double finalDamage = dmgAfterBaseArmorProtection - actualAbsorbed;
		return (float) Math.max(0.0, finalDamage);
	}

	private double getBleedThroughThreshold() {
		return Math.max(Math.min(getConfigManager().apply(StatusEffectType.ARMOR_HP_ABSORPTION, 1.0f), baseArmorHPBleedThroughStart), 0.0f);
	}

	// Small helper class representing a queued pending request and whether it requested a full recalculation.
	private static class Pending {
		final SegmentController controller;
		boolean fullRecalc;

		Pending(SegmentController controller, boolean fullRecalc) {
			this.controller = controller;
			this.fullRecalc = fullRecalc;
		}
	}
}
