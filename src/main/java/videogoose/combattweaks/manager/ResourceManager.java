package videogoose.combattweaks.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.shader.Shader;
import videogoose.combattweaks.CombatTweaks;

import java.util.HashMap;

public class ResourceManager {

	private static final HashMap<String, Shader> shaderMap = new HashMap<>();

	public static void loadResources(CombatTweaks instance) {
		StarLoaderTexture.runOnGraphicsThread(() -> {
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

	public static Shader getShader(String shaderName) {
		return shaderMap.get(shaderName);
	}
}