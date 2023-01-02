package thederpgamer.combattweaks.element.blocks.systems;

import api.config.BlockConfig;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.element.FactoryResource;
import org.schema.schine.graphicsengine.core.GraphicsContext;
import thederpgamer.combattweaks.element.ElementManager;
import thederpgamer.combattweaks.element.blocks.Block;
import thederpgamer.combattweaks.manager.ResourceManager;

/**
 * <Description>
 *
 * @author TheDerpGamer
 */
public class AIRemoteController extends Block {

	public AIRemoteController() {
		super("AI Remote Controller", ElementKeyMap.getInfo(ElementKeyMap.AI_ELEMENT).getType());
	}

	@Override
	public void initialize() {
		if(GraphicsContext.initialized) {
			try {
				short[] aiModuleTextures = ElementKeyMap.getInfo(ElementKeyMap.AI_ELEMENT).getTextureIds();
				short frontTexture = (short) ResourceManager.getTexture("ai-remote-controller-front").getTextureId();
				blockInfo.setTextureId(new short[] {frontTexture, aiModuleTextures[1], aiModuleTextures[2], aiModuleTextures[3], aiModuleTextures[4], aiModuleTextures[5]});
			} catch(Exception ignored) {}
		}

		blockInfo.setDescription("Used to remotely control ai drones from a distance.");
		blockInfo.setInRecipe(true);
		blockInfo.setCanActivate(true);
		blockInfo.setShoppable(true);
		blockInfo.setPrice(ElementKeyMap.getInfo(300).price);
		blockInfo.mass = 0.1f;
		blockInfo.maxHitPointsFull = 100;
		blockInfo.systemBlock = true;
		blockInfo.setOrientatable(true);
		blockInfo.setIndividualSides(6);
		blockInfo.setBlockStyle(BlockStyle.NORMAL.id);

		BlockConfig.addRecipe(blockInfo, ElementManager.FactoryType.ADVANCED_FACTORY.ordinal(), 10,
		                      new FactoryResource(3, ElementKeyMap.AI_ELEMENT),
		                      new FactoryResource(1, ElementKeyMap.FACTION_BLOCK),
		                      new FactoryResource(5, ElementKeyMap.LOGIC_WIRELESS));
		BlockConfig.add(blockInfo);
	}
}
