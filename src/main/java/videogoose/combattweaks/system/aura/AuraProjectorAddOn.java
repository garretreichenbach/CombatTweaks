package videogoose.combattweaks.system.aura;

import api.common.GameClient;
import api.common.GameCommon;
import api.common.GameServer;
import api.listener.events.systems.ReactorRecalibrateEvent;
import api.utils.addon.SimpleAddOn;
import api.utils.game.PlayerUtils;
import api.utils.game.SegmentControllerUtils;
import api.utils.sound.AudioUtils;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.PlayerUsableInterface;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.blockeffects.config.ConfigEntityManager;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.game.server.data.GameServerState;
import org.schema.game.server.data.ServerConfig;
import org.schema.schine.network.objects.Sendable;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.effect.ConfigEffectGroup;
import videogoose.combattweaks.effect.StatusEffectRegistry;
import videogoose.combattweaks.manager.AuraManager;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.utils.EntityUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base for a player-activatable aura projector. While active it applies its effect groups to ships of a
 * particular faction relation inside its range and maintains an "aura power" pool that the Aura Disruptor and ship
 * damage drain. Its current sphere is reported to {@link AuraManager} so the tactical map can draw it.
 * <p>
 * Concrete roles supply the base chamber, the faction relation they target, the aura kind (for sphere colour),
 * and which sub-chambers map to which effect groups:
 * <ul>
 *   <li>{@link SupportAuraAddOn} — buffs friends (support reactor tree)</li>
 *   <li>{@link OffenseAuraAddOn} — debuffs enemies (offense reactor tree)</li>
 * </ul>
 * The two are mutually exclusive on a ship, so at most one is ever active. Both addon instances are attached to
 * every ship; the one whose base chamber is absent stays non-usable and never touches {@link AuraManager}.
 * Ported and modernized from BetterChambers; the old client pulse rendering is replaced by persistent sphere sync.
 */
public abstract class AuraProjectorAddOn extends SimpleAddOn {

	public static final int UPDATE_TIMER = 300;
	protected final ArrayList<ConfigEffectGroup> effectsToApply = new ArrayList<>();
	private final ConcurrentHashMap<SegmentController, Boolean> targetingEntities = new ConcurrentHashMap<>();
	private final float disruptionPowerMultiplier;
	private final float auraRegenPercentPerUpdate;
	protected boolean usable;
	private int ticks;
	private float maxRange;
	private double auraPower;
	private double auraMaxPower;

	protected AuraProjectorAddOn(ManagerContainer<?> managerContainer, short baseChamberId, String reservedId) {
		super(managerContainer, baseChamberId, CombatTweaks.getInstance(), reservedId);
		onReactorRecalibrate(null);
		disruptionPowerMultiplier = (float) ConfigManager.getSystemConfig().auraDisruptorPowerMultiplier.value.doubleValue();
		auraRegenPercentPerUpdate = (float) ConfigManager.getSystemConfig().auraRegenPercentPerUpdate.value.doubleValue();
	}

	// --- Role hooks supplied by concrete subclasses ---

	/** The base chamber that gates this aura (its presence makes the addon usable). */
	protected abstract ElementInformation getBaseChamberInfo();

	/** The faction relation of ships this aura affects (FRIEND for buffs, ENEMY for debuffs). */
	protected abstract FactionRelation.RType getTargetRelation();

	/** One of {@link AuraState}'s KIND_* constants, used to colour the tactical-map sphere. */
	protected abstract int getAuraKind();

	/** Populate {@link #effectsToApply} from whichever sub-chambers are present. Called only while active. */
	protected abstract void collectEffects();

	/**
	 * Finds the active (or, failing that, usable) aura projector on a ship regardless of role. Needed because
	 * {@code SegmentControllerUtils.getAddon(.., Class)} matches by exact class, which would miss subclasses; this
	 * iterates the player-usables and matches by {@code instanceof}.
	 */
	public static AuraProjectorAddOn getActiveAura(ManagedSegmentController<?> ent) {
		if(ent == null || ent.getManagerContainer() == null) {
			return null;
		}
		AuraProjectorAddOn usableFallback = null;
		ObjectCollection<PlayerUsableInterface> usables = ent.getManagerContainer().getPlayerUsable();
		for(PlayerUsableInterface i : usables) {
			if(i instanceof AuraProjectorAddOn aura) {
				if(aura.isActive()) {
					return aura;
				}
				if(aura.isPlayerUsable()) {
					usableFallback = aura;
				}
			}
		}
		return usableFallback;
	}

	/** Recalibrate every aura projector on a ship (both roles) in response to a reactor change. */
	public static void recalibrateAll(ManagedUsableSegmentController<?> ent, ReactorRecalibrateEvent event) {
		if(ent == null || ent.getManagerContainer() == null) {
			return;
		}
		for(PlayerUsableInterface i : ent.getManagerContainer().getPlayerUsable()) {
			if(i instanceof AuraProjectorAddOn aura) {
				aura.onReactorRecalibrate(event);
			}
		}
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
			ReactorElement auraBase = SegmentControllerUtils.getChamberFromElement(controller, getBaseChamberInfo());
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
					collectEffects();
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
				targetingEntities.remove(target);
				// Free the no-stacking claim so another aura of this kind can cover the now-out-of-range ship.
				AuraManager.getInstance().release(target.getId(), getAuraKind(), getSegmentController().getId());
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
							if(currentFactionId > 0 && entityFactionId > 0 && Objects.requireNonNull(GameCommon.getGameState()).getFactionManager().getRelation(currentFactionId, entityFactionId) == getTargetRelation()) {
								float distance = EntityUtils.getDistance(entity, getSegmentController());
								if(distance <= maxRange && !targetingEntities.containsKey(entity)) {
									// No stacking: only affect this ship if no other aura of our kind already claims it.
									if(AuraManager.getInstance().tryClaim(entity.getId(), getAuraKind(), getSegmentController().getId())) {
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
					String disruptorName = shootingEntity != null ? shootingEntity.getName() : "enemy fire";
					for(PlayerState playerState : getAttachedPlayers()) {
						playerState.sendServerMessagePlayerWarning(new Object[] {"Aura Projector disrupted by \"" + disruptorName + "\"!"});
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
		if(!isOnServer() || !usable) {
			return;
		}
		try {
			SegmentController sc = getSegmentController();
			float frac = auraMaxPower > 0 ? (float) (auraPower / auraMaxPower) : 1.0f;
			AuraManager.getInstance().report(new AuraState(sc.getId(), sc.getSectorId(), maxRange, getAuraKind(), frac));
		} catch(Exception ignored) {
		}
	}

	/** Stop advertising this projector's aura to clients and free all of its no-stacking claims. */
	private void clearAura() {
		if(!isOnServer() || !usable) {
			return;
		}
		try {
			AuraManager.getInstance().clear(getSegmentController().getId());
			AuraManager.getInstance().releaseAll(getSegmentController().getId());
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
