package videogoose.combattweaks.effect.support.shield;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

public class ShieldAuraCapacityEffect2 extends ConfigEffectGroup {

	public ShieldAuraCapacityEffect2() {
		super("shield_aura_capacity_2_effect");
	}

	@Override
	public void createElements() {
		{ //Add Capacity
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.SHIELD_CAPACITY);
			configElement.weaponType = null;
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 2;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().shieldCapacityEffect2AuraAdd.value.floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
