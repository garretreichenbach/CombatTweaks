package videogoose.combattweaks.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.graphicsengine.shader.Shader;
import videogoose.combattweaks.CombatTweaks;

import javax.imageio.ImageIO;
import java.util.HashMap;
import java.util.Objects;

public class ResourceManager {

	private static final String[] spriteNames = {"tactical-map-indicators"};
	private static final HashMap<String, Sprite> spriteMap = new HashMap<>();
	private static final HashMap<String, Shader> shaderMap = new HashMap<>();

	public static void loadResources(CombatTweaks instance) {
		StarLoaderTexture.runOnGraphicsThread(() -> {
			for(String spriteName : spriteNames) {
				try {
					spriteMap.put(spriteName, StarLoaderTexture.newSprite(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/sprites/" + spriteName + ".png"))), instance, spriteName));
				} catch(Exception exception) {
					instance.logException("Failed to load sprite: " + spriteName, exception);
				}
			}

			// Load selection shaders
			loadShader(instance, "tactical_ring");
			loadShader(instance, "selection_outline");
			loadShader(instance, "selection_tint");
		});
	}

	private static void loadShader(CombatTweaks instance, String shaderName) {
		try {
			Shader shader = Shader.newModShader(instance.getSkeleton(), shaderName, instance.getClass().getResourceAsStream("/shaders/" + shaderName + ".vert"), instance.getClass().getResourceAsStream("/shaders/" + shaderName + ".frag"));
			shaderMap.put(shaderName, shader);
		} catch(Exception exception) {
			instance.logException("Failed to load shader: " + shaderName, exception);
		}
	}

	public static Sprite getSprite(String name) {
		return spriteMap.get(name);
	}

	public static Shader getShader(String shaderName) {
		return shaderMap.get(shaderName);
	}
}