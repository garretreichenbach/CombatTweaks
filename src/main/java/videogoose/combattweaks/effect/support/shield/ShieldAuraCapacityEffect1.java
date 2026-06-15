package videogoose.combattweaks.effect.support.shield;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

public class ShieldAuraCapacityEffect1 extends ConfigEffectGroup {

	public ShieldAuraCapacityEffect1() {
		super("shield_aura_capacity_1_effect");
	}

	@Override
	public void createElements() {
		{ //Add Capacity
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.SHIELD_CAPACITY);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().shieldCapacityEffect1AuraAdd.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
