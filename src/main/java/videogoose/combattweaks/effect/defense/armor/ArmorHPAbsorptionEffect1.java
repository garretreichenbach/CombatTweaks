package videogoose.combattweaks.effect.defense.armor;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

public class ArmorHPAbsorptionEffect1 extends ConfigEffectGroup {

	public ArmorHPAbsorptionEffect1() {
		super("armor_hp_absorption_effect_1");
	}

	@Override
	public void createElements() {
		{ //Add Range
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.ARMOR_HP_ABSORPTION);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().armorHpAbsorptionEffect1Sub.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
