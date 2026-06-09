package videogoose.combattweaks.element.block.weapon;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.FactoryResource;
import videogoose.combattweaks.element.block.Block;
import videogoose.combattweaks.manager.ResourceManager;

import java.util.ArrayList;

public class AuraDisruptorComputer extends Block {

	public AuraDisruptorComputer() {
		super("Aura Disruptor Computer");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Used to control Aura Disruptor Systems. Simply aim and fire at the enemy ship that is projecting the aura, and the effects of the aura will be reduced based on the size of the disruptor system relative to the strength of the aura itself.");
		blockInfo.setPlacable(true);
		blockInfo.canActivate = true;
		blockInfo.setInRecipe(true);
		blockInfo.shoppable = true;
		blockInfo.price = Blocks.DAMAGE_BEAM_COMPUTER.getInfo().price;
		blockInfo.mass = Blocks.DAMAGE_BEAM_COMPUTER.getInfo().mass;
		blockInfo.volume = Blocks.DAMAGE_BEAM_COMPUTER.getInfo().volume;
	}

	@Override
	public void postInitData() {
		BlockConfig.addRecipe(blockInfo, Blocks.DAMAGE_BEAM_COMPUTER.getInfo().getProducedInFactoryType(), (int) Blocks.DAMAGE_BEAM_COMPUTER.getInfo().getFactoryBakeTime(), (new ArrayList<>(Blocks.DAMAGE_BEAM_COMPUTER.getInfo().getConsistence()).toArray(new FactoryResource[1])));
		BlockConfig.setElementCategory(blockInfo, Blocks.DAMAGE_BEAM_COMPUTER.getInfo().getType());
	}

	@Override
	public void initResources() {
		blockInfo.setTextureId(new short[]{
				Blocks.SALVAGE_COMPUTER.getInfo().getTextureId(0),
				ResourceManager.Textures.AURA_DISRUPTOR_COMPUTER_FRONT.getTextureID(),
				Blocks.SALVAGE_COMPUTER.getInfo().getTextureId(2),
				Blocks.SALVAGE_COMPUTER.getInfo().getTextureId(3),
				ResourceManager.Textures.AURA_DISRUPTOR_COMPUTER_SIDES.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_COMPUTER_SIDES.getTextureID()});
		blockInfo.setBuildIconNum(ResourceManager.Icons.AURA_DISRUPTOR_COMPUTER_ICON.getIconID());
	}
}
