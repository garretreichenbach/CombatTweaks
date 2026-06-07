package videogoose.combattweaks.network.server;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.system.signature.IncomingSignature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the full current set of incoming-signature contacts for the receiving player. Sent
 * every detector tick (even when empty, so the client clears contacts that are no longer incoming).
 */
public class SendIncomingSignaturesPacket extends Packet {

	private List<IncomingSignature> signatures = new ArrayList<>();

	public SendIncomingSignaturesPacket() {
	}

	public SendIncomingSignaturesPacket(List<IncomingSignature> signatures) {
		this.signatures = signatures;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		int count = buf.readInt();
		signatures = new ArrayList<>(count);
		for(int i = 0; i < count; i++) {
			IncomingSignature s = new IncomingSignature(buf.readInt());
			s.relPos.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
			s.vel.set(buf.readFloat(), buf.readFloat(), buf.readFloat());
			s.fidelity = buf.readFloat();
			s.relation = buf.readInt();
			s.massDetail = buf.readInt();
			s.mass = buf.readFloat();
			s.kind = buf.readInt();
			signatures.add(s);
		}
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(signatures.size());
		for(IncomingSignature s : signatures) {
			buf.writeInt(s.id);
			buf.writeFloat(s.relPos.x);
			buf.writeFloat(s.relPos.y);
			buf.writeFloat(s.relPos.z);
			buf.writeFloat(s.vel.x);
			buf.writeFloat(s.vel.y);
			buf.writeFloat(s.vel.z);
			buf.writeFloat(s.fidelity);
			buf.writeInt(s.relation);
			buf.writeInt(s.massDetail);
			buf.writeFloat(s.mass);
			buf.writeInt(s.kind);
		}
	}

	@Override
	public void processPacketOnClient() {
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		if(drawer != null) {
			drawer.setIncomingSignatures(signatures);
		}
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
	}
}
