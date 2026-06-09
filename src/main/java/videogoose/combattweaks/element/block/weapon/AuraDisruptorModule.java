package videogoose.combattweaks.element.block.weapon;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.FactoryResource;
import videogoose.combattweaks.element.block.Block;
import videogoose.combattweaks.manager.ResourceManager;

import java.util.ArrayList;

public class AuraDisruptorModule extends Block {

	public AuraDisruptorModule() {
		super("Aura Disruptor Module");
	}

	@Override
	public void initData() {
		super.initData();
		blockInfo.setDescription("Used to control Aura Disruptor Systems. Simply aim and fire at the enemy ship that is projecting the aura, and the effects of the aura will be reduced based on the size of the disruptor system relative to the strength of the aura itself.");
		blockInfo.setPlacable(true);
		blockInfo.canActivate = true;
		blockInfo.setInRecipe(true);
		blockInfo.shoppable = true;
		blockInfo.price = Blocks.DAMAGE_BEAM_MODULE.getInfo().price;
		blockInfo.mass = Blocks.DAMAGE_BEAM_MODULE.getInfo().mass;
		blockInfo.volume = Blocks.DAMAGE_BEAM_MODULE.getInfo().volume;
	}

	@Override
	public void postInitData() {
		BlockConfig.addRecipe(blockInfo, Blocks.DAMAGE_BEAM_MODULE.getInfo().getProducedInFactoryType(), (int) Blocks.DAMAGE_BEAM_MODULE.getInfo().getFactoryBakeTime(), (new ArrayList<>(Blocks.DAMAGE_BEAM_MODULE.getInfo().getConsistence()).toArray(new FactoryResource[1])));
		BlockConfig.setElementCategory(blockInfo, Blocks.DAMAGE_BEAM_MODULE.getInfo().getType());
	}

	@Override
	public void initResources() {
		blockInfo.setTextureId(new short[]{
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_FRONT.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_SIDES.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_TOP.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_TOP.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_SIDES.getTextureID(),
				ResourceManager.Textures.AURA_DISRUPTOR_MODULE_SIDES.getTextureID()
		});
		blockInfo.setBuildIconNum(ResourceManager.Icons.AURA_DISRUPTOR_MODULE_ICON.getIconID());
	}
}
