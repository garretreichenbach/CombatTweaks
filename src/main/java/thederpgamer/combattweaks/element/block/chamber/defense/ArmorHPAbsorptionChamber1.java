package thederpgamer.combattweaks.element.block.chamber.defense;

import api.utils.element.Blocks;
import thederpgamer.combattweaks.effect.ConfigGroupRegistry;
import thederpgamer.combattweaks.element.block.chamber.ChamberBlock;

public class ArmorHPAbsorptionChamber1 extends ChamberBlock {

	public ArmorHPAbsorptionChamber1() {
		super("Armor HP Absorption 1");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Lowers the bleed through threshold of Armor HP, allowing armor to absorb more damage before the main ship is affected.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.ARMOR_HP_ABSORPTION_1.toString());
		blockInfo.chamberCapacity = 0.06f;
	}

	@Override
	public void postInitData() {
		blockInfo.chamberRoot = Blocks.DEFENCE_CHAMBER.getId();
		blockInfo.chamberParent = Blocks.BASE_ARMOR_ENHANCEMENT.getId();
		Blocks.BASE_ARMOR_ENHANCEMENT.getInfo().chamberChildren.add(getId());
	}

	@Override
	public void initResources() {
		blockInfo.setTextureId(Blocks.BASE_ARMOR_ENHANCEMENT.getInfo().getTextureIds());
		blockInfo.setBuildIconNum(Blocks.BASE_ARMOR_ENHANCEMENT.getInfo().getBuildIconNum());
	}
}