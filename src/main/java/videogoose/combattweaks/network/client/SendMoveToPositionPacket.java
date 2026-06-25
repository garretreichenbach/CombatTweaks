package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.OrderQueueManager;
import videogoose.combattweaks.utils.AIUtils;

import javax.vecmath.Vector3f;
import java.io.IOException;

/**
 * Client → server "move to position" order: fly the ship to an arbitrary point in empty space (not an entity).
 * The point is placed on the tactical map via the two-click + altitude-stalk placement UI and carried here as a
 * world position; the server routes it through {@link OrderQueueManager} to a fixed-point {@code MoveManager} move.
 */
public class SendMoveToPositionPacket extends Packet {

	private int shipId;
	private float x;
	private float y;
	private float z;
	private int sectorId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendMoveToPositionPacket() {
	}

	public SendMoveToPositionPacket(int shipId, Vector3f position, int sectorId, boolean queue) {
		this.shipId = shipId;
		this.x = position.x;
		this.y = position.y;
		this.z = position.z;
		this.sectorId = sectorId;
		this.queue = queue;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		shipId = buf.readInt();
		x = buf.readFloat();
		y = buf.readFloat();
		z = buf.readFloat();
		sectorId = buf.readInt();
		queue = buf.readBoolean();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(shipId);
		buf.writeFloat(x);
		buf.writeFloat(y);
		buf.writeFloat(z);
		buf.writeInt(sectorId);
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
		Vector3f position = new Vector3f(x, y, z);
		if(queue) {
			OrderQueueManager.getInstance().enqueueMoveToPosition(shipId, position, sectorId);
		} else {
			OrderQueueManager.getInstance().replaceMoveToPosition(shipId, position, sectorId);
		}
	}
}
