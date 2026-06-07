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

public class SendRepairPacket extends Packet {
	private int shipId;
	private int targetId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendRepairPacket() {
	}

	public SendRepairPacket(Ship ship, SegmentController target, boolean queue) {
		shipId = ship.getId();
		targetId = target.getId();
		this.queue = queue;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
		targetId = buf.readInt();
		queue = buf.readBoolean();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
		buf.writeInt(targetId);
		buf.writeBoolean(queue);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		if(!AIUtils.canReceiveOrders(shipId, playerState)) {
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(shipId, OrderQueueManager.OrderType.REPAIR, targetId);
		} else {
			OrderQueueManager.getInstance().replace(shipId, OrderQueueManager.OrderType.REPAIR, targetId);
		}
	}
}
