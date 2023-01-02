package thederpgamer.combattweaks.element.items;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementCategory;
import org.schema.game.common.data.element.ElementInformation;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.manager.ResourceManager;

/**
 * Abstract Item class.
 *
 * @author TheDerpGamer
 */
public abstract class Item {

    protected ElementInformation itemInfo;

    public Item(String name, ElementCategory category) {
        String internalName = name.toLowerCase().replace(" ", "-").trim();
        short textureId = (short) ResourceManager.getTexture(internalName).getTextureId();
        itemInfo = BlockConfig.newElement(CombatTweaks.getInstance(), name, textureId);
        itemInfo.setBuildIconNum(textureId);
        itemInfo.setPlacable(false);
        itemInfo.setPhysical(false);
        BlockConfig.setElementCategory(itemInfo, category);
    }

    public final ElementInformation getItemInfo() {
        return itemInfo;
    }

    public final short getId() {
        return itemInfo.getId();
    }

    public abstract void initialize();
}
