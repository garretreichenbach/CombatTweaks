package videogoose.combattweaks.effect.offense.aura;

import org.schema.game.common.data.blockeffects.config.EffectConfigElement;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.elements.ModifierStackType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.manager.ConfigManager;

/**
 * Offense Aura ECW effect (tier 1): multiplies the AI shooting "difficulty" (precision) of enemies inside the
 * sphere by a configured factor &lt;1, widening their shot deviation so AI-controlled turrets, drones and
 * point-defense miss more. Applied at runtime by {@code OffenseAuraAddOn} to hostile entities in range.
 *
 * <p>Deliberately an electronic-warfare effect, not a flat stat debuff: it only affects AI-controlled fire, so a
 * manually-piloted ship is immune. Combined with aura-power attrition under fire and the Aura Disruptor, this
 * gives multiple counters rather than a mandatory must-counter nerf (see the aura-balance-philosophy note).</p>
 */
public class TargetingJammerAuraEffect1 extends ConfigEffectGroup {

	public TargetingJammerAuraEffect1() {
		super("targeting_jammer_aura_1_effect");
	}

	@Override
	public void createElements() {
		float mult = ConfigManager.getSystemConfig().targetingJammerAuraAccuracyMult1.getValue().floatValue();
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
