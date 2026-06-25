package videogoose.combattweaks.element.block.chamber.offense.aura;

import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Offense Aura sub-chamber (tier 2): a stronger Targeting Jammer that scatters affected enemies' AI fire even
 * more. The upgrade target of {@link TargetingJammerAuraChamber1}; the debuff is applied at runtime by
 * {@code OffenseAuraAddOn}.
 */
public class TargetingJammerAuraChamber2 extends ChamberBlock {

	public TargetingJammerAuraChamber2() {
		super("Targeting Jammer Aura 2");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Strongly disrupts the AI targeting of enemy ships in this aura, badly scattering their turret, drone and point-defense fire.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.12f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.OFFENSE_AURA_CHAMBER.getInfo().chamberChildren.add(getId());
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
