package thederpgamer.combattweaks.manager;

import api.utils.textures.StarLoaderTexture;
import thederpgamer.combattweaks.CombatTweaks;

import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.Objects;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ResourceManager {

	private static final String[] textureNames = {
			"repair-paste-fabricator-caps",
			"repair-paste-fabricator-sides",
			"repair-paste-fabricator-icon"
	};

	private static final HashMap<String, StarLoaderTexture> textureMap = new HashMap<>();

	public static void loadResources(final CombatTweaks instance) {
		StarLoaderTexture.runOnGraphicsThread(new Runnable() {
			@Override
			public void run() {
				for(String textureName : textureNames) {
					try {
						if(textureName.endsWith("icon")) textureMap.put(textureName, StarLoaderTexture.newIconTexture(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/textures/" + textureName + ".png")))));
						else textureMap.put(textureName, StarLoaderTexture.newBlockTexture(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/textures/" + textureName + ".png")))));
					} catch(Exception exception) {
						exception.printStackTrace();
					}
				}
			}
		});
	}

	public static StarLoaderTexture getTexture(String name) {
		return textureMap.get(name);
	}
}