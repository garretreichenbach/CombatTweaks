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
 * Repair Paste Fabricator that slowly generates material that can be used by Astrotech systems to repair ships instead
 * of using blocks.
 *
 * @author TheDerpGamer
 */
public class RepairPasteFabricator extends Block {

	public RepairPasteFabricator() {
		super("Repair Paste Fabricator", ElementKeyMap.getInfo(362).getType());
	}

	@Override
	public void initialize() {
		if(GraphicsContext.initialized) {
			try {
				short capsId = (short) ResourceManager.getTexture("repair-paste-fabricator-caps").getTextureId();
				short sidesId = (short) ResourceManager.getTexture("repair-paste-fabricator-sides").getTextureId();
				blockInfo.setTextureId(new short[] {sidesId, sidesId, capsId, capsId, sidesId, sidesId});
				blockInfo.setBuildIconNum(ResourceManager.getTexture("repair-paste-fabricator-icon").getTextureId());
			} catch(Exception ignored) {}
		}
		blockInfo.setDescription("Slowly generates Repair Paste over time that can be used in Astrotech systems as an alternative to blocks and resources.");
		blockInfo.setInRecipe(true);
		blockInfo.setCanActivate(false);
		blockInfo.setShoppable(true);
		blockInfo.setPrice(ElementKeyMap.getInfo(300).price);
		blockInfo.mass = 0.1f;
		blockInfo.maxHitPointsFull = 100;
		blockInfo.systemBlock = true;
		blockInfo.setOrientatable(true);
		blockInfo.setIndividualSides(6);
		blockInfo.setBlockStyle(BlockStyle.NORMAL.id);
		blockInfo.controlledBy.add(ElementKeyMap.REPAIR_CONTROLLER_ID);
		ElementKeyMap.getInfo(ElementKeyMap.REPAIR_CONTROLLER_ID).controlling.add(getId());

		BlockConfig.addRecipe(blockInfo, ElementManager.FactoryType.ADVANCED_FACTORY.ordinal(), 10,
				new FactoryResource(50, (short) 202),
				new FactoryResource(10, (short) 867),
				new FactoryResource(50, (short) 220)
		);
		BlockConfig.add(blockInfo);
	}
}
