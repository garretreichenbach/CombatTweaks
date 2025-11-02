package thederpgamer.combattweaks.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.forms.Sprite;
import thederpgamer.combattweaks.CombatTweaks;

import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.Objects;

public class ResourceManager {
	private static final String[] textureNames = {

	};
	private static final String[] spriteNames = {"tactical-map-indicators"};
	private static final HashMap<String, StarLoaderTexture> textureMap = new HashMap<>();
	private static final HashMap<String, Sprite> spriteMap = new HashMap<>();

	public static void loadResources(final CombatTweaks instance) {
		StarLoaderTexture.runOnGraphicsThread(new Runnable() {
			@Override
			public void run() {
				for(String textureName : textureNames) {
					try {
						if(textureName.endsWith("icon"))
							textureMap.put(textureName, StarLoaderTexture.newIconTexture(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/textures/" + textureName + ".png")))));
						else
							textureMap.put(textureName, StarLoaderTexture.newBlockTexture(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/textures/" + textureName + ".png")))));
					} catch(Exception exception) {
						instance.logException("Failed to load texture: " + textureName, exception);
					}
				}
				for(String spriteName : spriteNames) {
					try {
						spriteMap.put(spriteName, StarLoaderTexture.newSprite(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/sprites/" + spriteName + ".png"))), instance, spriteName));
					} catch(Exception exception) {
						instance.logException("Failed to load sprite: " + spriteName, exception);
					}
				}
			}
		});
	}

	public static StarLoaderTexture getTexture(String name) {
		return textureMap.get(name);
	}

	public static Sprite getSprite(String name) {
		return spriteMap.get(name);
	}
}