package videogoose.combattweaks.mixin;

import org.schema.game.common.controller.Ship;
import org.schema.game.common.data.fleet.Fleet;
import org.schema.game.common.data.fleet.FleetMember;
import org.schema.game.common.data.fleet.missions.machines.states.FleetState;
import org.schema.game.server.ai.program.fleetcontrollable.FleetControllableProgram;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetBreaking;
import org.schema.game.server.ai.program.fleetcontrollable.states.FleetIdleWaiting;
import org.schema.schine.ai.stateMachines.FSMException;
import org.schema.schine.ai.stateMachines.State;
import org.schema.schine.ai.stateMachines.Transition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import videogoose.combattweaks.utils.AIUtils;

/**
 * Lets a single fleeted ship mine on its own.
 *
 * <p>An idle fleet calls {@link FleetState#restartAllLoaded()} every update, which force-transitions
 * every member that isn't idle/breaking into FLEET_BREAKING — so the moment our solo-mining ship
 * enters the engine's FleetMining state, its own fleet yanks it back out (the "[FLEET] breaking ship"
 * spam and the start/stop "tapping the mine button" behaviour). We replace that method with one that
 * skips any ship we have an active mining assignment for, leaving everything else identical.</p>
 */
@Mixin(value = FleetState.class, remap = false)
public abstract class FleetStateMixin {

	@Shadow
	public abstract Fleet getEntityState();

	@Inject(method = "restartAllLoaded", at = @At("HEAD"), cancellable = true)
	private void combatTweaks$skipMiningShips(CallbackInfo ci) {
		for(FleetMember mem : getEntityState().getMembers()) {
			if(!mem.isLoaded() || !(mem.getLoaded() instanceof Ship s)) {
				continue;
			}
			// Leave ships under any CombatTweaks order (attack/defend/mine/repair) alone — otherwise the
			// idle fleet breaks them out of their combat/repair/mining states.
			if(AIUtils.isUnderCommand(s.getId())) {
				continue;
			}
			if(s.isCoreOverheating()
					|| s.getAiConfiguration().getAiEntityState().getCurrentProgram() == null
					|| !(s.getAiConfiguration().getAiEntityState().getCurrentProgram() instanceof FleetControllableProgram)) {
				continue;
			}
			State st = s.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().getCurrentState();
			if(!(st instanceof FleetIdleWaiting) && !(st instanceof FleetBreaking)) {
				try {
					s.getAiConfiguration().getAiEntityState().getCurrentProgram().getMachine().getFsm().stateTransition(Transition.FLEET_BREAKING);
				} catch(FSMException ignored) {
				}
			}
		}
		ci.cancel();
	}
}
