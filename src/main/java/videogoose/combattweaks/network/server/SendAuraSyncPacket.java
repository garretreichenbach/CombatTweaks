package videogoose.combattweaks.network.server;

import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import org.schema.game.common.data.player.PlayerState;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;
import videogoose.combattweaks.system.aura.AuraState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: the full set of active auras whose projector sits in the receiving player's sector. Sent
 * each AuraManager tick; an empty list clears auras the client should no longer draw. The tactical map reads
 * these to render bounding spheres around projecting ships.
 */
public class SendAuraSyncPacket extends Packet {

	private List<AuraState> auras = new ArrayList<>();

	public SendAuraSyncPacket() {
	}

	public SendAuraSyncPacket(List<AuraState> auras) {
		this.auras = auras;
	}

	@Override
	public void readPacketData(PacketReadBuffer buf) throws IOException {
		int count = buf.readInt();
		auras = new ArrayList<>(count);
		for(int i = 0; i < count; i++) {
			AuraState a = new AuraState(buf.readInt());
			a.radius = buf.readFloat();
			a.auraKind = buf.readInt();
			a.powerFraction = buf.readFloat();
			auras.add(a);
		}
	}

	@Override
	public void writePacketData(PacketWriteBuffer buf) throws IOException {
		buf.writeInt(auras.size());
		for(AuraState a : auras) {
			buf.writeInt(a.entityId);
			buf.writeFloat(a.radius);
			buf.writeInt(a.auraKind);
			buf.writeFloat(a.powerFraction);
		}
	}

	@Override
	public void processPacketOnClient() {
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		if(drawer != null) {
			drawer.setActiveAuras(auras);
		}
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
	}
}
