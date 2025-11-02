package thederpgamer.combattweaks;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import glossar.GlossarCategory;
import glossar.GlossarInit;
import org.schema.schine.resource.ResourceLoader;
import thederpgamer.combattweaks.element.ElementManager;
import thederpgamer.combattweaks.manager.ConfigManager;
import thederpgamer.combattweaks.manager.EventManager;
import thederpgamer.combattweaks.manager.ResourceManager;
import thederpgamer.combattweaks.network.client.SendAttackPacket;

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
	}

	@Override
	public void onResourceLoad(ResourceLoader resourceLoader) {
		ResourceManager.loadResources(this);
	}

	@Override
	public void onBlockConfigLoad(BlockConfig blockConfig) {
		ElementManager.initialize();
	}

	private void registerPackets() {
		PacketUtil.registerPacket(SendAttackPacket.class);
	}

	private void initializeGlossary() {
		GlossarInit.initGlossar(this);
		GlossarCategory combatTweaks = new GlossarCategory("Combat Tweaks");
		GlossarInit.addCategory(combatTweaks);
	}
}
