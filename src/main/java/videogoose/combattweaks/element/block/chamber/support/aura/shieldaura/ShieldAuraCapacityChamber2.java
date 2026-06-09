package videogoose.combattweaks.element.block.chamber.support.aura.shieldaura;

import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

public class ShieldAuraCapacityChamber2 extends ChamberBlock {

	public ShieldAuraCapacityChamber2() {
		super("Shield Aura Capacity 2");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Boosts the shield capacity of ships affected by this aura.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		// Applied at runtime by the Aura Projector (see ShieldAuraCapacityChamber1); no passive config group here.
		blockInfo.chamberRoot = BlockRegistry.REACTOR_SUPPORT_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.12f;
	}

	@Override
	public void postInitData() {
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_SUPPORT_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_SUPPORT_CHAMBER_ICON.getIconID());
	}
}
