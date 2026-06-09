package videogoose.combattweaks.effect.support.aura;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.effect.StatusEffectRegistry;
import videogoose.combattweaks.manager.ConfigManager;

public class AuraRangeBoostEffect1 extends ConfigEffectGroup {

	public AuraRangeBoostEffect1() {
		super("aura_range_boost_effect_1");
	}

	@Override
	public void createElements() {
		{ //Add Range
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectRegistry.AURA_RANGE);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().auraRangeBoostEffect1Add.value.floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
