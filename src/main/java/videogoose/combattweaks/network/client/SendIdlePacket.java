package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.DefenseManager;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendIdlePacket extends Packet {
	private Ship ship;

	public SendIdlePacket() {}

	public SendIdlePacket(Ship ship) {
		this.ship = ship;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		ship = (Ship) buf.readSendable();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeSendable(ship);
	}

	@Override
	public void processPacketOnClient() {}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		DefenseManager.getInstance().removeDefense(ship.getId());
		AIUtils.clearTarget(ship);
	}
}
