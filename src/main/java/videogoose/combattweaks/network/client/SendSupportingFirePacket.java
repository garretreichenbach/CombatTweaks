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

/**
 * Client → server "Supporting Fire" order: engage the target like a normal attack, but hold position instead of
 * closing to orbit range and strafing. The ship still rotates to face the target and fires; the engaging-state
 * movement is suppressed by {@code EngagingTargetSteadyMixin} for ships flagged via {@link AIUtils#setSupportingFire}.
 */
public class SendSupportingFirePacket extends Packet {

	private int entityId;
	private int targetId;
	/** When true, append after existing orders instead of replacing them (shift-held). */
	private boolean queue;

	public SendSupportingFirePacket() {
	}

	public SendSupportingFirePacket(Ship entity, SegmentController target, boolean queue) {
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
		// Supporting Fire is still a combat order — a weaponless ship has nothing to support with.
		SegmentController ship = EntityUtils.getEntityById(entityId);
		if(ship != null && !ship.isDocked() && !AIUtils.hasWeapons(ship)) {
			PlayerUtils.sendMessage(playerState, ship.getName() + " has no weapons and can't attack.");
			return;
		}
		if(queue) {
			OrderQueueManager.getInstance().enqueue(entityId, OrderQueueManager.OrderType.SUPPORTING_FIRE, targetId);
		} else {
			OrderQueueManager.getInstance().replace(entityId, OrderQueueManager.OrderType.SUPPORTING_FIRE, targetId);
		}
	}
}
