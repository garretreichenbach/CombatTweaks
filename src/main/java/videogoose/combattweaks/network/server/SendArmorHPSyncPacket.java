package videogoose.combattweaks.network.server;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.utils.EntityUtils;

import java.io.IOException;

public class SendArmorHPSyncPacket extends Packet {
	private int entityId;
	private double currentHP;
	private double maxHP;

	public SendArmorHPSyncPacket() {
	}

	public SendArmorHPSyncPacket(int entityId, double currentHP, double maxHP) {
		this.entityId = entityId;
		this.currentHP = currentHP;
		this.maxHP = maxHP;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		entityId = packetReadBuffer.readInt();
		currentHP = packetReadBuffer.readDouble();
		maxHP = packetReadBuffer.readDouble();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(entityId);
		packetWriteBuffer.writeDouble(currentHP);
		packetWriteBuffer.writeDouble(maxHP);
	}

	@Override
	public void processPacketOnClient() {
		SegmentController entity = EntityUtils.getEntityById(entityId);
		if(entity instanceof ManagedUsableSegmentController<?>) {
			ArmorHPCollection collection = ArmorHPCollection.getCollection(entity);
			if(collection != null) {
				collection.applySync(currentHP, maxHP);
			}
		}
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
	}
}
