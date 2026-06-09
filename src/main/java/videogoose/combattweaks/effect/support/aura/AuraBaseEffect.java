package videogoose.combattweaks.effect.support.aura;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.effect.StatusEffectRegistry;
import videogoose.combattweaks.manager.ConfigManager;

public class AuraBaseEffect extends ConfigEffectGroup {

	public AuraBaseEffect() {
		super("aura_base_effect");
	}

	@Override
	public void createElements() {
		{ //Set Range
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectRegistry.AURA_RANGE);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.SET;
			configElement.priority = 0;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().auraBaseChamberRangeSet.value.floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
