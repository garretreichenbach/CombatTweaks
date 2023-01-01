package thederpgamer.combattweaks.manager;

import api.common.GameServer;
import api.listener.events.entity.ShipJumpEngageEvent;
import api.network.packets.PacketUtil;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.blockeffects.config.StatusEffectType;
import org.schema.game.common.data.player.PlayerState;
import thederpgamer.combattweaks.network.server.JumpHudUpdatePacket;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class JumpHandler {

	public static void onJumpEngage(final ShipJumpEngageEvent event) {
		assert event.isServer();
		//Get all nearby players and send a hud update packet to notify them of incoming jump
		for(PlayerState playerState : GameServer.getServerState().getPlayerStatesByName().values()) {
			if(getDistance(playerState.getCurrentSector(), event.getController().getSector(new Vector3i())) <= 10) PacketUtil.sendPacket(playerState, new JumpHudUpdatePacket(event.getController(), event.getOriginalSectorPos(), event.getNewSector()));
		}

		event.setCanceled(true); //Cancel the event, so we can handle the jump ourselves
		final int distance = getDistance(event.getOriginalSectorPos(), event.getNewSector());
		final float multiplier = (event.getController().getConfigManager().apply(StatusEffectType.JUMP_DISTANCE, VoidElementManager.REACTOR_JUMP_DISTANCE_DEFAULT));
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(distance * 1500L); //Make the jump take longer relative to the distance
				} catch(InterruptedException exception) {
					exception.printStackTrace();
				} finally {
					event.getController().engageJump((int) multiplier);
				}
			}
		}.start();
	}

	private static int getDistance(Vector3i originalSector, Vector3i newSector) {
		return Math.abs(originalSector.x - newSector.x) + Math.abs(originalSector.y - newSector.y) + Math.abs(originalSector.z - newSector.z);
	}
}
