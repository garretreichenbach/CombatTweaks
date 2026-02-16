package thederpgamer.combattweaks.effect.defense.armor;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import thederpgamer.combattweaks.effect.ConfigEffectGroup;
import thederpgamer.combattweaks.manager.ConfigManager;

public class ArmorHPAbsorptionEffect2 extends ConfigEffectGroup {

	public ArmorHPAbsorptionEffect2() {
		super("armor_hp_absorption_effect_2");
	}

	@Override
	public void createElements() {
		{ //Add Range
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.ARMOR_HP_ABSORPTION);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 2;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set((float) ConfigManager.getSystemConfig().getDouble("armor_hp_absorption_effect_2_sub"));
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
