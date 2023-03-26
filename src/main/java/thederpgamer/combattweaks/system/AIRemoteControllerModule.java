package thederpgamer.combattweaks.system;

import api.common.GameClient;
import api.common.GameServer;
import api.network.packets.PacketUtil;
import api.utils.game.module.util.SimpleDataStorageMCModule;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.data.PlayerControllable;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.data.SegmentPiece;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.network.objects.Sendable;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.element.ElementManager;
import thederpgamer.combattweaks.manager.ConfigManager;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class AIRemoteControllerModule extends SimpleDataStorageMCModule {

	public static boolean inRange(SegmentController target) {
		PlayerControllable playerControllable = GameClient.getCurrentControl();
		if(playerControllable instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> managedUsableSegmentController = (ManagedUsableSegmentController<?>) playerControllable;
			if(target.equals(managedUsableSegmentController)) return false;
			else {
				Vector3i thisSector = managedUsableSegmentController.getSector(new Vector3i());
				Vector3i otherSector = target.getSector(new Vector3i());
				float distance = Vector3fTools.distance(thisSector.x, thisSector.y, thisSector.z, otherSector.x, otherSector.y, otherSector.z);
				return distance <= ConfigManager.getSystemConfig().getDouble("ai-remote-controller-max-range");
			}
		} else return false;
	}

	public static AIRemoteControllerModule getModule(SegmentController controller) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			ManagedUsableSegmentController<?> managedUsableSegmentController = (ManagedUsableSegmentController<?>) controller;
			return (AIRemoteControllerModule) managedUsableSegmentController.getManagerContainer().getModMCModule(ElementManager.getBlock("AI Remote Controller").getId());
		} else return null;
	}

	public static AIRemoteControllerData getData(SegmentPiece segmentPiece) {
		AIRemoteControllerModule module = getModule(segmentPiece.getSegmentController());
		return module.getControllerData(segmentPiece.getAbsoluteIndex());
	}

	private static final float UPDATE_TIMER = 1000.0f;
	private float timer;

	public AIRemoteControllerModule(SegmentController ship, ManagerContainer<?> managerContainer) {
		super(ship, managerContainer, CombatTweaks.getInstance(), ElementManager.getBlock("AI Remote Controller").getId());
		timer = UPDATE_TIMER;
	}

	@Override
	public String getName() {
		return "AI Remote Controller";
	}

	@Override
	public void handle(Timer timer) {
		super.handle(timer);
		if(this.timer <= 0) {
			updateSystemData();
			this.timer = UPDATE_TIMER;
		} else this.timer--;
	}

	@Override
	public void handlePlace(long absIndex, byte orientation) {
		super.handlePlace(absIndex, orientation);
		updateSystemData();
	}

	@Override
	public void handleRemove(long absIndex) {
		super.handleRemove(absIndex);
		updateSystemData();
	}

	@Override
	public double getPowerConsumedPerSecondResting() {
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
	}

	@Override
	public void dischargeFully() {
	}

	public void updateSystemData() {
		flagUpdatedData();
	}

	public AIRemoteControllerData getControllerData(long absoluteIndex) {
		AIRemoteControllerDataContainer dataContainer = getDataContainer();
		if(dataContainer.dataMap.containsKey(absoluteIndex)) return dataContainer.dataMap.get(absoluteIndex);
		else return null;
	}

	private AIRemoteControllerDataContainer getDataContainer() {
		if(data == null) data = new AIRemoteControllerDataContainer();
		return (AIRemoteControllerDataContainer) data;
	}

	public static class AIRemoteControllerDataContainer {
		public final Long2ObjectArrayMap<AIRemoteControllerData> dataMap = new Long2ObjectArrayMap<>();
	}

	public static class AIRemoteControllerData {
		public final long absoluteIndex;
		public final long targetId;

		public AIRemoteControllerData(long absoluteIndex, long targetId) {
			this.absoluteIndex = absoluteIndex;
			this.targetId = targetId;
		}

		public SegmentController getTarget() {
			if(GameServer.getServerState() != null) {
				for(SegmentController controller : GameServer.getServerState().getSegmentControllersByName().values()) {
					if(controller.getDbId() == targetId) return controller;
				}
			} else if(GameClient.getClientState() != null) {
				for(Sendable sendable : GameClient.getClientState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
					if(sendable instanceof SegmentController) {
						SegmentController controller = (SegmentController) sendable;
						if(controller.getDbId() == targetId) return controller;
					}
				}
			}
			return null;
		}

		public void activateControl() {
			SegmentController controller = getTarget();
			if(controller != null) {
				if(inRange(controller) && controller.isFullyLoadedWithDock()) {
					if(!controller.isOnServer()) GameClient.getClientPlayerState().getControllerState().requestControl(GameClient.getCurrentControl());
					else PacketUtil.sendPacket();
				}
			}
		}
	}
}
