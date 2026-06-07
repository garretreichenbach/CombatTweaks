package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.OrderQueueManager;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendAttackPacket extends Packet {
	private int entityId;
	private int targetId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendAttackPacket() {
	}

	public SendAttackPacket(Ship entity, SegmentController target, boolean queue) {
		entityId = entity.getId();
		targetId = target.getId();
		this.queue = queue;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		entityId = packetReadBuffer.readInt();
		targetId = packetReadBuffer.readInt();
		queue = packetReadBuffer.readBoolean();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(entityId);
		packetWriteBuffer.writeInt(targetId);
		packetWriteBuffer.writeBoolean(queue);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		if(!AIUtils.canReceiveOrders(entityId, playerState)) {
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(entityId, OrderQueueManager.OrderType.ATTACK, targetId);
		} else {
			OrderQueueManager.getInstance().replace(entityId, OrderQueueManager.OrderType.ATTACK, targetId);
		}
	}
}