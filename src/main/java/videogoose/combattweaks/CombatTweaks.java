package videogoose.combattweaks;

import api.block.BlockConfig;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import api.config.registry.UniversalRegistry;
import api.render.texture.StarLoaderTexture;
import org.schema.game.client.view.mainmenu.GuidesRegistry;
import org.schema.schine.graphicsengine.core.GLFW;
import org.schema.schine.graphicsengine.core.GraphicsContext;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;
import org.schema.schine.resource.ResourceLoader;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.*;
import videogoose.combattweaks.network.client.*;
import videogoose.combattweaks.network.server.SendAuraSyncPacket;
import videogoose.combattweaks.network.server.SendIncomingSignaturesPacket;

import java.util.Collections;
import java.util.List;

public class CombatTweaks extends StarMod {

	public static void main(String[] args) {}
	private static CombatTweaks instance;
	public static CombatTweaks getInstance() {
		return instance;
	}
	public CombatTweaks() {
		instance = this;
	}

	/**
	 * The tactical-map toggle binding, registered with StarMade's keybind system so it shows up in the
	 * Controls settings and is user-rebindable. Detected via {@code KeyPressEvent.isMapping(this)}.
	 */
	public KeyboardMappings tacticalMapKey;

	@Override
	public List<String> getMixinConfigs() {
		return Collections.singletonList("mixins.combattweaks.json");
	}

	@Override
	public void onEnable() {
		instance = this;
		ConfigManager.initialize(this);
		tacticalMapKey = KeyboardMappings.registerMapping(this, "Toggle Tactical Map", GLFW.GLFW_KEY_BACKSLASH, KeyboardContext.GENERAL);
		EventManager.initialize(this);
		MoveManager.getInstance(); // Initialize move manager
		MineManager.getInstance(); // Initialize mine manager
		RepairManager.getInstance(); // Initialize repair manager
		IncomingSignatureManager.getInstance(); // Initialize incoming-signature detector
		AuraManager.getInstance(); // Initialize aura broadcaster
		registerPackets();
		overwriteTextures();
	}

	/**
	 * Replaces StarMade's reactor chamber-tab icon sheet with our own so the ported chambers (offense/support
	 * trees) show proper tab art. No-op on a headless server (no graphics context). Ported from BetterChambers.
	 */
	private void overwriteTextures() {
		if(GraphicsContext.initialized) {
			StarLoaderTexture.addSpriteChange("gui/ingame/UI 64px ChamberTabs-4x4-gui-", (bufferedImage, graphics) -> bufferedImage.setData(getJarBufferedImage("sprites/reactor_chamber_tab_icons.png").getData()));
		}
	}

	@Override
	public void onResourceLoad(ResourceLoader resourceLoader) {
		ResourceManager.loadResources(this);
	}

	@Override
	public void onUniversalRegistryLoad() {
		// Reserve each aura projector's player-usable id so the activatable addons get stable, synced ids.
		UniversalRegistry.registerURV(UniversalRegistry.RegistryType.PLAYER_USABLE_ID, getSkeleton(), "SupportAuraChamber");
		UniversalRegistry.registerURV(UniversalRegistry.RegistryType.PLAYER_USABLE_ID, getSkeleton(), "OffenseAuraChamber");
	}

	@Override
	public void onRegisterGuides(GuidesRegistry.ModGuideRegistrar registrar) {
		String key = "combattweaks";
		String label = "CombatTweaks";
		registrar.registerFromResource(key, label, "Overview", "guides/index.md", this);
		registrar.registerFromResource(key, label, "Configuration", "guides/features/configuration.md", this);
		registrar.registerFromResource(key, label, "Tactical Map", "guides/features/tactical-map.md", this);
		registrar.registerFromResource(key, label, "Fleet Orders", "guides/features/fleet-orders.md", this);
		registrar.registerFromResource(key, label, "Incoming Signatures", "guides/features/incoming-signatures.md", this);
		registrar.registerFromResource(key, label, "Auras", "guides/features/auras.md", this);
		registrar.registerFromResource(key, label, "Aura Disruptor", "guides/features/aura-disruptor.md", this);
		registrar.registerFromResource(key, label, "Warhead Pre-Charger", "guides/features/warhead-pre-charger.md", this);
		registrar.registerFromResource(key, label, "Thrust Blast", "guides/features/thrust-blast.md", this);
	}

	@Override
	public void onBlockConfigLoad(BlockConfig config) {
		BlockRegistry.registerBlocks();
	}

	private void registerPackets() {
		PacketUtil.registerPacket(this, SendAttackPacket.class);
		PacketUtil.registerPacket(this, SendDefensePacket.class);
		PacketUtil.registerPacket(this, SendIdlePacket.class);
		PacketUtil.registerPacket(this, SendMinePacket.class);
		PacketUtil.registerPacket(this, SendRepairPacket.class);
		PacketUtil.registerPacket(this, SendMoveToPacket.class);
		PacketUtil.registerPacket(this, SendMoveToPositionPacket.class);
		PacketUtil.registerPacket(this, SendThrustBlastPacket.class);
		PacketUtil.registerPacket(this, SendSupportingFirePacket.class);
		PacketUtil.registerPacket(this, SendAuraSyncPacket.class);
		PacketUtil.registerPacket(this, SendIncomingSignaturesPacket.class);
	}
}
