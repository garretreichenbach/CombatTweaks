package videogoose.combattweaks.effect;

import org.schema.game.common.data.blockeffects.config.StatusEffectCategory;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.blockeffects.config.parameter.StatusEffectFloatValue;
import videogoose.combattweaks.CombatTweaks;

/**
 * Registers the mod's custom status-effect types. {@link #AURA_RANGE} carries the projected aura radius (as a
 * fraction of sector size) on a ship's reactor config; the Aura Projector reads the summed value to size its
 * bounding sphere. {@link #touch()} forces class-load so the type is registered before effects reference it.
 */
public class StatusEffectRegistry {

	//Categories
	public static final StatusEffectCategory AURA = StatusEffectCategory.registerEffectCategory(
			CombatTweaks.getInstance(), "Aura");

	//Types
	public static final StatusEffectType AURA_RANGE = StatusEffectType.registerEffectType(
			CombatTweaks.getInstance(), "Aura Range", AURA, false, false,
			StatusEffectFloatValue.class);

	/**
	 * No-op that guarantees this class (and therefore its static effect-type registrations) is initialized.
	 * Called during config-group registration before any effect references {@link #AURA_RANGE}.
	 */
	public static void touch() {
	}
}
