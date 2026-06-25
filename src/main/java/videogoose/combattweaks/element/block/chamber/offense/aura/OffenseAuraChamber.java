package videogoose.combattweaks.element.block.chamber.offense.aura;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Base chamber for the hostile Offense Aura (debuffs enemy ships inside the projected sphere). Lives under the
 * reactor <b>offense</b> tree, and shares the {@code AURA_BASE_EFFECT} range status with the Support Aura.
 * Mutually exclusive with the Support Aura so a ship picks one aura role.
 */
public class OffenseAuraChamber extends ChamberBlock {

	public OffenseAuraChamber() {
		super("Offense Aura");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Base chamber for hostile debuff auras; weakens enemy ships inside the sphere.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.AURA_BASE_EFFECT.toString());
		blockInfo.chamberRoot = BlockRegistry.REACTOR_OFFENSE_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.02f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.REACTOR_OFFENSE_CHAMBER.getInfo().chamberChildren.add(getId());
		// One aura role per ship: a reactor can't run both the support and offense aura base chambers.
		addExclusives((ChamberBlock) BlockRegistry.SUPPORT_AURA_CHAMBER.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
