package videogoose.combattweaks.network.server;

import api.common.GameClient;
import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.network.objects.Sendable;
import videogoose.combattweaks.system.armor.ArmorHPCollection;

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
		// Resolve the entity from the CLIENT state explicitly. In single-player the client and server share
		// one JVM; EntityUtils/GameCommon.getGameObject(id) resolves through getGameState() and returns the
		// SERVER instance, whose armor collection has isOnServer()==true — so applySync() no-ops and the
		// client HUD stays at 0. Looking it up in the client state's own object container guarantees we get
		// the client-side collection. (On a real client connected to a dedicated server this is also the
		// correct, unambiguous lookup.)
		SegmentController entity = null;
		try {
			Sendable s = GameClient.getClientState().getLocalAndRemoteObjectContainer().getLocalObjects().get(entityId);
			if(s instanceof SegmentController) {
				entity = (SegmentController) s;
			}
		} catch(Exception ignored) {
		}
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
