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

public class SendDefensePacket extends Packet {

	private int defenderId;
	private int targetId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendDefensePacket() {
	}

	public SendDefensePacket(Ship defender, SegmentController target, boolean queue) {
		defenderId = defender.getId();
		targetId = target.getId();
		this.queue = queue;
	}


	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		defenderId = packetReadBuffer.readInt();
		targetId = packetReadBuffer.readInt();
		queue = packetReadBuffer.readBoolean();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(defenderId);
		packetWriteBuffer.writeInt(targetId);
		packetWriteBuffer.writeBoolean(queue);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		if(!AIUtils.canReceiveOrders(defenderId, playerState)) {
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(defenderId, OrderQueueManager.OrderType.DEFEND, targetId);
		} else {
			OrderQueueManager.getInstance().replace(defenderId, OrderQueueManager.OrderType.DEFEND, targetId);
		}
	}
}
