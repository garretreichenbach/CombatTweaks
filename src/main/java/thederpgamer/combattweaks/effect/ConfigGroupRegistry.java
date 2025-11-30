package thederpgamer.combattweaks.effect;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import org.schema.game.common.data.blockeffects.config.ConfigGroup;
import thederpgamer.combattweaks.effect.defense.armor.ArmorHPAbsorptionEffect1;

public enum ConfigGroupRegistry {
	ARMOR_HP_ABSORPTION_1(new ArmorHPAbsorptionEffect1()),;

	public final ConfigEffectGroup configEffectGroup;

	ConfigGroupRegistry(ConfigEffectGroup configEffectGroup) {
		this.configEffectGroup = configEffectGroup;
	}

	@Override
	public String toString() {
		return configEffectGroup.id;
	}

	public static void registerEffects(ObjectArrayFIFOQueue<ConfigGroup> configGroups) {
		for(ConfigGroupRegistry registry : values()) {
			registry.configEffectGroup.createElements();
			configGroups.enqueue(registry.configEffectGroup);
		}
	}
}