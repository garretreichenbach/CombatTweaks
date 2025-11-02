package thederpgamer.combattweaks.network.client;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.server.ai.program.common.TargetProgram;
import org.schema.game.server.ai.program.searchanddestroy.SimpleSearchAndDestroyProgram;
import thederpgamer.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

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
		entity.activateAI(true, true);
		if(!(entity.getAiConfiguration().getAiEntityState().getCurrentProgram() instanceof SimpleSearchAndDestroyProgram)) {
			entity.getAiConfiguration().getAiEntityState().setCurrentProgram(new SimpleSearchAndDestroyProgram(entity.getAiConfiguration().getAiEntityState(), false));
		}
		((TargetProgram<?>) entity.getAiConfiguration().getAiEntityState().getCurrentProgram()).setTarget(target);
		TacticalMapGUIDrawer.getInstance().drawMap.get(entity.getId()).setCurrentTarget(target);
	}
}