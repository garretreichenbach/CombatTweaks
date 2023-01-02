package thederpgamer.combattweaks.element.blocks;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementCategory;
import org.schema.game.common.data.element.ElementInformation;
import thederpgamer.combattweaks.CombatTweaks;

/**
 * Abstract Block class.
 *
 * @author TheDerpGamer
 */
public abstract class Block {

    protected ElementInformation blockInfo;

    public Block(String name, ElementCategory category) {
        blockInfo = BlockConfig.newElement(CombatTweaks.getInstance(), name, new short[6]);
        BlockConfig.setElementCategory(blockInfo, category);
    }

    public final ElementInformation getBlockInfo() {
        return blockInfo;
    }

    public final short getId() {
        return blockInfo.getId();
    }

    public abstract void initialize();
}
