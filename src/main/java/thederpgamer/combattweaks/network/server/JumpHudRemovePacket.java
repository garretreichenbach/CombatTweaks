package thederpgamer.combattweaks.network.server;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
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
public class JumpHudRemovePacket extends Packet {

	private Sendable controller;

	public JumpHudRemovePacket() {
	}

	public JumpHudRemovePacket(SegmentController controller) {
		this.controller = controller;
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		controller = packetReadBuffer.readSendable();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeSendable(controller);
	}

	@Override
	public void processPacketOnClient() {
		HudManager.removeIncomingJump((SendableSegmentController) controller);
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
	}
}
