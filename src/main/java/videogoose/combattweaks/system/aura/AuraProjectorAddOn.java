package videogoose.combattweaks.system.aura;

import api.common.GameClient;
import api.common.GameCommon;
import api.common.GameServer;
import api.listener.events.systems.ReactorRecalibrateEvent;
import api.utils.addon.SimpleAddOn;
import api.utils.game.PlayerUtils;
import api.utils.game.SegmentControllerUtils;
import api.utils.sound.AudioUtils;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.blockeffects.config.ConfigEntityManager;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.FactionManager;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.GameServerState;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.network.objects.Sendable;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.effect.StatusEffectRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;
import videogoose.combattweaks.manager.AuraManager;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.utils.EntityUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-activatable Aura Projector. While active it applies its support effects (shield capacity) to friendly
 * ships inside its range and maintains an "aura power" pool that the Aura Disruptor (and ship damage, Phase 3)
 * drain. Its current sphere is reported to {@link AuraManager} so the tactical map can draw it as a bounding
 * sphere. Ported and modernized from BetterChambers; the old client pulse rendering is replaced by the
 * persistent sphere sync.
 */
public class AuraProjectorAddOn extends SimpleAddOn {

	public static final int UPDATE_TIMER = 300;
	private final ArrayList<ConfigEffectGroup> effectsToApply = new ArrayList<>();
	private final ConcurrentHashMap<SegmentController, Boolean> targetingEntities = new ConcurrentHashMap<>();
	private final float disruptionPowerMultiplier;
	private final float auraRegenPercentPerUpdate;
	private boolean usable;
	private int ticks;
	private float maxRange;
	private double auraPower;
	private double auraMaxPower;

	public AuraProjectorAddOn(ManagerContainer<?> managerContainer) {
		super(managerContainer, BlockRegistry.AURA_PROJECTOR_CHAMBER.getId(), CombatTweaks.getInstance(), "AuraProjectorChamber");
		onReactorRecalibrate(null);
		disruptionPowerMultiplier = (float) ConfigManager.getSystemConfig().auraDisruptorPowerMultiplier.value.doubleValue();
		auraRegenPercentPerUpdate = (float) ConfigManager.getSystemConfig().auraRegenPercentPerUpdate.value.doubleValue();
	}

	private static boolean canAffect(SegmentController controllerA, SegmentController controllerB) {
		boolean canAffectRoot = ConfigManager.getMainConfig().auraAffectsRoot.getValue();
		if(!(controllerA instanceof ManagedUsableSegmentController<?>) || !(controllerB instanceof ManagedUsableSegmentController<?>)) {
			return false;
		}
		ManagedUsableSegmentController<?> rootA = (ManagedUsableSegmentController<?>) controllerA.railController.getRoot();
		ManagedUsableSegmentController<?> rootB = (ManagedUsableSegmentController<?>) controllerB.railController.getRoot();
		if(!rootA.getManagerContainer().hasActiveReactors() || !rootB.getManagerContainer().hasActiveReactors()) {
			return false;
		}
		float rootALevel = rootA.getManagerContainer().getPowerInterface().getActiveReactor().getLevelReadable() + 1;
		float rootBLevel = rootB.getManagerContainer().getPowerInterface().getActiveReactor().getLevelReadable() + 1;
		float minSizePercent = (float) ConfigManager.getSystemConfig().auraMinSizePercent.value.doubleValue();
		return rootALevel / rootBLevel > minSizePercent && (canAffectRoot || !rootA.equals(rootB));
	}

	@Override
	public void onReactorRecalibrate(ReactorRecalibrateEvent event) {
		try {
			if(!(getSegmentController() instanceof ManagedUsableSegmentController<?>)) {
				return; //Prevents asteroid-related crashes
			}
			ManagedUsableSegmentController<?> controller = (ManagedUsableSegmentController<?>) getManagedSegmentController();
			if(controller.getManagerContainer() == null || controller.getManagerContainer().getPowerInterface() == null || controller.getManagerContainer().getPowerInterface().getActiveReactor() == null) {
				return;
			}
			ReactorElement auraBase = SegmentControllerUtils.getChamberFromElement(controller, BlockRegistry.AURA_PROJECTOR_CHAMBER.getInfo());
			if(auraBase != null && auraBase.isAllValidOrUnspecified()) {
				usable = true;
				effectsToApply.clear();
				auraPower = auraBase.calculateHpRecursively();
				auraMaxPower = auraBase.calculateHpRecursively();
				if(isActive()) {
					ConfigEntityManager configManager = getManagerUsableSegmentController().getConfigManager();
					if(configManager.getModules().containsKey(StatusEffectRegistry.AURA_RANGE)) {
						maxRange = (configManager.getModules().get(StatusEffectRegistry.AURA_RANGE).getFloatValue()) * (Integer) ServerConfig.SECTOR_SIZE.getCurrentState();
					}
					ReactorElement shieldAuraCap1 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.SHIELD_AURA_CAPACITY_CHAMBER_1.getInfo());
					if(shieldAuraCap1 != null && shieldAuraCap1.isAllValidOrUnspecified()) {
						effectsToApply.add(ConfigGroupRegistry.SHIELD_AURA_CAPACITY_EFFECT_1.configEffectGroup);
					}
					ReactorElement shieldAuraCap2 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.SHIELD_AURA_CAPACITY_CHAMBER_2.getInfo());
					if(shieldAuraCap2 != null && shieldAuraCap2.isAllValidOrUnspecified()) {
						effectsToApply.add(ConfigGroupRegistry.SHIELD_AURA_CAPACITY_EFFECT_2.configEffectGroup);
					}
				} else {
					removeEntityEffects();
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error initializing Aura Projector", exception);
		}
	}

