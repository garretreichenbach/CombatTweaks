package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.DefenseManager;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendDefensePacket extends Packet {

	private int defenderId;
	private int targetId;

	public SendDefensePacket() {
	}

	public SendDefensePacket(Ship defender, SegmentController target) {
		defenderId = defender.getId();
		targetId = target.getId();
	}

	public SendDefensePacket(int defenderId, int targetId) {
		this.defenderId = defenderId;
		this.targetId = targetId;
	}


	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		defenderId = packetReadBuffer.readInt();
		targetId = packetReadBuffer.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(defenderId);
		packetWriteBuffer.writeInt(targetId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.clearTarget(defenderId);
		DefenseManager.getInstance().addDefense(defenderId, targetId);
	}
}
