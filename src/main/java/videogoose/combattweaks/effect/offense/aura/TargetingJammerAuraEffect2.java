package videogoose.combattweaks.effect.offense.aura;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

/**
 * Offense Aura ECW effect (tier 2): a stronger version of {@link TargetingJammerAuraEffect1}. Multiplies enemy AI
 * shooting precision by a lower factor, so AI turrets/drones/point-defense scatter their shots even more. Applied
 * at runtime by {@code OffenseAuraAddOn} to hostile entities in range.
 */
public class TargetingJammerAuraEffect2 extends ConfigEffectGroup {

	public TargetingJammerAuraEffect2() {
		super("targeting_jammer_aura_2_effect");
	}

	@Override
	public void createElements() {
		float mult = ConfigManager.getSystemConfig().targetingJammerAuraAccuracyMult2.getValue().floatValue();
		addAccuracyMult(StatusEffectType.AI_ACCURACY_TURRET, mult);
		addAccuracyMult(StatusEffectType.AI_ACCURACY_DRONE, mult);
		addAccuracyMult(StatusEffectType.AI_ACCURACY_POINT_DEFENSE, mult);
	}

	private void addAccuracyMult(StatusEffectType type, float mult) {
		EffectConfigElement configElement = new EffectConfigElement();
		configElement.init(type);
		configElement.weaponType = null;
		configElement.stackType = ModifierStackType.MULT;
		configElement.priority = 1;
		StatusEffectFloatValue value = new StatusEffectFloatValue();
		value.value.set(mult);
		configElement.value = value;
		elements.add(configElement);
	}
}
