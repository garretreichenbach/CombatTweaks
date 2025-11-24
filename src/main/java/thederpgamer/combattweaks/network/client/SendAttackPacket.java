package thederpgamer.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import thederpgamer.combattweaks.utils.AIUtils;

import java.io.IOException;

public class SendAttackPacket extends Packet {
	private Ship entity;
	private SegmentController target;

	public SendAttackPacket() {
	}

	public SendAttackPacket(Ship entity, SegmentController target) {
		this.entity = entity;
		this.target = target;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		entity = (Ship) packetReadBuffer.readSendable();
		target = (SegmentController) packetReadBuffer.readSendable();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeSendable(entity);
		packetWriteBuffer.writeSendable(target);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		AIUtils.setTarget(entity, target);
	}
}