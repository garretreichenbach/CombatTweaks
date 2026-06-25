package videogoose.combattweaks.network.client;

import api.common.GameCommon;
import api.network.Packet;
import api.network.PacketReadBuffer;
import api.network.PacketWriteBuffer;
import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.PlayerUsableInterface;
import org.schema.game.common.controller.elements.effectblock.EffectAddOn;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.PlayerState;
import org.schema.schine.network.objects.Sendable;

import java.io.IOException;

/**
 * Client → server request to fire the vanilla Thrust Blast (Take-Off) effect on the controlled ship. Sent by the
 * double-tap-movement-key detection in {@code EventManager}; the server verifies the ship actually has a booted
 * Thrust Blast chamber before activating its Take-Off effect addon. Ported from BetterChambers.
 */
public class SendThrustBlastPacket extends Packet {

	/** Vanilla "Thrust Blast" reactor chamber (child of the Mobility chamber 1011); no named constant exists for it. */
	private static final short THRUST_BLAST_CHAMBER = 1057;

	private int controllerId;

	public SendThrustBlastPacket() {
	}

	public SendThrustBlastPacket(ManagedUsableSegmentController<?> segmentController) {
		controllerId = segmentController.getId();
	}

	@Override
	public void readPacketData(PacketReadBuffer packetReadBuffer) throws IOException {
		controllerId = packetReadBuffer.readInt();
	}

	@Override
	public void writePacketData(PacketWriteBuffer packetWriteBuffer) throws IOException {
		packetWriteBuffer.writeInt(controllerId);
	}

	@Override
	public void processPacketOnClient() {
	}

	@Override
	public void processPacketOnServer(PlayerState playerState) {
		Sendable sendable = GameCommon.getGameObject(controllerId);
		if(sendable instanceof ManagedUsableSegmentController<?> segmentController) {
			ReactorElement reactorElement = SegmentControllerUtils.getChamberFromElement(segmentController, ElementKeyMap.getInfo(THRUST_BLAST_CHAMBER));
			if(reactorElement != null && reactorElement.isBooted()) {
				PlayerUsableInterface playerUsable = segmentController.getManagerContainer().getPlayerUsable(PlayerUsableInterface.USABLE_ID_TAKE_OFF);
				if(playerUsable instanceof EffectAddOn effectAddOn) {
					effectAddOn.setAutoChargeOn(true);
					if(effectAddOn.canExecute()) {
						effectAddOn.executeModule();
					}
				}
			}
		}
	}
}
