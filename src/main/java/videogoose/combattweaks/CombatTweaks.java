package videogoose.combattweaks;

import api.config.BlockConfig;
import api.mod.StarMod;
import api.network.packets.PacketUtil;
import org.schema.schine.resource.ResourceLoader;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.*;
import videogoose.combattweaks.network.client.*;
import videogoose.combattweaks.network.server.SendArmorHPSyncPacket;

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
		PacketUtil.registerPacket(SendArmorHPSyncPacket.class);
	}
}
