package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import api.utils.game.PlayerUtils;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.OrderQueueManager;
import videogoose.combattweaks.utils.AIUtils;
import videogoose.combattweaks.utils.EntityUtils;

import java.io.IOException;

public class SendMinePacket extends Packet {
	private int shipId;
	private int asteroidId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendMinePacket() {
	}

	public SendMinePacket(Ship ship, SegmentController asteroid, boolean queue) {
		shipId = ship.getId();
		asteroidId = asteroid.getId();
		this.queue = queue;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
		asteroidId = buf.readInt();
		queue = buf.readBoolean();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
		buf.writeInt(asteroidId);
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
		SegmentController ship = EntityUtils.getEntityById(shipId);
		if(!AIUtils.hasSalvageBeams(ship)) {
			if(playerState != null) {
				PlayerUtils.sendMessage(playerState, (ship != null ? ship.getName() : "Ship") + " has no salvage beams to mine with.");
			}
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(shipId, OrderQueueManager.OrderType.MINE, asteroidId);
		} else {
			OrderQueueManager.getInstance().replace(shipId, OrderQueueManager.OrderType.MINE, asteroidId);
		}
	}
}
