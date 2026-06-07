package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.network.packets.PacketUtil;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.network.server.SendArmorHPSyncPacket;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.utils.EntityUtils;

import java.io.IOException;

/**
 * Client -> server: "send me this entity's armor HP now." The armor heartbeat is deliberately slow to keep
 * idle traffic down, so when the tactical map starts showing an entity (HP rings / selection panel) whose
 * armor the client hasn't received yet, it asks once and the server replies immediately to that player.
 */
public class RequestArmorSyncPacket extends Packet {

	private int entityId;

	public RequestArmorSyncPacket() {
	}

	public RequestArmorSyncPacket(int entityId) {
		this.entityId = entityId;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		entityId = buf.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(entityId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		SegmentController entity = EntityUtils.getEntityById(entityId);
		if(entity == null) {
			return;
		}
		ArmorHPCollection collection = ArmorHPCollection.getCollection(entity);
		if(collection != null && collection.getMaxHP() > 0) {
			PacketUtil.sendPacket(playerState, new SendArmorHPSyncPacket(entityId, collection.getCurrentHP(), collection.getMaxHP()));
		}
	}
}
