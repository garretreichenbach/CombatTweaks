package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendMoveToPacket extends Packet {
	private int shipId;
	private int targetId;

	public SendMoveToPacket() {
	}

	public SendMoveToPacket(Ship ship, SegmentController target) {
		shipId = ship.getId();
		targetId = target.getId();
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
		targetId = buf.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
		buf.writeInt(targetId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.setMoveToTarget(shipId, targetId);
	}
}
