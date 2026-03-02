package videogoose.combattweaks;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import glossar.GlossarCategory;
import glossar.GlossarInit;
import org.lwjgl.input.Keyboard;
import org.schema.schine.resource.ResourceLoader;
import videogoose.combattweaks.data.ControlBindingData;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.manager.EventManager;
import videogoose.combattweaks.manager.ResourceManager;
import videogoose.combattweaks.network.client.SendAttackPacket;

public class CombatTweaks extends StarMod {

	public static void main(String[] args) {}
	private static CombatTweaks instance;
	public static CombatTweaks getInstance() {
		return instance;
	}
	public CombatTweaks() {
		instance = this;
	}

	@Override
	public void onEnable() {
		instance = this;
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerPackets();
	}

	@Override
	public void onClientCreated(ClientInitializeEvent clientInitializeEvent) {
		super.onClientCreated(clientInitializeEvent);
		initializeGlossary();
		registerBindings();
	}

	@Override
	public void onResourceLoad(ResourceLoader resourceLoader) {
		ResourceManager.loadResources(this);
	}

	@Override
	public void onBlockConfigLoad(BlockConfig config) {
		BlockRegistry.registerBlocks();
	}

	private void registerPackets() {
		PacketUtil.registerPacket(SendAttackPacket.class);
	}

	private void initializeGlossary() {
		GlossarInit.initGlossar(this);
		GlossarCategory combatTweaks = new GlossarCategory("Combat Tweaks");
		GlossarInit.addCategory(combatTweaks);
	}

	public void registerBindings() {
		ControlBindingData.load(this);
		ControlBindingData.registerBinding(instance, "Tactical Map - Open", "Opens/Closes the Tactical Map UI.", Keyboard.KEY_PERIOD);
		ControlBindingData.registerBinding(instance, "Tactical Map - Toggle Movement Paths", "Toggles the display of movement paths on the Tactical Map.", Keyboard.KEY_LMENU);
		logInfo("Registered Bindings");
	}
}
