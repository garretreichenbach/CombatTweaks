package videogoose.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.manager.DefenseManager;
import videogoose.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendDefensePacket extends Packet {
	private Ship defender;
	private SegmentController defendTarget;

	public SendDefensePacket() {
	}

	public SendDefensePacket(Ship defender, SegmentController defendTarget) {
		this.defender = defender;
		this.defendTarget = defendTarget;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		defender = (Ship) packetReadBuffer.readSendable();
		defendTarget = (SegmentController) packetReadBuffer.readSendable();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeSendable(defender);
		packetWriteBuffer.writeSendable(defendTarget);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.clearTarget(defender);
		DefenseManager.getInstance().addDefense(defender.getId(), defendTarget.getId());
	}
}
