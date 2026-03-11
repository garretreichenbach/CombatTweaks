package videogoose.combattweaks;

import api.config.BlockConfig;
import api.listener.events.controller.ClientInitializeEvent;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import glossar.GlossarCategory;
import glossar.GlossarInit;
import org.schema.schine.resource.ResourceLoader;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.*;
import videogoose.combattweaks.network.client.*;

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
		MoveManager.getInstance(); // Initialize move manager
		MineManager.getInstance(); // Initialize mine manager
		RepairManager.getInstance(); // Initialize repair manager
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
	}

	private void initializeGlossary() {
		GlossarInit.initGlossar(this);
		GlossarCategory combatTweaks = new GlossarCategory("Combat Tweaks");
		GlossarInit.addCategory(combatTweaks);
	}
}
