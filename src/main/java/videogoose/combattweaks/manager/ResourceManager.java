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
	private static Shader tacRingShader;

	public static void loadResources(CombatTweaks instance) {
		StarLoaderTexture.runOnGraphicsThread(() -> {
			for(String spriteName : spriteNames) {
				try {
					spriteMap.put(spriteName, StarLoaderTexture.newSprite(ImageIO.read(Objects.requireNonNull(instance.getClass().getResourceAsStream("/sprites/" + spriteName + ".png"))), instance, spriteName));
				} catch(Exception exception) {
					instance.logException("Failed to load sprite: " + spriteName, exception);
				}
			}
			try {
				tacRingShader = Shader.newModShader(instance.getSkeleton(), "TacticalRingShader", instance.getClass().getResourceAsStream("/shaders/tactical_ring.vert"), instance.getClass().getResourceAsStream("/shaders/tactical_ring.frag"));
			} catch(Exception exception) {
				instance.logException("Failed to load tactical ring shader", exception);
			}
		});
	}

	public static Sprite getSprite(String name) {
		return spriteMap.get(name);
	}

	public static Shader getTacRingShader() {
		return tacRingShader;
	}
}