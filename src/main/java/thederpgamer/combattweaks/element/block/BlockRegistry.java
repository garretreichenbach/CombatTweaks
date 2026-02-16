package thederpgamer.combattweaks.element.block;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import thederpgamer.combattweaks.element.block.chamber.defense.ArmorHPAbsorptionChamber1;
import thederpgamer.combattweaks.element.block.chamber.defense.ArmorHPAbsorptionChamber2;

/**
 * Central registry that defines and registers all mod blocks/chambers/weapons.
 */
public enum BlockRegistry {
	//Chambers
	ARMOR_HP_ABSORPTION_CHAMBER_1(new ArmorHPAbsorptionChamber1()),
	ARMOR_HP_ABSORPTION_CHAMBER_2(new ArmorHPAbsorptionChamber2());

	public final ElementInterface elementInterface;

	BlockRegistry(ElementInterface elementInterface) {
		this.elementInterface = elementInterface;
	}

	public static void registerBlocks() {
		for(BlockRegistry registry : values()) {
			registry.elementInterface.initData();
		}
		for(BlockRegistry registry : values()) {
			registry.elementInterface.postInitData();
		}
		for(BlockRegistry registry : values()) {
			registry.elementInterface.initResources();
		}
		for(BlockRegistry registry : values()) {
			BlockConfig.add(registry.getInfo());
		}
	}

	public ElementInformation getInfo() {
		return elementInterface.getInfo();
	}

	public short getId() {
		return getInfo().id;
	}
}

