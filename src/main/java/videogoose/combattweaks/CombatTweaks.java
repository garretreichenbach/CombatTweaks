package videogoose.combattweaks;

import api.config.BlockConfig;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import org.lwjgl.input.Keyboard;
import org.schema.game.client.view.mainmenu.GuidesRegistry;
import org.schema.schine.graphicsengine.core.GLFW;
import org.schema.schine.input.KeyboardContext;
import org.schema.schine.input.KeyboardMappings;
import org.schema.schine.resource.ResourceLoader;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.*;
import videogoose.combattweaks.network.client.*;
import videogoose.combattweaks.network.server.SendArmorHPSyncPacket;
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
		// Register the tactical-map toggle with StarMade's keybind system (rebindable in Controls settings,
		// GENERAL context = available everywhere). The config value seeds the default key; once registered,
		// StarMade owns the binding. Detected in EventManager via KeyPressEvent.isMapping(tacticalMapKey).
		tacticalMapKey = KeyboardMappings.registerMapping(this, "Toggle Tactical Map", GLFW.GLFW_KEY_BACKSLASH, KeyboardContext.GENERAL);
		EventManager.initialize(this);
		MoveManager.getInstance(); // Initialize move manager
		MineManager.getInstance(); // Initialize mine manager
		RepairManager.getInstance(); // Initialize repair manager
		IncomingSignatureManager.getInstance(); // Initialize incoming-signature detector
		registerPackets();
	}

	@Override
	public void onResourceLoad(ResourceLoader resourceLoader) {
		ResourceManager.loadResources(this);
	}

	@Override
	public void onRegisterGuides(GuidesRegistry.ModGuideRegistrar registrar) {
		String key = "combattweaks";
		String label = "CombatTweaks";
		registrar.registerFromResource(key, label, "Overview", "guides/index.md", this);
		registrar.registerFromResource(key, label, "Configuration", "guides/getting-started/configuration.md", this);
		registrar.registerFromResource(key, label, "Tactical Map", "guides/features/tactical-map.md", this);
		registrar.registerFromResource(key, label, "Fleet Orders", "guides/features/fleet-orders.md", this);
		registrar.registerFromResource(key, label, "Incoming Signatures", "guides/features/incoming-signatures.md", this);
		registrar.registerFromResource(key, label, "Armor HP System", "guides/features/armor-hp.md", this);
	}

	@Override
	public void onBlockConfigLoad(BlockConfig config) {
		BlockRegistry.registerBlocks();
	}

	private void registerPackets() {
		PacketUtil.registerPacket(SendAttackPacket.class);
		PacketUtil.registerPacket(SendDefensePacket.class);
		PacketUtil.registerPacket(SendIdlePacket.class);
		PacketUtil.registerPacket(SendMinePacket.class);
		PacketUtil.registerPacket(SendRepairPacket.class);
		PacketUtil.registerPacket(SendMoveToPacket.class);
		PacketUtil.registerPacket(SendArmorHPSyncPacket.class);
		PacketUtil.registerPacket(SendIncomingSignaturesPacket.class);
		PacketUtil.registerPacket(videogoose.combattweaks.network.client.RequestArmorSyncPacket.class);
	}
}
