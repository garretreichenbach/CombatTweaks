package thederpgamer.combattweaks.element.block;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;

/**
 * Central registry that defines and registers all mod blocks/chambers/weapons.
 */
public enum BlockRegistry {
	//Chambers

	;

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

