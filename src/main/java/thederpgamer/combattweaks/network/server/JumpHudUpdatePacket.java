package thederpgamer.combattweaks.network.server;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.SendableSegmentController;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.network.objects.Sendable;
import thederpgamer.combattweaks.manager.HudManager;

import java.io.IOException;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class JumpHudUpdatePacket extends Packet {

	private Sendable controller;
	private Vector3i originalSector;
	private Vector3i newSector;

	public JumpHudUpdatePacket() {

	}

	public JumpHudUpdatePacket(SegmentController controller, Vector3i originalSector, Vector3i newSector) {
		this.controller = controller;
		this.originalSector = originalSector;
		this.newSector = newSector;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		controller = packetReadBuffer.readSendable();
		originalSector = packetReadBuffer.readVector();
		newSector = packetReadBuffer.readVector();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeSendable(controller);
		packetWriteBuffer.writeVector(originalSector);
		packetWriteBuffer.writeVector(newSector);
	}

	@Override
	public void processPacketOnClient() {
		HudManager.addNewIncomingJump((SendableSegmentController) controller, originalSector, newSector);
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {

	}
}
