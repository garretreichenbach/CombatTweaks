package videogoose.combattweaks.system.aura;

import api.common.GameClient;
import api.common.GameCommon;
import api.common.GameServer;
import api.entity.SegmentControllerUtils;
import api.entity.module.SimpleAddOn;
import api.player.PlayerUtils;
import api.sound.AudioUtils;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.PlayerUsableInterface;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.damage.Damager;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.effectblock.EffectElementManager;
import org.schema.game.common.controller.elements.power.reactor.PowerImplementation;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.ManagedSegmentController;
import org.schema.game.common.data.blockeffects.config.ConfigEntityManager;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.player.faction.FactionRelation;
import org.schema.game.common.data.world.Sector;
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

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * <p>
 * Power model: hits drain the pool while the aura is up; at zero the aura collapses, strips its effects, and can't
 * be re-activated until the pool has regenerated to full. Regeneration runs whether or not the aura is up.
 * Target tracking and effect application are server-only; the client learns collapse state and power draw from
 * the aura sync packet.
 */
public abstract class AuraProjectorAddOn extends SimpleAddOn {

	public static final int UPDATE_TIMER = 300;
	/** How long a synced client-side value stays trusted without a fresh sync packet (AuraManager sends every 500ms). */
	private static final long SYNC_STALE_MS = 2000;
	/** Minimum gap between AI activation scans, which walk every loaded entity. */
	private static final long AI_SCAN_INTERVAL_MS = 2000;

	protected final ArrayList<ConfigEffectGroup> effectsToApply = new ArrayList<>();
	/** Ships this projector currently applies its effects to and holds a no-stacking claim on. Server only. */
	private final Set<SegmentController> targets = ConcurrentHashMap.newKeySet();
	private final float disruptionPowerMultiplier;
	private final float auraRegenPercentPerUpdate;
	protected boolean usable;
	private int ticks;
	private float maxRange;
	private double auraPower;
	private double auraMaxPower;
	private boolean powerInitialized;
	/** Pool hit zero; stays set until it has regenerated to full. */
	private boolean collapsed;
	private boolean wasActive;
	private long lastAiScanMs;
	// Client mirrors of server-only state, filled from SendAuraSyncPacket.
	private volatile boolean syncedCollapsed;
	private volatile double syncedTargetMass;
	private volatile long syncedAtMs;

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

	/** The projector of the given kind on a ship, active or not. */
	public static AuraProjectorAddOn getAuraOfKind(ManagedSegmentController<?> ent, int kind) {
		if(ent == null || ent.getManagerContainer() == null) {
			return null;
		}
		for(PlayerUsableInterface i : ent.getManagerContainer().getPlayerUsable()) {
			if(i instanceof AuraProjectorAddOn aura && aura.getAuraKind() == kind) {
				return aura;
			}
		}
		return null;
	}

	/** Recalibrate every aura projector on a ship (both roles) in response to a reactor change. */
	public static void recalibrateAll(ManagedUsableSegmentController<?> ent, PowerImplementation implementation) {
		if(ent == null || ent.getManagerContainer() == null) {
			return;
		}
		for(PlayerUsableInterface i : ent.getManagerContainer().getPlayerUsable()) {
			if(i instanceof AuraProjectorAddOn aura) {
				aura.onReactorRecalibrate(implementation);
			}
		}
	}