	@Override
	public float getChargeRateFull() {
		return 3;
	}

	@Override
	public double getPowerConsumedPerSecondResting() {
		double powerConsumed = 0.0;
		try {
			if(isActive() && !targetingEntities.isEmpty()) {
				for(Map.Entry<SegmentController, Boolean> entry : targetingEntities.entrySet()) {
					if(entry.getKey() instanceof ManagedSegmentController<?>) {
						powerConsumed += entry.getKey().getMass() / 10.0f;
					}
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error calculating Aura Projector power consumption", exception);
		}
		return powerConsumed;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return 0;
	}

	@Override
	public boolean isPlayerUsable() {
		return usable;
	}

	@Override
	public float getDuration() {
		return -1;
	}

	@Override
	public boolean isActive() {
		return super.isActive() && !isDisrupted();
	}

	@Override
	public boolean executeModule() {
		super.executeModule();
		if(usable && isActive()) {
			onReactorRecalibrate(null);
			execute();
		} else {
			removeEntityEffects();
		}
		return usable;
	}

	@Override
	public void onAttemptToExecute() {
		if(getState() instanceof GameServerState) {
			if(isActive()) {
				AudioUtils.serverPlaySound("0022_spaceship user - special synthetic weapon recharged 1", 10.0f, 1.0f, getAttachedPlayers());
			} else {
				AudioUtils.serverPlaySound("0022_spaceship user - special synthetic weapon recharged 2", 10.0f, 1.0f, getAttachedPlayers());
			}
		}
	}

	@Override
	public boolean onExecuteServer() {
		return true;
	}

	@Override
	public boolean onExecuteClient() {
		return true;
	}

	@Override
	public void onDeactivateFromTime() {
		removeEntityEffects();
		targetingEntities.clear();
		clearAura();
	}

	@Override
	public void onActive() {
		if(ticks >= UPDATE_TIMER) {
			updateTargetList();
			auraPower = Math.min(auraPower + (auraPower * auraRegenPercentPerUpdate), auraMaxPower);
			reportAura();
			ticks = 0;
		} else {
			ticks++;
		}
	}

	@Override
	public void onInactive() {
		if(ticks >= UPDATE_TIMER) {
			removeEntityEffects();
			targetingEntities.clear();
			clearAura();
			onReactorRecalibrate(null);
			ticks = 0;
		} else {
			ticks++;
		}
	}

	@Override
	public String getName() {
		return "Aura Projector";
	}

	@Override
	public boolean isDeactivatableManually() {
		return isActive();
	}

	public void updateTargetList() {
		ArrayList<SegmentController> toRemove = new ArrayList<>();
		for(Map.Entry<SegmentController, Boolean> entry : targetingEntities.entrySet()) {
			SegmentController target = entry.getKey();
			if(entry.getValue()) {
				for(ConfigEffectGroup configGroup : effectsToApply) {
					target.getConfigManager().addEffectAndSend(configGroup, true, target.getNetworkObject());
				}
				entry.setValue(false);
			}
			float distance = EntityUtils.getDistance(target, getSegmentController());
			if(distance > maxRange) {
				toRemove.add(target);
			}
		}
		//Remove invalid entries
		if(!toRemove.isEmpty()) {
			for(SegmentController target : toRemove) {
				if(target.getId() != getSegmentController().getId()) {
					for(ConfigEffectGroup configGroup : effectsToApply) {
						target.getConfigManager().removeEffectAndSend(configGroup, true, target.getNetworkObject());
					}
				}
			}
		}
		if(GameServer.getServerState() != null) {
			addEntityEffects(); //Add any new valid targets
		}
	}

	private void execute() {
		if(!isOnServer()) {
			return;
		}
		removeEntityEffects();
		addEntityEffects();
		reportAura();
		try {
			AudioUtils.serverPlaySound("0022_spaceship user - turbo boost large", 10.0F, 1.0F, getAttachedPlayers());
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error playing Aura Projector sound", exception);
		}
	}

	public void removeEntityEffects() {
		for(Map.Entry<SegmentController, Boolean> entry : targetingEntities.entrySet()) {
			if(entry.getKey().getId() != getSegmentController().getId()) {
				for(ConfigEffectGroup configGroup : effectsToApply) {
					entry.getKey().getConfigManager().removeEffectAndSend(configGroup, true, entry.getKey().getNetworkObject());
				}
			}
		}
	}

	public void addEntityEffects() {
		assert isOnServer();
		try {
			for(Sendable sendable : GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
				if(sendable instanceof SegmentController) {
					SegmentController entity = (SegmentController) sendable;
					if(!entity.isFullyLoadedWithDock()) {
						continue;
					}
					for(ConfigEffectGroup configGroup : effectsToApply) {
						if(entity.getId() != getSegmentController().getId() && !entity.equals(getSegmentController()) && !entity.equals(getSegmentController().railController.getRoot())) {
							if(!canAffect(getSegmentController(), entity)) {
								continue;
							}
							int currentFactionId = getSegmentController().getFactionId();
							int entityFactionId = entity.getFactionId();
							if(currentFactionId > 0 && entityFactionId > 0 && Objects.requireNonNull(GameCommon.getGameState()).getFactionManager().getRelation(currentFactionId, entityFactionId) == FactionRelation.RType.FRIEND) {
								float distance = EntityUtils.getDistance(entity, getSegmentController());
								if(distance <= maxRange && !targetingEntities.containsKey(entity)) {
									entity.getConfigManager().addEffectAndSend(configGroup, true, entity.getNetworkObject());
									targetingEntities.put(entity, true);
								}
							}
						} else {
							entity.getConfigManager().removeEffectAndSend(configGroup, true, entity.getNetworkObject());
							targetingEntities.remove(entity);
						}
					}
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error adding Aura Projector effects to entities", exception);
		}
	}

	public boolean isDisrupted() {
		return auraPower < auraMaxPower;
	}

	public void disrupt(SimpleTransformableSendableObject<?> shootingEntity, float disruptAmount) {
		if(isActive() && !targetingEntities.isEmpty()) {
			auraPower = Math.max(0, auraPower - (disruptAmount * disruptionPowerMultiplier));
			if(auraPower <= 0) {
				auraPower = 0;
				clearAura();
				if(getState() instanceof GameServerState) {
					AudioUtils.serverPlaySound("0022_spaceship user - special synthetic weapon recharged 1", 10.0f, 1.0f, getAttachedPlayers());
					for(PlayerState playerState : getAttachedPlayers()) {
						playerState.sendServerMessagePlayerWarning(new Object[] {"Aura Projector disrupted by \"" + shootingEntity.getName() + "\"!"});
					}
				} else {
					AudioUtils.clientPlaySound("0022_spaceship user - special synthetic weapon recharged 1", 10.0f, 1.0f);
					if(PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()).equals(getSegmentController())) {
						((GameClientState) getState()).getWorldDrawer().getGuiDrawer().notifyEffectHit(getSegmentController(), EffectElementManager.OffensiveEffects.STOP);
					}
				}
				for(Map.Entry<SegmentController, Boolean> entry : targetingEntities.entrySet()) {
					SegmentController entity = entry.getKey();
					if(!entry.getValue()) {
						for(ConfigEffectGroup configGroup : effectsToApply) {
							entity.getConfigManager().removeEffectAndSend(configGroup, true, entity.getNetworkObject());
						}
					}
					entry.setValue(true);
				}
			} else {
				reportAura();
			}
			deactivateManually();
		}
	}

	/** Push the current sphere (radius + power fraction) to the server-side AuraManager for client rendering. */
	private void reportAura() {
		if(!isOnServer()) {
			return;
		}
		try {
			SegmentController sc = getSegmentController();
			float frac = auraMaxPower > 0 ? (float) (auraPower / auraMaxPower) : 1.0f;
			AuraManager.getInstance().report(new AuraState(sc.getId(), sc.getSectorId(), maxRange, AuraState.KIND_SUPPORT, frac));
		} catch(Exception ignored) {
		}
	}

	/** Stop advertising this projector's aura to clients. */
	private void clearAura() {
		if(!isOnServer()) {
			return;
		}
		try {
			AuraManager.getInstance().clear(getSegmentController().getId());
		} catch(Exception ignored) {
		}
	}

	public double getAuraPower() {
		return auraPower;
	}

	public double getAuraMaxPower() {
		return auraMaxPower;
	}

	public float getMaxRange() {
		return maxRange;
	}

	public String[] getEffects() {
		String[] effects = new String[effectsToApply.size()];
		int i = 0;
		for(ConfigEffectGroup configGroup : effectsToApply) {
			effects[i] = configGroup.id.trim();
			i++;
		}
		return effects;
	}
}
