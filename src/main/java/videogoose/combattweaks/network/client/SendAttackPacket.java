package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendAttackPacket extends Packet {
	private int entityId;
	private int targetId;

	public SendAttackPacket() {
	}

	public SendAttackPacket(Ship entity, SegmentController target) {
		entityId = entity.getId();
		targetId = target.getId();
	}

	public SendAttackPacket(int entityId, int targetId) {
		this.entityId = entityId;
		this.targetId = targetId;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		entityId = packetReadBuffer.readInt();
		targetId = packetReadBuffer.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(entityId);
		packetWriteBuffer.writeInt(targetId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.setAttackTarget(entityId, targetId);
	}
}