	/** Server: damage on a ship bleeds its active aura's power (attrition, the non-Disruptor takedown path). */
	public static void onProjectorDamaged(ManagedSegmentController<?> ship, float damage, Damager from) {
		double factor = ConfigManager.getSystemConfig().auraDamageAttritionFactor.value.doubleValue();
		if(factor <= 0 || damage <= 0) {
			return;
		}
		AuraProjectorAddOn aura = getActiveAura(ship);
		if(aura != null && aura.isActive()) {
			aura.drain(damage * factor, from != null ? from.getShootingEntity() : null);
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
	public void onReactorRecalibrate(PowerImplementation implementation) {
		try {
			if(!(getSegmentController() instanceof ManagedUsableSegmentController<?> controller)) {
				return; //Prevents asteroid-related crashes
			}
			if(controller.getManagerContainer() == null || controller.getManagerContainer().getPowerInterface() == null || controller.getManagerContainer().getPowerInterface().getActiveReactor() == null) {
				return;
			}
			ReactorElement auraBase = SegmentControllerUtils.getChamberFromElement(controller, getBaseChamberInfo());
			if(auraBase == null || !auraBase.isAllValidOrUnspecified()) {
				if(usable) {
					// Chamber removed/invalidated: tear down with the effect list we applied, before it changes.
					dropAllTargets();
					clearAura();
				}
				usable = false;
				return;
			}
			usable = true;
			auraMaxPower = auraBase.calculateHpRecursively();
			// Recalibrating must not refill the pool (it runs on every reactor change), only clamp it to the new max.
			auraPower = powerInitialized ? Math.min(auraPower, auraMaxPower) : auraMaxPower;
			powerInitialized = true;
			ConfigEntityManager configManager = getManagerUsableSegmentController().getConfigManager();
			if(configManager.getModules().containsKey(StatusEffectRegistry.AURA_RANGE)) {
				maxRange = configManager.getModules().get(StatusEffectRegistry.AURA_RANGE).getFloatValue() * (Integer) ServerConfig.SECTOR_SIZE.getCurrentState();
			}
			if(isActive()) {
				List<ConfigEffectGroup> previous = new ArrayList<>(effectsToApply);
				effectsToApply.clear();
				collectEffects();
				if(!previous.equals(effectsToApply) && isOnServer()) {
					// Sub-chambers changed mid-aura: swap the old groups for the new ones on everyone we cover.
					for(SegmentController target : targets) {
						removeGroups(target, previous);
						applyGroups(target, effectsToApply);
					}
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
		if(!isActive()) {
			return 0;
		}
		// The client has no target list; it uses the server's figure from the aura sync so both sides draw the same power.
		double mass = isOnServer() ? targetMass() : (isSyncFresh() ? syncedTargetMass : 0);
		return mass / 10.0;
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
	public boolean executeModule() {
		if(isRecovering()) {
			getSegmentController().popupOwnClientMessage("Aura recovering: " + Math.round(getPowerFraction() * 100) + "%", 1);
			return false;
		}
		super.executeModule();
		if(usable && isActive()) {
			onReactorRecalibrate(null);
			execute();
		} else if(isOnServer()) {
			dropAllTargets();
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
		onDeactivated();
	}

	@Override
	public void onActive() {
		wasActive = true;
		if(++ticks < UPDATE_TIMER) {
			return;
		}
		ticks = 0;
		regenerate();
		if(isOnServer()) {
			updateTargetList();
			syncAura();
		}
	}

	@Override
	public void onInactive() {
		if(wasActive) {
			// Clean up on the tick the aura goes down, not up to UPDATE_TIMER ticks later.
			wasActive = false;
			ticks = 0;
			onDeactivated();
		}
		if(++ticks < UPDATE_TIMER) {
			return;
		}
		ticks = 0;
		regenerate();
		onReactorRecalibrate(null);
		if(isOnServer()) {
			syncAura();
		}
	}

	@Override
	public boolean isDeactivatableManually() {
		return isActive();
	}

	private void onDeactivated() {
		if(isOnServer()) {
			dropAllTargets();
			syncAura();
		}
	}

	private void regenerate() {
		// Regen is a fraction of max (not current) power, so an empty pool still recovers.
		auraPower = Math.min(auraMaxPower, auraPower + auraMaxPower * auraRegenPercentPerUpdate);
		if(collapsed && auraPower >= auraMaxPower) {
			collapsed = false;
		}
	}

	/** Server: drop targets that left range, unloaded or changed relation, then pick up new ones. */
	private void updateTargetList() {
		for(SegmentController target : targets) {
			if(!isLoaded(target) || !isEligible(target)) {
				removeTarget(target);
			}
		}
		addEntityEffects();
	}

	/** Server: reset and re-acquire targets on activation. */
	private void execute() {
		if(!isOnServer()) {
			return;
		}
		dropAllTargets();
		addEntityEffects();
		syncAura();
		try {
			AudioUtils.serverPlaySound("0022_spaceship user - turbo boost large", 10.0F, 1.0F, getAttachedPlayers());
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error playing Aura Projector sound", exception);
		}
	}

	/** Server: claim and apply effects to every eligible ship we don't already cover. */
	private void addEntityEffects() {
		try {
			for(Sendable sendable : GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
				if(sendable instanceof SegmentController entity && !targets.contains(entity) && isEligible(entity)
						// No stacking: only affect this ship if no other aura of our kind already claims it.
						&& AuraManager.getInstance().tryClaim(entity.getId(), getAuraKind(), getSegmentController().getId())) {
					applyGroups(entity, effectsToApply);
					targets.add(entity);
				}
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error adding Aura Projector effects to entities", exception);
		}
	}

	/** Server: whether {@code entity} should be covered by this aura right now (ignores the no-stacking claim). */
	private boolean isEligible(SegmentController entity) {
		SegmentController self = getSegmentController();
		if(entity.getId() == self.getId() || entity.equals(self.railController.getRoot())) {
			return false;
		}
		try {
			if(!entity.isFullyLoadedWithDock() || !canAffect(self, entity)) {
				return false;
			}
			int selfFaction = self.getFactionId();
			int entityFaction = entity.getFactionId();
			if(selfFaction <= 0 || entityFaction <= 0 || GameCommon.getGameState().getFactionManager().getRelation(selfFaction, entityFaction) != getTargetRelation()) {
				return false;
			}
			return serverDistanceTo(entity) <= maxRange;
		} catch(Exception exception) {
			// Odd rail roots (non-usable controllers) or half-unloaded entities: just don't cover them.
			return false;
		}
	}

	/** Server: distance from this projector, correct across sector borders (world transforms are sector-local). */
	private float serverDistanceTo(SegmentController other) {
		SegmentController self = getSegmentController();
		if(other.getSectorId() == self.getSectorId()) {
			return EntityUtils.getDistance(other, self);
		}
		Sector sector = ((GameServerState) self.getState()).getUniverse().getSector(self.getSectorId());
		if(sector == null) {
			return Float.MAX_VALUE;
		}
		other.calcWorldTransformRelative(self.getSectorId(), sector.pos);
		Vector3f delta = new Vector3f(other.getClientTransform().origin);
		delta.sub(self.getWorldTransform().origin);
		return delta.length();
	}

	private static boolean isLoaded(SegmentController entity) {
		return GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().get(entity.getId()) == entity;
	}

	private static void applyGroups(SegmentController target, List<ConfigEffectGroup> groups) {
		for(ConfigEffectGroup group : groups) {
			target.getConfigManager().addEffectAndSend(group, true, target.getNetworkObject());
		}
	}

	private static void removeGroups(SegmentController target, List<ConfigEffectGroup> groups) {
		for(ConfigEffectGroup group : groups) {
			target.getConfigManager().removeEffectAndSend(group, true, target.getNetworkObject());
		}
	}

	/** Server: strip our effects from one target and free its no-stacking claim. */
	private void removeTarget(SegmentController target) {
		removeGroups(target, effectsToApply);
		targets.remove(target);
		AuraManager.getInstance().release(target.getId(), getAuraKind(), getSegmentController().getId());
	}

	/** Server: strip our effects from every target and free all claims. */
	private void dropAllTargets() {
		for(SegmentController target : targets) {
			removeTarget(target);
		}
	}

	private double targetMass() {
		double mass = 0;
		for(SegmentController target : targets) {
			mass += target.getMass();
		}
		return mass;
	}

	/** Whether the pool collapsed and hasn't regenerated yet. On the client this comes from the aura sync. */
	public boolean isRecovering() {
		return collapsed || (!isOnServer() && syncedCollapsed && isSyncFresh());
	}

	private boolean isSyncFresh() {
		return System.currentTimeMillis() - syncedAtMs < SYNC_STALE_MS;
	}

	/** Client: apply the server's view of this projector from the aura sync packet. */
	public void applySync(AuraState state) {
		syncedCollapsed = state.collapsed;
		syncedTargetMass = state.targetMass;
		syncedAtMs = System.currentTimeMillis();
	}

	/** Aura Disruptor beam hit. */
	public void disrupt(SimpleTransformableSendableObject<?> shootingEntity, float disruptAmount) {
		drain(disruptAmount * disruptionPowerMultiplier, shootingEntity);
	}

	private void drain(double amount, SimpleTransformableSendableObject<?> source) {
		if(!isActive() || amount <= 0) {
			return;
		}
		auraPower = Math.max(0, auraPower - amount);
		if(auraPower > 0) {
			if(isOnServer()) {
				syncAura();
			}
			return;
		}
		collapse(source);
	}

	private void collapse(SimpleTransformableSendableObject<?> source) {
		auraPower = 0;
		collapsed = true;
		if(getState() instanceof GameServerState) {
			AudioUtils.serverPlaySound("0022_spaceship user - special synthetic weapon recharged 1", 10.0f, 1.0f, getAttachedPlayers());
			String disruptorName = source != null ? source.getName() : "enemy fire";
			for(PlayerState playerState : getAttachedPlayers()) {
				playerState.sendServerMessagePlayerWarning(new Object[] {"Aura Projector disrupted by \"" + disruptorName + "\"!"});
			}
		} else {
			AudioUtils.clientPlaySound("0022_spaceship user - special synthetic weapon recharged 1", 10.0f, 1.0f);
			if(getSegmentController().equals(PlayerUtils.getCurrentControl(GameClient.getClientPlayerState()))) {
				((GameClientState) getState()).getWorldDrawer().getGuiDrawer().notifyEffectHit(getSegmentController(), EffectElementManager.OffensiveEffects.STOP);
			}
		}
		deactivateManually();
		wasActive = false;
		onDeactivated();
	}

	/**
	 * Server: publish this projector to {@link AuraManager}. Active auras report their sphere; a collapsed one
	 * reports a zero-radius entry so the client knows it's recovering; otherwise the entry is removed.
	 */
	private void syncAura() {
		if(!isOnServer()) {
			return;
		}
		try {
			SegmentController sc = getSegmentController();
			boolean active = usable && isActive();
			if(active || collapsed) {
				AuraState state = new AuraState(sc.getId(), sc.getSectorId(), active ? maxRange : 0, getAuraKind(), getPowerFraction());
				state.collapsed = collapsed;
				state.targetMass = (float) targetMass();
				AuraManager.getInstance().report(state);
			} else {
				clearAura();
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error reporting Aura Projector state", exception);
		}
	}

	/** Server: stop advertising this projector's aura to clients. */
	private void clearAura() {
		if(isOnServer()) {
			AuraManager.getInstance().clear(getSegmentController().getId());
		}
	}

	/** Server: AI activation — only when there's something in range for this aura to affect. */
	public void tryAiActivate() {
		if(!usable || isActive() || isRecovering()) {
			return;
		}
		// Charging only runs with auto-charge on, which a player toggles from the hotbar; nobody does that for an AI ship.
		if(!isAutoChargeOn()) {
			setAutoChargeOn(true);
		}
		if(getCharges() <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if(now - lastAiScanMs < AI_SCAN_INTERVAL_MS) {
			return;
		}
		lastAiScanMs = now;
		for(Sendable sendable : GameServer.getServerState().getLocalAndRemoteObjectContainer().getLocalObjects().values()) {
			if(sendable instanceof SegmentController entity && isEligible(entity)) {
				executeModule();
				return;
			}
		}
	}

	/** Server: whether this projector currently applies its effects to {@code ship}. */
	public boolean isTargeting(SegmentController ship) {
		return targets.contains(ship);
	}

	public float getPowerFraction() {
		return auraMaxPower > 0 ? (float) (auraPower / auraMaxPower) : 1.0f;
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
