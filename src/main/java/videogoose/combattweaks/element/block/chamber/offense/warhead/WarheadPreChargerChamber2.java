package videogoose.combattweaks.element.block.chamber.offense.warhead;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

public class WarheadPreChargerChamber2 extends ChamberBlock {

	public WarheadPreChargerChamber2() {
		super("Warhead Pre-Charger Chamber 2");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Increases Warhead Radius and Damage at the cost of added volatility.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.WARHEAD_PRE_CHARGER_EFFECT_2.toString());
		blockInfo.chamberCapacity = 0.012f;
	}

	@Override
	public void postInitData() {
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
