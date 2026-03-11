package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendMinePacket extends Packet {
	private int shipId;
	private int asteroidId;

	public SendMinePacket() {
	}

	public SendMinePacket(Ship ship, SegmentController asteroid) {
		shipId = ship.getId();
		asteroidId = asteroid.getId();
	}

	public SendMinePacket(int shipId, int asteroidId) {
		this.shipId = shipId;
		this.asteroidId = asteroidId;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
		asteroidId = buf.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
		buf.writeInt(asteroidId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.setMineTarget(shipId, asteroidId);
	}
}
