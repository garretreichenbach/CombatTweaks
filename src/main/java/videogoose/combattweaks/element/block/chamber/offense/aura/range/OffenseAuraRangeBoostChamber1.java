package videogoose.combattweaks.element.block.chamber.offense.aura.range;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Offense-tree range booster (tier 1): enlarges the sphere of an Offense Aura. Reuses the shared
 * {@code AURA_RANGE_BOOST_EFFECT_1} (which adds to the reactor's Aura Range), mirroring the support tree's range
 * boost so an offense-aura ship can grow its field without investing in the support tree. Upgrades to tier 2.
 */
public class OffenseAuraRangeBoostChamber1 extends ChamberBlock {

	public OffenseAuraRangeBoostChamber1() {
		super("Offense Aura Range Boost 1");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Boosts the maximum range of an active Offense Aura.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.AURA_RANGE_BOOST_EFFECT_1.toString());
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.04f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.REACTOR_OFFENSE_CHAMBER.getInfo().chamberChildren.add(getId());
		setUpgrade((ChamberBlock) BlockRegistry.OFFENSE_AURA_RANGE_BOOST_CHAMBER_2.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
