package thederpgamer.combattweaks.system.armor;

import api.common.GameClient;
import org.schema.common.util.StringTools;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.GUIKeyValueEntry;
import org.schema.game.client.view.gui.structurecontrol.ModuleValueEntry;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.ManagerModule;
import org.schema.game.common.controller.elements.ManagerModuleCollection;
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

	private static final long UPDATE_FREQUENCY = 1000;
	private static final long REGEN_FREQUENCY = 1000;
	// Delay after the last enqueued change before processing the batch (ms)
	private static final long PROCESS_DELAY_MS = 500; // arbitrary time since last update
	private static final Set<SegmentController> pending = Collections.synchronizedSet(new HashSet<SegmentController>());
	// Track last change time per controller. Using WeakHashMap so controllers can be GC'd when no longer referenced.
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
				for(ManagerModule<?, ?, ?> module : managed.getManagerContainer().getModules()) {
					if(module instanceof ManagerModuleCollection) {
						for(Object cm : ((ManagerModuleCollection<?,?,?>) module).getCollectionManagers()) {
							if(cm instanceof ArmorHPCollection) {
								return (ArmorHPCollection) cm;
							}
						}
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
	public static void enqueueRecalc(SegmentController controller) {
		if(controller == null) {
			return;
		}
		synchronized(pending) {
			pending.add(controller);
			lastChangeTimestamp.put(controller, System.currentTimeMillis());
		}
		CombatTweaks.getInstance().logInfo("Enqueued armor HP recalculation for entity " + controller.getName() + " (" + controller.getUniqueIdentifier() + ")");
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

		if(System.currentTimeMillis() - lastUpdate > UPDATE_FREQUENCY && maxHP <= 0 && hasAnyArmorBlocks() && getSegmentController().isFullyLoadedWithDock() && flagCollectionChanged) {
			lastUpdate = System.currentTimeMillis();
			enqueueRecalc(getSegmentController());
		}

		regenEnabled = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) > 1.0f;
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
		try {
			if(currentHP < maxHP) {
				updateMaxOnly = true;
			}
			recalcHP();
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("Failed to recalc armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", e);
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
		} catch(Exception exception) {
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
		long start = System.currentTimeMillis();
		currentHP = 0;
		maxHP = 0;
		ElementInformation[] infos = ElementKeyMap.getInfoArray();
		for(ElementInformation info : infos) {
			if(info != null && !info.isDeprecated() && info.isArmor()) {
				short type = info.getId();
				int count = getCount(type);
				if(count > 0) {
					if(!updateMaxOnly) {
						currentHP += (info.getArmorValue() * armorHPValueMultiplier) * count;
					}
					maxHP += (info.getArmorValue() * armorHPValueMultiplier) * count;
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

		if(getSegmentController().railController.isRoot()) { //We only log for the root controller to avoid log spam
			CombatTweaks.getInstance().logInfo("Recalculated armor HP for entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ") in " + (System.currentTimeMillis() - start) + " ms. Current HP: " + currentHP + ", Max HP: " + maxHP);
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
}
