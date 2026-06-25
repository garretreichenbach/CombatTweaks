package videogoose.combattweaks.effect.offense.aura;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

/**
 * Offensive (debuff) aura effect: multiplies the weapon range of enemies inside an Offense Aura sphere by a
 * configured factor (&lt;1 reduces it). Applied at runtime by {@code OffenseAuraAddOn} to hostile ships in range.
 */
public class WeaponRangeDampenAuraEffect extends ConfigEffectGroup {

	public WeaponRangeDampenAuraEffect() {
		super("weapon_range_dampen_aura_effect");
	}

	@Override
	public void createElements() {
		EffectConfigElement configElement = new EffectConfigElement();
		configElement.init(StatusEffectType.WEAPON_RANGE);
		configElement.weaponType = null;
		configElement.stackType = ModifierStackType.MULT;
		configElement.priority = 1;
		StatusEffectFloatValue value = new StatusEffectFloatValue();
		value.value.set(ConfigManager.getSystemConfig().weaponRangeDampenAuraMult.getValue().floatValue());
		configElement.value = value;
		elements.add(configElement);
	}
}
