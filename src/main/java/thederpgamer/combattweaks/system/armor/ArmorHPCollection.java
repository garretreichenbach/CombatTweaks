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

import java.util.ArrayList;

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
		if(currentHP < maxHP && maxHP > 0) {
			flagCollectionChanged = false; //This should prevent people from just placing blocks to get their HP back
			return;
		}
		if((flagCollectionChanged || (maxHP <= 0 && hasAnyArmorBlocks())) && getSegmentController().isFullyLoadedWithDock()) {
			recalcHP();
		}
		if(System.currentTimeMillis() - lastUpdate >= UPDATE_FREQUENCY) {
			lastUpdate = System.currentTimeMillis();
			regenEnabled = getSegmentController().getConfigManager().apply(StatusEffectType.ARMOR_HP_REGENERATION, 1.0f) > 1.0f;
		}
		if(System.currentTimeMillis() - lastRegen >= REGEN_FREQUENCY && regenEnabled && currentHP < maxHP) {
			lastRegen = System.currentTimeMillis();
			doRegen();
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
		if(!armorMap.containsKey(type)) {
			armorMap.put(type, 0);
		}
		return armorMap.get(type);
	}

	private void setCount(short type, int count) {
		if(count < 0) {
			CombatTweaks.getInstance().logWarning("Negative armor count detected, resetting to 0.\nThis is likely a sign of a deeper issue with the entity, and should not be ignored!\nEntity: " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")");
			count = 0;
		}
		armorMap.put(type, count);
	}

	private boolean hasAnyArmorBlocks() {
		for(short type : armorMap.keySet()) {
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
	public void recalcHP() {
		// Reset current and maximum HP to zero
		currentHP = 0;
		maxHP = 0;

		// Get the armor multiplier
		float armorMult = ConfigManager.getSystemConfig().getConfigurableFloat("armor_hp_value_multiplier", 20.0f);

		// Iterate through all armor types to calculate HP values
		for(short type : armorMap.keySet()) {
			if(type != 0) {
				int count = getCount(type);
				if(count > 0) {
					if(!updateMaxOnly) {
						// Update current HP based on armor value and count
						currentHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorMult) * count;
					}
					// Update maximum HP based on armor value and count
					maxHP += (ElementKeyMap.getInfo(type).getArmorValue() * armorMult) * count;
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
		flagCollectionChanged = false;
		updateMaxOnly = false;
		lastUpdate = System.currentTimeMillis();
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

	public void addBlock(long index, short type) {
		setCount(type, getCount(type) + 1);
		if(currentHP < maxHP) {
			updateMaxOnly = true;
		}
		flagCollectionChanged = true;
		try {
			if(rawCollection == null && type != 0) {
				doAdd(index, type);
			}
		} catch(Exception exception) { //I know this is terrible exception handling, but this whole process is shit and can randomly break, so it's better than crashing the game
			CombatTweaks.getInstance().logException("Failed to add block of type " + type + " to entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", exception);
		}
	}

	public void removeBlock(long index, short type) {
		setCount(type, getCount(type) - 1);
		flagCollectionChanged = true;
		try {
			if(rawCollection == null && type != 0) {
				doRemove(index);
			}
		} catch(Exception exception) { //I know this is terrible exception handling, but this whole process is shit and can randomly break, so it's better than crashing the game
			CombatTweaks.getInstance().logException("Failed to remove block of type " + type + " to entity " + getSegmentController().getName() + " (" + getSegmentController().getUniqueIdentifier() + ")", exception);
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

		// config: how much AHP is lost per 1 point of damage absorbed
		float hpLostPerDamage = ConfigManager.getSystemConfig().getConfigurableFloat("armor_hp_lost_per_damage_absorbed", 1.0f);

		// If we're above the bleedthrough threshold, AHP fully absorbs damage (entity takes 0)
		if(hpPercent >= bleedThreshold) {
			// reduce AHP by the full post-armor damage amount (scaled by configured loss)
			double hpLoss = dmgAfterBaseArmorProtection * hpLostPerDamage;
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
		double absorbCapacity = currentHP / hpLostPerDamage; // damage-per-AHP conversion
		double actualAbsorbed = Math.min(desiredAbsorbed, absorbCapacity);

		// Deduct HP based on actual absorbed damage
		double hpLoss = actualAbsorbed * hpLostPerDamage;
		setCurrentHP(currentHP - hpLoss);

		// Any damage not actually absorbed (either because it was intended to bleed or because AHP was exhausted) bleeds through
		double finalDamage = dmgAfterBaseArmorProtection - actualAbsorbed;
		return (float) max(0.0, finalDamage);
	}

	private double getBleedthroughThreshold() {
		return max(ConfigManager.getSystemConfig().getConfigurableFloat("min_armor_hp_bleedthrough_start", 0.5f), getConfigManager().apply(StatusEffectType.ARMOR_HP_ABSORPTION, ConfigManager.getSystemConfig().getConfigurableFloat("base_armor_hp_bleedthrough_start", 0.75f)));
	}
}
