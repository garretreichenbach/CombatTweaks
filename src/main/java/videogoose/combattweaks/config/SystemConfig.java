package videogoose.combattweaks.config;

import api.mod.StarMod;
import api.utils.simpleconfig.SimpleConfigContainer;
import api.utils.simpleconfig.SimpleConfigDouble;

public class SystemConfig extends SimpleConfigContainer {

	public final SimpleConfigDouble armorHpValueMultiplier =
			new SimpleConfigDouble(this, "armor_hp_value_multiplier", 20.0, "Base multiplier applied to raw armor value totals");

	public final SimpleConfigDouble armorHpScalingExponent =
			new SimpleConfigDouble(this, "armor_hp_scaling_exponent", 0.75, "Sub-linear scaling exponent (1.0 = linear, lower = more diminishing returns for small ships)");

	public final SimpleConfigDouble armorHpLostPerDamageAbsorbed =
			new SimpleConfigDouble(this, "armor_hp_lost_per_damage_absorbed", 1.0, "Armor HP consumed per point of damage absorbed");

	public final SimpleConfigDouble baseArmorHpBleedThroughStart =
			new SimpleConfigDouble(this, "base_armor_hp_bleed_through_start", 0.7, "Controls bleed-through ramp rate (higher = bleed reaches 100% sooner)");

	public final SimpleConfigDouble armorHpAbsorptionEffect1Sub =
			new SimpleConfigDouble(this, "armor_hp_absorption_effect_1_sub", -7.5, "Chamber 1 bleed-through reduction");

	public final SimpleConfigDouble armorHpAbsorptionEffect2Sub =
			new SimpleConfigDouble(this, "armor_hp_absorption_effect_2_sub", -7.5, "Chamber 2 bleed-through reduction");

	public SystemConfig(StarMod mod) {
		super(mod, "system_config", false);
	}
}
