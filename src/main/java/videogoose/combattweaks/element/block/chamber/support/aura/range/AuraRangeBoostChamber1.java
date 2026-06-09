package videogoose.combattweaks.element.block.chamber.support.aura.range;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

public class AuraRangeBoostChamber1 extends ChamberBlock {

	public AuraRangeBoostChamber1() {
		super("Aura Range Boost 1");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Boosts the maximum range of active auras.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.AURA_RANGE_BOOST_EFFECT_1.toString());
		blockInfo.chamberRoot = BlockRegistry.REACTOR_SUPPORT_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.04f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.REACTOR_SUPPORT_CHAMBER.getInfo().chamberChildren.add(getId());
		setUpgrade((ChamberBlock) BlockRegistry.AURA_RANGE_BOOST_CHAMBER_2.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_SUPPORT_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_SUPPORT_CHAMBER_ICON.getIconID());
	}
}
