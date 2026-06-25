package videogoose.combattweaks.element.block.chamber.offense.aura;

import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Offense Aura sub-chamber (tier 1): makes the projector jam the AI targeting of enemies inside the sphere, so
 * their turrets/drones/point-defense miss more. The debuff is applied at runtime by {@code OffenseAuraAddOn}, so
 * no passive config group is attached here. Upgrades to {@link TargetingJammerAuraChamber2}.
 */
public class TargetingJammerAuraChamber1 extends ChamberBlock {

	public TargetingJammerAuraChamber1() {
		super("Targeting Jammer Aura 1");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Disrupts the AI targeting of enemy ships in this aura, scattering their turret, drone and point-defense fire.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.08f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.OFFENSE_AURA_CHAMBER.getInfo().chamberChildren.add(getId());
		setUpgrade((ChamberBlock) BlockRegistry.TARGETING_JAMMER_AURA_CHAMBER_2.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
