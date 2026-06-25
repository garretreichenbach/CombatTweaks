package videogoose.combattweaks.element.block.chamber.support.aura.shieldaura;

import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.element.block.chamber.ChamberBlock;
import videogoose.combattweaks.manager.ResourceManager;

public class ShieldAuraCapacityChamber1 extends ChamberBlock {

	public ShieldAuraCapacityChamber1() {
		super("Shield Aura Capacity 1");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Boosts the shield capacity of ships affected by this aura.");
		blockInfo.setPlacable(false);
		blockInfo.setInRecipe(false);
		blockInfo.reactorHp = 20;
		blockInfo.shoppable = false;
		// The shield-capacity effect is applied at runtime by the Aura Projector to ships inside the sphere,
		// not as a passive chamber effect on the projector itself, so no config group is attached here.
		blockInfo.chamberRoot = BlockRegistry.REACTOR_SUPPORT_CHAMBER.getId();
		blockInfo.chamberCapacity = 0.08f;
	}

	@Override
	public void postInitData() {
		BlockRegistry.SUPPORT_AURA_CHAMBER.getInfo().chamberChildren.add(getId());
		setUpgrade((ChamberBlock) BlockRegistry.SHIELD_AURA_CAPACITY_CHAMBER_2.elementInterface);
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_SUPPORT_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_SUPPORT_CHAMBER_ICON.getIconID());
	}
}
