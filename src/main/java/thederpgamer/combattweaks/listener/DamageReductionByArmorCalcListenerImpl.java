package thederpgamer.combattweaks.listener;

import api.listener.fastevents.segmentpiece.DamageReductionByArmorCalcListener;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

public class DamageReductionByArmorCalcListenerImpl implements DamageReductionByArmorCalcListener {
	@Override
	public float onDamageReductionByArmor(SegmentController controller, float damageBeforeReduction, float damageAfterReduction) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			ArmorHPCollection armorHPCollection = ArmorHPCollection.getCollection(controller);
			if(armorHPCollection != null) {
				return armorHPCollection.processDamageToArmor(damageBeforeReduction, damageAfterReduction);
			}
		}
		return damageAfterReduction;
	}
}
