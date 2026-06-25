package videogoose.combattweaks.system.weapon.auradisruptor;

import org.schema.game.client.data.GameClientState;
import org.schema.game.client.data.GameStateInterface;
import org.schema.game.client.view.gui.structurecontrol.ControllerManagerGUI;
import org.schema.game.common.controller.damage.HitType;
import org.schema.game.common.controller.elements.ControlBlockElementCollectionManager;
import org.schema.game.common.controller.elements.beam.BeamUnit;
import org.schema.game.common.controller.elements.beam.harvest.SalvageElementManager;
import videogoose.combattweaks.manager.ConfigManager;

/**
 * A single firing unit of the Aura Disruptor support beam. It deals no block damage; on hit the handler drains
 * the target projector's aura power instead (see {@link AuraDisruptorBeamHandler}). Ported from BetterChambers,
 * with the old YAML {@code getConfigurableFloat} calls replaced by CombatTweaks' typed {@code SystemConfig}.
 */
public class AuraDisruptorBeamUnit extends BeamUnit<AuraDisruptorBeamUnit, AuraDisruptorBeamCollectionManager, AuraDisruptorBeamElementManager> {

	@Override
	public float getBeamPowerWithoutEffect() {
		return getBeamPower();
	}

	@Override
	public float getBeamPower() {
		return size() * getBaseBeamPower();
	}

	@Override
	public float getBaseBeamPower() {
		return (float) ConfigManager.getSystemConfig().auraDisruptorBeamPowerPerUnit.value.doubleValue();
	}

	@Override
	public float getBasePowerConsumption() {
		return (float) ConfigManager.getSystemConfig().auraDisruptorBeamPowerConsumptionPerUnit.value.doubleValue();
	}

	@Override
	public float getPowerConsumption() {
		return size() * getBasePowerConsumption();
	}

	@Override
	public float getPowerConsumptionWithoutEffect() {
		return getBasePowerConsumption();
	}

	@Override
	public HitType getHitType() {
		return HitType.SUPPORT;
	}

	@Override
	public float getDistanceRaw() {
		return SalvageElementManager.DISTANCE * ((GameStateInterface) getSegmentController().getState()).getGameState().getWeaponRangeReference() * 5.0f;
	}

	@Override
	public float getDamage() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondResting() {
		return 0;
	}

	@Override
	public double getPowerConsumedPerSecondCharging() {
		return getPowerConsumption();
	}

	@Override
	public PowerConsumerCategory getPowerConsumerCategory() {
		return PowerConsumerCategory.SUPPORT_BEAMS;
	}

	@Override
	public ControllerManagerGUI createUnitGUI(GameClientState gameClientState, ControlBlockElementCollectionManager<?, ?, ?> supportCol, ControlBlockElementCollectionManager<?, ?, ?> effectCol) {
		return elementCollectionManager.getElementManager().getGUIUnitValues(this, elementCollectionManager, supportCol, effectCol);
	}

	@Override
	public void flagBeamFiredWithoutTimeout() {
		elementCollectionManager.flagBeamFiredWithoutTimeout(this);
	}
}
