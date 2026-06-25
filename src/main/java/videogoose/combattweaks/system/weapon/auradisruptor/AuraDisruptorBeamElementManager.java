package videogoose.combattweaks.system.weapon.auradisruptor;

import api.utils.game.module.CustomModuleUtils;
import api.utils.sound.AudioUtils;
import com.bulletphysics.linearmath.Transform;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import org.schema.common.util.StringTools;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.ControllerManagerGUI;
import org.schema.game.client.view.gui.structurecontrol.ModuleValueEntry;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ControlBlockElementCollectionManager;
import org.schema.game.common.controller.elements.beam.BeamElementManager;
import org.schema.game.common.controller.elements.combination.BeamCombiSettings;
import org.schema.game.common.controller.elements.combination.CombinationAddOn;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.server.data.GameServerState;
import org.schema.schine.common.language.Lng;
import videogoose.combattweaks.element.block.BlockRegistry;

/**
 * Element manager that binds the Aura Disruptor computer/module pair to the beam runtime. Ported from
 * BetterChambers; references CombatTweaks' {@link BlockRegistry}.
 */
public class AuraDisruptorBeamElementManager extends BeamElementManager<AuraDisruptorBeamUnit, AuraDisruptorBeamCollectionManager, AuraDisruptorBeamElementManager> {

	public AuraDisruptorBeamElementManager(SegmentController segmentController) {
		super(BlockRegistry.AURA_DISRUPTOR_COMPUTER.getId(), BlockRegistry.AURA_DISRUPTOR_MODULE.getId(), segmentController);
		CustomModuleUtils.setElementManager(this, getComputerInfo().getId(), getModuleInfo().getId());
	}

	public ElementInformation getComputerInfo() {
		return BlockRegistry.AURA_DISRUPTOR_COMPUTER.getInfo();
	}

	public ElementInformation getModuleInfo() {
		return BlockRegistry.AURA_DISRUPTOR_MODULE.getInfo();
	}

	@Override
	public float getTickRate() {
		return 100.0f;
	}

	@Override
	public float getCoolDown() {
		return 3.0f;
	}

	@Override
	public float getBurstTime() {
		return 3.0f;
	}

	@Override
	public float getInitialTicks() {
		return 1.0f;
	}

	@Override
	public double getRailHitMultiplierParent() {
		return 3.0f;
	}

	@Override
	public double getRailHitMultiplierChild() {
		return 3.0f;
	}

	@Override
	public void updateActivationTypes(ShortOpenHashSet shortOpenHashSet) {
		shortOpenHashSet.add(getModuleInfo().getId());
	}

	@Override
	protected String getTag() {
		return "mainreactor";
	}

	@Override
	public AuraDisruptorBeamCollectionManager getNewCollectionManager(SegmentPiece segmentPiece, Class<AuraDisruptorBeamCollectionManager> aClass) {
		return new AuraDisruptorBeamCollectionManager(segmentPiece, getSegmentController(), this);
	}

	@Override
	public String getManagerName() {
		return "Aura Disruptor System Collective";
	}

	@Override
	protected void playSound(AuraDisruptorBeamUnit auraDisruptorBeamUnit, Transform transform) {
		if(getState() instanceof GameServerState) {
			for(PlayerState playerState : getAttachedPlayers()) {
				AudioUtils.serverPlaySound("0022_spaceship user - special synthetic weapon fire 2", transform.origin.x, transform.origin.y, transform.origin.z, 10.0f, 1.0f, playerState);
			}
		}
	}

	@Override
	public ControllerManagerGUI getGUIUnitValues(AuraDisruptorBeamUnit firingUnit, AuraDisruptorBeamCollectionManager col, ControlBlockElementCollectionManager<?, ?, ?> supportCol, ControlBlockElementCollectionManager<?, ?, ?> effectCol) {
		return ControllerManagerGUI.create((GameClientState) getState(), Lng.str("Beam Unit"), firingUnit,
				new ModuleValueEntry("Disruptor Power", StringTools.formatPointZero(firingUnit.getBeamPower())),
				new ModuleValueEntry(Lng.str("TickRate"), StringTools.formatPointZeroZero(firingUnit.getTickRate())),
				new ModuleValueEntry(Lng.str("Range"), StringTools.formatPointZero(firingUnit.getDistance())),
				new ModuleValueEntry(Lng.str("PowerConsumptionResting"), firingUnit.getPowerConsumedPerSecondResting()),
				new ModuleValueEntry(Lng.str("PowerConsumptionCharging"), firingUnit.getPowerConsumedPerSecondCharging()));
	}

	@Override
	public CombinationAddOn<AuraDisruptorBeamUnit, AuraDisruptorBeamCollectionManager, ? extends AuraDisruptorBeamElementManager, BeamCombiSettings> getAddOn() {
		return null;
	}
}
