package videogoose.combattweaks.element.block.chamber.support.aura;

import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

/**
 * Base chamber for the friendly Support Aura (buffs allied ships inside the projected sphere). Lives under the
 * reactor <b>support</b> tree. Mutually exclusive with the Offense Aura so a ship picks one aura role.
 * (Formerly "Aura Projector".)
 */
public class SupportAuraChamber extends ChamberBlock {

	public SupportAuraChamber() {
		super("Support Aura");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Base chamber for friendly support auras; buffs allied ships inside the sphere.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		blockInfo.chamberConfigGroupsLowerCase.add(ConfigGroupRegistry.AURA_BASE_EFFECT.toString());
		blockInfo.chamberRoot = BlockRegistry.REACTOR_SUPPORT_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.02f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.REACTOR_SUPPORT_CHAMBER.getInfo().chamberChildren.add(getId());
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_SUPPORT_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_SUPPORT_CHAMBER_ICON.getIconID());
	}
}
