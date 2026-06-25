package videogoose.combattweaks.element.block.chamber.offense.aura;

import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Offense Aura sub-chamber: makes the projector reduce the weapon range of enemies inside the sphere. The debuff
 * itself is applied at runtime by {@code OffenseAuraAddOn}, so no passive config group is attached here.
 */
public class WeaponRangeDampenAuraChamber extends ChamberBlock {

	public WeaponRangeDampenAuraChamber() {
		super("Weapon Range Dampen Aura");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Reduces the weapon range of enemy ships affected by this aura.");
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
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
