package thederpgamer.combattweaks.element;

import api.config.BlockConfig;
import org.apache.commons.lang3.StringUtils;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementCategory;
import org.schema.game.common.data.element.ElementKeyMap;
import thederpgamer.combattweaks.element.blocks.Block;
import thederpgamer.combattweaks.element.items.Item;

import java.util.ArrayList;

/**
 * Manages mod blocks and items.
 *
 * @author TheDerpGamer
 * @since 07/02/2021
 */
public class ElementManager {

    public enum FactoryType {NONE, CAPSULE_REFINERY, MICRO_ASSEMBLER, BASIC_FACTORY, STANDARD_FACTORY, ADVANCED_FACTORY}

    private static final ArrayList<Block> blockList = new ArrayList<>();
    private static final ArrayList<Item> itemList = new ArrayList<>();

    public static void initialize() {
        for(Block block : blockList) block.initialize();
        for(Item item : itemList) item.initialize();
    }

    public static ArrayList<Block> getBlockList() {
        return blockList;
    }

    public static ArrayList<Item> getAllItems() {
        return itemList;
    }

    public static Block getBlock(short id) {
        for(Block blockElement : getBlockList()) if(blockElement.getBlockInfo().getId() == id) return blockElement;
        return null;
    }

    public static Block getBlock(String blockName) {
        for(Block block : getBlockList()) {
            if(block.getBlockInfo().getName().equalsIgnoreCase(blockName)) return block;
        }
        return null;
    }

    public static Block getBlock(SegmentPiece segmentPiece) {
        for(Block block : getBlockList()) if(block.getId() == segmentPiece.getType()) return block;
        return null;
    }

    public static Item getItem(String itemName) {
        for(Item item : getAllItems()) {
            if(item.getItemInfo().getName().equalsIgnoreCase(itemName)) return item;
        }
        return null;
    }

    public static void addBlock(Block block) {
        blockList.add(block);
    }

    public static void addItem(Item item) {
        itemList.add(item);
    }

    public static ElementCategory getCategory(String path) {
        String[] split = path.split("\\.");
        ElementCategory category = ElementKeyMap.getCategoryHirarchy();
        for(String s : split) {
            boolean createNew = false;
            if(category.hasChildren()) {
                for(ElementCategory child : category.getChildren()) {
                    if(child.getCategory().equalsIgnoreCase(s)) {
                        category = child;
                        break;
                    }
                    createNew = true;
                }
            } else createNew = true;
            if(createNew) category = BlockConfig.newElementCategory(category, StringUtils.capitalize(s));
        }
        return category;
    }
}
