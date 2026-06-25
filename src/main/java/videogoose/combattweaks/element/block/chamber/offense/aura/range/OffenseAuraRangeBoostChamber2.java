package videogoose.combattweaks.element.block.chamber.offense.aura.range;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Offense-tree range booster (tier 2): a stronger Offense Aura range boost, the upgrade target of
 * {@link OffenseAuraRangeBoostChamber1}. Reuses the shared {@code AURA_RANGE_BOOST_EFFECT_2}.
 */
public class OffenseAuraRangeBoostChamber2 extends ChamberBlock {

	public OffenseAuraRangeBoostChamber2() {
		super("Offense Aura Range Boost 2");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Boosts the maximum range of an active Offense Aura.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.AURA_RANGE_BOOST_EFFECT_2.toString());
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.08f;
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
