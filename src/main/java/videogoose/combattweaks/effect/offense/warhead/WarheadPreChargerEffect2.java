package videogoose.combattweaks.effect.offense.warhead;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

public class WarheadPreChargerEffect2 extends ConfigEffectGroup {

	public WarheadPreChargerEffect2() {
		super("warhead_pre_charger_effect_2");
	}

	@Override
	public void createElements() {
		{ //Add Warhead Radius
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_RADIUS);
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 2;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect2RadiusAdd.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Multiply Warhead Damage
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_DAMAGE);
			configElement.stackType = ModifierStackType.MULT;
			configElement.priority = 2;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect2DamageMultiplier.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}

		{ //Add Warhead Volatility
			EffectConfigElement configElement = new EffectConfigElement();
			configElement.init(StatusEffectType.WARHEAD_CHANCE_FOR_EXPLOSION_ON_HIT);
			configElement.stackType = ModifierStackType.ADD;
			configElement.priority = 2;
			StatusEffectFloatValue value = new StatusEffectFloatValue();
			value.value.set(ConfigManager.getSystemConfig().warheadPreChargerEffect2VolatilityAdd.getValue().floatValue());
			configElement.value = value;
			elements.add(configElement);
		}
	}
}
