package videogoose.combattweaks.system.aura;

import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.faction.FactionRelation;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;

/**
 * Hostile Offense Aura: debuffs enemy ships inside the sphere (shield dampen, weapon-range dampen). Gated by the
 * Offense Aura base chamber in the reactor offense tree.
 */
public class OffenseAuraAddOn extends AuraProjectorAddOn {

	public OffenseAuraAddOn(ManagerContainer<?> managerContainer) {
		super(managerContainer, BlockRegistry.OFFENSE_AURA_CHAMBER.getId(), "OffenseAuraChamber");
	}

	@Override
	protected ElementInformation getBaseChamberInfo() {
		return BlockRegistry.OFFENSE_AURA_CHAMBER.getInfo();
	}

	@Override
	protected FactionRelation.RType getTargetRelation() {
		return FactionRelation.RType.ENEMY;
	}

	@Override
	protected int getAuraKind() {
		return AuraState.KIND_OFFENSE;
	}

	@Override
	protected void collectEffects() {
		ReactorElement shieldDampen = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.SHIELD_DAMPEN_AURA_CHAMBER.getInfo());
		if(shieldDampen != null && shieldDampen.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.SHIELD_DAMPEN_AURA_EFFECT.configEffectGroup);
		}
		ReactorElement weaponRangeDampen = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.WEAPON_RANGE_DAMPEN_AURA_CHAMBER.getInfo());
		if(weaponRangeDampen != null && weaponRangeDampen.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.WEAPON_RANGE_DAMPEN_AURA_EFFECT.configEffectGroup);
		}
	}

	@Override
	public String getName() {
		return "Offense Aura";
	}
}
