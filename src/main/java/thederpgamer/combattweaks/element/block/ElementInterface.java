package thederpgamer.combattweaks.element.block;

import org.schema.game.common.data.element.ElementInformation;

public interface ElementInterface {

	short getId();

	ElementInformation getInfo();

	/**
	 * Initialize the data for the element.
	 */
	void initData();

	/**
	 * Post-initializes any additional data for the element that requires all elements to be registered first.
	 */
	void postInitData();

	/**
	 * Initializes any resources (textures, icons, models, etc.) for the element.
	 */
	void initResources();
}
