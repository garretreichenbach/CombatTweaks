package videogoose.combattweaks.system.aura;

import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.faction.FactionRelation;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;

/**
 * Hostile Offense Aura (ECW): jams the AI targeting of enemy ships inside the sphere via the Targeting Jammer
 * sub-chambers, so their AI-controlled turrets/drones/point-defense scatter their fire. Gated by the Offense Aura
 * base chamber in the reactor offense tree. Deliberately an electronic-warfare effect rather than a flat stat
 * debuff (see aura-balance-philosophy): it spares manually-piloted ships and is countered by attrition under
 * fire and the Aura Disruptor.
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
		ReactorElement jammer1 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.TARGETING_JAMMER_AURA_CHAMBER_1.getInfo());
		if(jammer1 != null && jammer1.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.TARGETING_JAMMER_AURA_EFFECT_1.configEffectGroup);
		}
		ReactorElement jammer2 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.TARGETING_JAMMER_AURA_CHAMBER_2.getInfo());
		if(jammer2 != null && jammer2.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.TARGETING_JAMMER_AURA_EFFECT_2.configEffectGroup);
		}
	}

	@Override
	public String getName() {
		return "Offense Aura";
	}
}
