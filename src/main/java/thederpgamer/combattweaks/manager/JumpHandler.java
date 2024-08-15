package thederpgamer.combattweaks.manager;

import api.common.GameServer;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.network.packets.PacketUtil;
import api.utils.other.HashList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.world.Sector;
import thederpgamer.combattweaks.network.server.JumpHudRemovePacket;
import thederpgamer.combattweaks.network.server.JumpHudUpdatePacket;

/**
 * JumpHandler class that handles jump event. Will send packets to nearby players to notify them of incoming jump.
 * However, if a player is not near the jump destination, any info sent to them will be removed.
 *
 * @author TheDerpGamer
 */
public class JumpHandler {

	private static final HashList<Vector3i, PlayerState> playerNotifMap = new HashList<>();

	public static void onJumpEngage(ShipJumpEngageEvent event) {
		//Todo: Remove marker when jump is completed
		if(!event.getController().isOnServer()) return;
		try {
			Sector sector = GameServer.getUniverse().getSector(event.getNewSector());
			for(PlayerState playerState : GameServer.getServerState().getPlayerStatesByName().values()) {
				if(!sector.getRemoteSector()._getCurrentPlayers().contains(playerState)) {
					if(playerNotifMap.containsKey(sector.pos)) playerNotifMap.getList(sector.pos).remove(playerState);
					PacketUtil.sendPacket(playerState, new JumpHudRemovePacket(event.getController()));
				} else {
					if(!playerNotifMap.containsKey(sector.pos)) {
						playerNotifMap.add(sector.pos, playerState);
						PacketUtil.sendPacket(playerState, new JumpHudUpdatePacket(event.getController()));
					}
				}
			}
		} catch(Exception exception) {
			exception.printStackTrace();
		}

//		event.setCanceled(true); //Cancel the event, so we can handle the jump ourselves
//		final int distance = getDistance(event.getOriginalSectorPos(), event.getNewSector());
//		final float multiplier = (event.getController().getConfigManager().apply(StatusEffectType.JUMP_DISTANCE, VoidElementManager.REACTOR_JUMP_DISTANCE_DEFAULT));
//		events.put(event.getController(), false);
//		event.getController().getNetworkObject().graphicsEffectModifier.add((byte) 1);
//		events.put(event.getController(), true);
//		event.getController().engageJump((int) multiplier);
	}

	private static int getDistance(Vector3i originalSector, Vector3i newSector) {
		return Math.abs(originalSector.x - newSector.x) + Math.abs(originalSector.y - newSector.y) + Math.abs(originalSector.z - newSector.z);
	}
}
