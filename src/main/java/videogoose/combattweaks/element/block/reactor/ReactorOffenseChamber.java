package videogoose.combattweaks.element.block.reactor;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.FactoryResource;
import videogoose.combattweaks.element.block.Block;
import videogoose.combattweaks.manager.ResourceManager;

import java.util.ArrayList;

/**
 * General reactor chamber that serves as the root of the offensive chamber tree (e.g. Warhead Pre-Charger).
 * Modeled on the vanilla defence chamber stats; needs a physical reactor link via Reactor Conduit blocks.
 */
public class ReactorOffenseChamber extends Block {

	public ReactorOffenseChamber() {
		super("Reactor Offense Chamber");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Reactor Chamber to enhance offensive capabilities.\nNeeds to be physically connected with a Power Reactor by using Reactor Conduit blocks.");
		blockInfo.setPlacable(true);
		blockInfo.canActivate = true;
		blockInfo.setInRecipe(true);
		blockInfo.shoppable = true;
		blockInfo.price = Blocks.DEFENCE_CHAMBER.getInfo().price;
		blockInfo.mass = Blocks.DEFENCE_CHAMBER.getInfo().mass;
		blockInfo.volume = Blocks.DEFENCE_CHAMBER.getInfo().volume;

		blockInfo.chamberPermission = 1;
		blockInfo.blockResourceType = 2;
		blockInfo.chamberGeneral = true;
		blockInfo.reactorGeneralIconIndex = 10;
	}

	@Override
	public void postInitData() {
		BlockConfig.addRecipe(blockInfo, Blocks.DEFENCE_CHAMBER.getInfo().getProducedInFactoryType(), (int) Blocks.DEFENCE_CHAMBER.getInfo().getFactoryBakeTime(), (new ArrayList<>(Blocks.DEFENCE_CHAMBER.getInfo().getConsistence()).toArray(new FactoryResource[1])));
		BlockConfig.setElementCategory(blockInfo, Blocks.DEFENCE_CHAMBER.getInfo().getType());
	}

	@Override
	public void initResources() {
		short textureID = ResourceManager.Textures.REACTOR_OFFENSE_CHAMBER_ALL.getTextureID();
		blockInfo.setTextureId(new short[]{textureID, textureID, textureID, textureID, textureID, textureID});
		blockInfo.setBuildIconNum(ResourceManager.Icons.REACTOR_OFFENSE_CHAMBER_ICON.getIconID());
	}
}
