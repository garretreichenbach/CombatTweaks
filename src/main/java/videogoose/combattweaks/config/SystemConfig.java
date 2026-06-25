package videogoose.combattweaks.config;

import api.mod.StarMod;
import api.utils.simpleconfig.SimpleConfigContainer;
import api.utils.simpleconfig.SimpleConfigDouble;

public class SystemConfig extends SimpleConfigContainer {

	public final SimpleConfigDouble armorHpValueMultiplier =
			new SimpleConfigDouble(this, "armor_hp_value_multiplier", 1000.0, "Base multiplier applied to raw armor value totals");

	public final SimpleConfigDouble armorHpScalingExponent =
			new SimpleConfigDouble(this, "armor_hp_scaling_exponent", 0.75, "Sub-linear scaling exponent (1.0 = linear, lower = more diminishing returns for small ships)");

	public final SimpleConfigDouble armorHpLostPerDamageAbsorbed =
			new SimpleConfigDouble(this, "armor_hp_lost_per_damage_absorbed", 1.0, "Armor HP consumed per point of damage absorbed");

	public final SimpleConfigDouble baseArmorHpBleedThroughStart =
			new SimpleConfigDouble(this, "base_armor_hp_bleed_through_start", 0.9, "Controls bleed-through ramp rate (higher = bleed reaches 100% sooner)");

	public final SimpleConfigDouble armorHpAbsorptionEffect1Sub =
			new SimpleConfigDouble(this, "armor_hp_absorption_effect_1_sub", -7.5, "Chamber 1 bleed-through reduction");

	public final SimpleConfigDouble armorHpAbsorptionEffect2Sub =
			new SimpleConfigDouble(this, "armor_hp_absorption_effect_2_sub", -7.5, "Chamber 2 bleed-through reduction");

	public final SimpleConfigDouble armorHpRepairFractionPerSecond =
			new SimpleConfigDouble(this, "armor_hp_repair_fraction_per_second", 0.05, "Fraction of a repair target's max Armor HP restored per second by an active repair beam (0.05 = 5%/s, ~20s from empty to full).");

	public final SimpleConfigDouble auraBaseChamberRangeSet =
			new SimpleConfigDouble(this, "aura_base_chamber_range_set", 0.2, "Base aura range set by the Aura Projector chamber, as a fraction of sector size.");

	public final SimpleConfigDouble auraRangeBoostEffect1Add =
			new SimpleConfigDouble(this, "aura_range_boost_effect_1_add", 0.2, "Aura Range Boost chamber tier 1: range added, as a fraction of sector size.");

	public final SimpleConfigDouble auraRangeBoostEffect2Add =
			new SimpleConfigDouble(this, "aura_range_boost_effect_2_add", 0.2, "Aura Range Boost chamber tier 2: range added, as a fraction of sector size.");

	public final SimpleConfigDouble shieldCapacityEffect1AuraAdd =
			new SimpleConfigDouble(this, "shield_capacity_effect_1_aura_add", 1.2, "Shield Aura Capacity tier 1: shield capacity bonus applied to affected ships.");

	public final SimpleConfigDouble shieldCapacityEffect2AuraAdd =
			new SimpleConfigDouble(this, "shield_capacity_effect_2_aura_add", 1.2, "Shield Aura Capacity tier 2: shield capacity bonus applied to affected ships.");

	// --- Aura runtime (Aura Projector + Disruptor) ---
	public final SimpleConfigDouble auraDisruptorPowerMultiplier =
			new SimpleConfigDouble(this, "aura_disruptor_power_multiplier", 1.5, "Multiplier applied to Aura Disruptor beam power when draining a projector's aura power.");

	public final SimpleConfigDouble auraRegenPercentPerUpdate =
			new SimpleConfigDouble(this, "aura_regen_percent_per_update", 0.05, "Fraction of current aura power regenerated each projector update tick.");

	public final SimpleConfigDouble auraMinSizePercent =
			new SimpleConfigDouble(this, "aura_min_size_percent", 0.15, "Minimum projector/target reactor-level ratio for an aura to affect a ship (smaller ships can't aura much larger ones).");

	public final SimpleConfigDouble auraDisruptorBeamPowerPerUnit =
			new SimpleConfigDouble(this, "aura_disruptor_beam_power_per_unit", 1.0, "Aura Disruptor beam power contributed per module block (multiplied into the aura-power drain on hit).");

	public final SimpleConfigDouble auraDisruptorBeamPowerConsumptionPerUnit =
			new SimpleConfigDouble(this, "aura_disruptor_beam_power_consumption_per_unit", 12.0, "Reactor power consumed per Aura Disruptor module block while firing.");

	public final SimpleConfigDouble auraDamageAttritionFactor =
			new SimpleConfigDouble(this, "aura_damage_attrition_factor", 0.05, "Fraction of incoming damage that bleeds an active projector's aura power when its ship is hit (the second takedown path alongside the Aura Disruptor). 0 disables damage attrition.");

	// --- Offense Aura ECW (Targeting Jammer; applied to ENEMY ships inside the sphere) ---
	// Multiplier on enemy AI shooting precision (AI_ACCURACY_TURRET/DRONE/POINT_DEFENSE). <1 widens their shot
	// deviation so AI-controlled fire misses more; only affects AI shooters, not manually-piloted ships.
	public final SimpleConfigDouble targetingJammerAuraAccuracyMult1 =
			new SimpleConfigDouble(this, "targeting_jammer_aura_accuracy_mult_1", 0.6, "Targeting Jammer aura tier 1: multiplier on affected enemies' AI targeting precision (0.6 = ~40% worse AI accuracy). Lower = more scatter.");

	public final SimpleConfigDouble targetingJammerAuraAccuracyMult2 =
			new SimpleConfigDouble(this, "targeting_jammer_aura_accuracy_mult_2", 0.4, "Targeting Jammer aura tier 2: multiplier on affected enemies' AI targeting precision (0.4 = ~60% worse AI accuracy). Lower = more scatter.");

	// --- Warhead Pre-Charger chambers (ported from BetterChambers) ---
	public final SimpleConfigDouble warheadPreChargerEffect1RadiusAdd =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_1_radius_add", 5.0, "Warhead Pre-Charger tier 1: warhead radius added.");

	public final SimpleConfigDouble warheadPreChargerEffect1DamageMultiplier =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_1_damage_multiplier", 5.0, "Warhead Pre-Charger tier 1: warhead damage multiplier.");

	public final SimpleConfigDouble warheadPreChargerEffect1VolatilityAdd =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_1_volatility_add", 0.3, "Warhead Pre-Charger tier 1: added chance for explosion on hit (volatility).");

	public final SimpleConfigDouble warheadPreChargerEffect2RadiusAdd =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_2_radius_add", 7.5, "Warhead Pre-Charger tier 2: warhead radius added.");

	public final SimpleConfigDouble warheadPreChargerEffect2DamageMultiplier =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_2_damage_multiplier", 7.5, "Warhead Pre-Charger tier 2: warhead damage multiplier.");

	public final SimpleConfigDouble warheadPreChargerEffect2VolatilityAdd =
			new SimpleConfigDouble(this, "warhead_pre_charger_effect_2_volatility_add", 0.5, "Warhead Pre-Charger tier 2: added chance for explosion on hit (volatility).");

	public SystemConfig(StarMod mod) {
		super(mod, "system_config", false);
	}
}
