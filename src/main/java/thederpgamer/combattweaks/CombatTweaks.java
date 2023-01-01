package thederpgamer.combattweaks;

import api.mod.StarMod;
import api.network.packets.PacketUtil;
import thederpgamer.combattweaks.manager.ConfigManager;
import thederpgamer.combattweaks.manager.EventManager;
import thederpgamer.combattweaks.network.server.JumpHudUpdatePacket;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
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
		super.onEnable();
		instance = this;
		ConfigManager.initialize(this);
		EventManager.initialize(this);
		registerPackets();
	}

	private void registerPackets() {
		PacketUtil.registerPacket(JumpHudUpdatePacket.class);
	}
}
