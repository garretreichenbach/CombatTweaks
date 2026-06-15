package videogoose.combattweaks.effect.offense.warhead;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

public class WarheadPreChargerEffect1 extends ConfigEffectGroup {

	public WarheadPreChargerEffect1() {
		super("warhead_pre_charger_effect_1");
	}

	@Override
	public void createElements() {
		{ //Set Base Warhead Radius
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_RADIUS);
			configElement.stackType = ModifierStackType.SET;
			configElement.priority = 0;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(1);
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Set Base Warhead Damage
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_DAMAGE);
			configElement.stackType = ModifierStackType.SET;
			configElement.priority = 0;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(10.0f);
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Set Base Warhead Volatility
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_CHANCE_FOR_EXPLOSION_ON_HIT);
			configElement.stackType = ModifierStackType.SET;
			configElement.priority = 0;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(0.1f);
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Add Warhead Radius
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_RADIUS);
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect1RadiusAdd.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Multiply Warhead Damage
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_DAMAGE);
			configElement.stackType = ModifierStackType.MULT;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect1DamageMultiplier.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Add Warhead Volatility
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_CHANCE_FOR_EXPLOSION_ON_HIT);
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 1;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect1VolatilityAdd.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
