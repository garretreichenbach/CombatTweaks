package videogoose.combattweaks.listener;

import api.listener.fastevents.segmentpiece.DamageReductionByArmorCalcListener;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

public class DamageReductionByArmorCalcListenerImpl implements DamageReductionByArmorCalcListener {
	@Override
	public float onDamageReductionByArmor(SegmentController controller, float damageBeforeReduction, float damageAfterReduction) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			// Second aura-takedown path: damaging a ship that is projecting an active aura bleeds its aura power
			// proportional to the hit, so sustained fire eventually drops the aura even without an Aura Disruptor.
			attriteAura(controller, damageBeforeReduction);
			ArmorHPCollection armorHPCollection = ArmorHPCollection.getCollection(controller);
			if(armorHPCollection != null) {
				return armorHPCollection.processDamageToArmor(damageBeforeReduction, damageAfterReduction);
			}
		}
		return damageAfterReduction;
	}

	/** Drains an active projector's aura power by a configured fraction of the incoming damage. */
	private void attriteAura(SegmentController controller, float damage) {
		try {
			if(damage <= 0 || Float.isNaN(damage)) {
				return;
			}
			float factor = (float) ConfigManager.getSystemConfig().auraDamageAttritionFactor.value.doubleValue();
			if(factor <= 0) {
				return;
			}
			SegmentController root = controller.railController.getRoot();
			if(!(root instanceof ManagedUsableSegmentController<?>)) {
				return;
			}
			AuraProjectorAddOn addOn = AuraProjectorAddOn.getActiveAura((ManagedUsableSegmentController<?>) root);
			if(addOn != null && addOn.isActive()) {
				addOn.disrupt(null, damage * factor);
			}
		} catch(Exception exception) {
			CombatTweaks.getInstance().logException("Error attriting aura from damage", exception);
		}
	}
}
