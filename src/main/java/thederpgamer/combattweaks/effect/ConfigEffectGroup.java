package thederpgamer.combattweaks.effect;

import org.schema.game.common.data.blockeffects.config.ConfigGroup;

public abstract class ConfigEffectGroup extends ConfigGroup {

	protected ConfigEffectGroup(String effectIdentifier) {
		super(effectIdentifier);
	}

	public abstract void createElements();
}
