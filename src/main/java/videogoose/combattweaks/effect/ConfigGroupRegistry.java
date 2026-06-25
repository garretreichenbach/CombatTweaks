package videogoose.combattweaks.effect;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.schema.game.common.data.blockeffects.config.ConfigGroup;
import videogoose.combattweaks.effect.defense.armor.ArmorHPAbsorptionEffect1;
import videogoose.combattweaks.effect.defense.armor.ArmorHPAbsorptionEffect2;
import videogoose.combattweaks.effect.offense.aura.ShieldDampenAuraEffect;
import videogoose.combattweaks.effect.offense.aura.WeaponRangeDampenAuraEffect;
import videogoose.combattweaks.effect.offense.warhead.WarheadPreChargerEffect1;
import videogoose.combattweaks.effect.offense.warhead.WarheadPreChargerEffect2;
import videogoose.combattweaks.effect.support.aura.AuraBaseEffect;
import videogoose.combattweaks.effect.support.aura.AuraRangeBoostEffect1;
import videogoose.combattweaks.effect.support.aura.AuraRangeBoostEffect2;
import videogoose.combattweaks.effect.support.shield.ShieldAuraCapacityEffect1;
import videogoose.combattweaks.effect.support.shield.ShieldAuraCapacityEffect2;

public enum ConfigGroupRegistry {
	//Defense
	ARMOR_HP_ABSORPTION_1(new ArmorHPAbsorptionEffect1()),
	ARMOR_HP_ABSORPTION_2(new ArmorHPAbsorptionEffect2()),

	//Aura (ported from BetterChambers)
	AURA_BASE_EFFECT(new AuraBaseEffect()),
	AURA_RANGE_BOOST_EFFECT_1(new AuraRangeBoostEffect1()),
	AURA_RANGE_BOOST_EFFECT_2(new AuraRangeBoostEffect2()),

	// Applied at runtime by the Support Aura to friendly ships inside the sphere (no passive chamber references these).
	SHIELD_AURA_CAPACITY_EFFECT_1(new ShieldAuraCapacityEffect1()),
	SHIELD_AURA_CAPACITY_EFFECT_2(new ShieldAuraCapacityEffect2()),

	// Applied at runtime by the Offense Aura to enemy ships inside the sphere.
	SHIELD_DAMPEN_AURA_EFFECT(new ShieldDampenAuraEffect()),
	WEAPON_RANGE_DAMPEN_AURA_EFFECT(new WeaponRangeDampenAuraEffect()),

	//Offense — Warhead Pre-Charger (ported from BetterChambers)
	WARHEAD_PRE_CHARGER_EFFECT_1(new WarheadPreChargerEffect1()),
	WARHEAD_PRE_CHARGER_EFFECT_2(new WarheadPreChargerEffect2());

	public final ConfigEffectGroup configEffectGroup;

	ConfigGroupRegistry(ConfigEffectGroup configEffectGroup) {
		this.configEffectGroup = configEffectGroup;
	}

	@Override
	public String toString() {
		return configEffectGroup.id;
	}

	public static void registerEffects(ObjectArrayFIFOQueue<ConfigGroup> configGroups) {
		// Ensure custom status-effect types (e.g. AURA_RANGE) are registered before any effect references them.
		StatusEffectRegistry.touch();
		for(ConfigGroupRegistry registry : values()) {
			registry.configEffectGroup.createElements();
			configGroups.enqueue(registry.configEffectGroup);
		}
	}
}
