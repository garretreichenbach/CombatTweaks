package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.PlayerUtils;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.OrderQueueManager;
import videogoose.combattweaks.utils.AIUtils;
import videogoose.combattweaks.utils.EntityUtils;

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
		// A ship with no repair (astrotech) beams can't repair anything, so ignore the order rather than
		// sending it to orbit the target doing nothing.
		SegmentController ship = EntityUtils.getEntityById(shipId);
		if(ship != null && !AIUtils.hasRepairBeams(ship)) {
			PlayerUtils.sendMessage(playerState, ship.getName() + " has no repair beams and can't repair.");
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(shipId, OrderQueueManager.OrderType.REPAIR, targetId);
		} else {
			OrderQueueManager.getInstance().replace(shipId, OrderQueueManager.OrderType.REPAIR, targetId);
		}
	}
}
