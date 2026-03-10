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

	private int shipId;

	public SendIdlePacket() {
	}

	public SendIdlePacket(Ship ship) {
		shipId = ship.getId();
	}

	public SendIdlePacket(int shipId) {
		this.shipId = shipId;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		DefenseManager.getInstance().removeDefense(shipId);
		AIUtils.clearTarget(shipId);
	}
}
