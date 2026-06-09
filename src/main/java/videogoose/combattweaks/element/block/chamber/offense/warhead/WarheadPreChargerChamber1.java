package videogoose.combattweaks.element.block.chamber.offense.warhead;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

public class WarheadPreChargerChamber1 extends ChamberBlock {

	public WarheadPreChargerChamber1() {
		super("Warhead Pre-Charger Chamber 1");
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
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.WARHEAD_PRE_CHARGER_EFFECT_1.toString());
		blockInfo.chamberCapacity = 0.08f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.REACTOR_OFFENSE_CHAMBER.getInfo().chamberChildren.add(getId());
		setUpgrade((ChamberBlock) BlockRegistry.WARHEAD_PRE_CHARGER_CHAMBER_2.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
