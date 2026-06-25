package videogoose.combattweaks.system.aura;

import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.elements.ManagerContainer;
import org.schema.game.common.controller.elements.power.reactor.tree.ReactorElement;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.player.faction.FactionRelation;
import videogoose.combattweaks.effect.ConfigGroupRegistry;
import videogoose.combattweaks.element.block.BlockRegistry;

/**
 * Friendly Support Aura: buffs allied ships inside the sphere (shield capacity tiers). Gated by the Support Aura
 * base chamber in the reactor support tree.
 */
public class SupportAuraAddOn extends AuraProjectorAddOn {

	public SupportAuraAddOn(ManagerContainer<?> managerContainer) {
		super(managerContainer, BlockRegistry.SUPPORT_AURA_CHAMBER.getId(), "SupportAuraChamber");
	}

	@Override
	protected ElementInformation getBaseChamberInfo() {
		return BlockRegistry.SUPPORT_AURA_CHAMBER.getInfo();
	}

	@Override
	protected FactionRelation.RType getTargetRelation() {
		return FactionRelation.RType.FRIEND;
	}

	@Override
	protected int getAuraKind() {
		return AuraState.KIND_SUPPORT;
	}

	@Override
	protected void collectEffects() {
		ReactorElement shieldAuraCap1 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.SHIELD_AURA_CAPACITY_CHAMBER_1.getInfo());
		if(shieldAuraCap1 != null && shieldAuraCap1.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.SHIELD_AURA_CAPACITY_EFFECT_1.configEffectGroup);
		}
		ReactorElement shieldAuraCap2 = SegmentControllerUtils.getChamberFromElement(getManagerUsableSegmentController(), BlockRegistry.SHIELD_AURA_CAPACITY_CHAMBER_2.getInfo());
		if(shieldAuraCap2 != null && shieldAuraCap2.isAllValidOrUnspecified()) {
			effectsToApply.add(ConfigGroupRegistry.SHIELD_AURA_CAPACITY_EFFECT_2.configEffectGroup);
		}
	}

	@Override
	public String getName() {
		return "Support Aura";
	}
}
