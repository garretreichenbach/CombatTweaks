package videogoose.combattweaks.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.shader.Shader;
import org.schema.schine.graphicsengine.texture.TGALoader;
import videogoose.combattweaks.CombatTweaks;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

/**
 * Loads and caches the mod's GPU resources: block textures, build icons, UI sprites and shaders.
 * <p>
 * Block art lives in {@code 16x16}-tile atlases ({@code textures/atlas0.png} + its {@code _NRM} normal map,
 * {@code icons/icons0.png}). Each tile is sliced out into a {@link StarLoaderTexture} indexed by its grid
 * position; blocks reference their slice through the {@link Textures}/{@link Icons} enums so the indices stay
 * stable and self-documenting. (Atlas/icon loading folded in from the BetterChambers merge.)
 */
public class ResourceManager {

	private static final int TEXTURE_ATLAS_SIZE = 4096;
	private static final int TEXTURES_PER_ATLAS = 16 * 16; // 256 textures per atlas
	private static final int ICON_ATLAS_SIZE = 1024;
	private static final int ICONS_PER_ATLAS = 16 * 16; // 256 sprites per atlas

	private static final HashMap<Integer, StarLoaderTexture> textures = new HashMap<>();
	private static final HashMap<Integer, StarLoaderTexture> icons = new HashMap<>();
	private static final HashMap<String, Shader> shaderMap = new HashMap<>();

	public static void loadResources(CombatTweaks instance) {
		// Decode block/icon art synchronously (as BetterChambers did): newBlockTexture/newIconTexture only build
		// and cache scaled BufferedImages — no GL work — so they're safe off the graphics thread, AND the jar
		// resource streams must be read here-and-now. Deferring this read onto the graphics-thread run queue
		// (runOnGraphicsThread) lets the backing jar handle close first, which throws "Stream closed" mid-TGA.
		loadAtlas(instance, "atlas0");
		loadIcons(instance, "icons0");
		// Shaders DO need the GL context, so those stay on the graphics thread.
		StarLoaderTexture.runOnGraphicsThread(() -> {
			loadShader(instance, "tactical_ring");
			loadShader(instance, "selection_outline");
			loadShader(instance, "selection_tint");
		});
	}

	private static void loadAtlas(CombatTweaks instance, String atlasName) {
		try {
			BufferedImage atlas0 = instance.getJarBufferedImage("textures/" + atlasName + ".png");
			InputStream atlas0NRMStream = instance.getJarResource("textures/" + atlasName + "_NRM.tga");
			ByteBuffer atlas0NRMBuffer = TGALoader.loadImage(atlas0NRMStream);
			BufferedImage atlas0NRM = TGALoader.convertByteBufferToImage(atlas0NRMBuffer, TGALoader.getLastWidth(), TGALoader.getLastHeight(), true);
			for(int i = 0; i < TEXTURES_PER_ATLAS; i++) {
				int x = (i % 16) * (TEXTURE_ATLAS_SIZE / 16);
				int y = (i / 16) * (TEXTURE_ATLAS_SIZE / 16);
				BufferedImage texture = atlas0.getSubimage(x, y, TEXTURE_ATLAS_SIZE / 16, TEXTURE_ATLAS_SIZE / 16);
				BufferedImage textureNRM = atlas0NRM.getSubimage(x, y, TEXTURE_ATLAS_SIZE / 16, TEXTURE_ATLAS_SIZE / 16);
				StarLoaderTexture starLoaderTexture = StarLoaderTexture.newBlockTexture(texture, textureNRM);
				textures.put(i, starLoaderTexture);
			}
		} catch(Exception exception) {
			instance.logException("Failed to load atlas " + atlasName, exception);
		}
	}

	private static void loadIcons(CombatTweaks instance, String sheetName) {
		try {
			BufferedImage icons0 = instance.getJarBufferedImage("icons/" + sheetName + ".png");
			for(int i = 0; i < ICONS_PER_ATLAS; i++) {
				int x = (i % 16) * (ICON_ATLAS_SIZE / 16);
				int y = (i / 16) * (ICON_ATLAS_SIZE / 16);
				BufferedImage icon = icons0.getSubimage(x, y, ICON_ATLAS_SIZE / 16, ICON_ATLAS_SIZE / 16);
				StarLoaderTexture starLoaderTexture = StarLoaderTexture.newIconTexture(icon);
				icons.put(i, starLoaderTexture);
			}
		} catch(Exception exception) {
			instance.logException("Failed to load icons " + sheetName, exception);
		}
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

	public static short getTextureID(int index) {
		StarLoaderTexture t = textures.get(index);
		if(t == null) {
			return 0;
		}
		return (short) t.getTextureId();
	}

	public enum Icons {
		REACTOR_OFFENSE_CHAMBER_ICON,
		REACTOR_SUPPORT_CHAMBER_ICON,
		AURA_DISRUPTOR_COMPUTER_ICON,
		AURA_DISRUPTOR_MODULE_ICON;

		public StarLoaderTexture getIcon() {
			return icons.get(ordinal());
		}

		public short getIconID() {
			return (short) getIcon().getTextureId();
		}
	}

	public enum Textures {
		REACTOR_OFFENSE_CHAMBER_ALL(0),
		REACTOR_SUPPORT_CHAMBER_ALL(2),
		AURA_DISRUPTOR_COMPUTER_FRONT(4),
		AURA_DISRUPTOR_COMPUTER_SIDES(5),
		AURA_DISRUPTOR_MODULE_FRONT(6),
		AURA_DISRUPTOR_MODULE_SIDES(7),
		AURA_DISRUPTOR_MODULE_TOP(8);

		private final int index;

		Textures(int index) {
			this.index = index;
		}

		public StarLoaderTexture getTexture() {
			return textures.get(index);
		}

		public short getTextureID() {
			return (short) getTexture().getTextureId();
		}
	}
}
