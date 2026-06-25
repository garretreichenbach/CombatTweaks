package videogoose.combattweaks.listener;

import api.listener.fastevents.segmentpiece.DamageReductionByArmorCalcListener;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ShieldAddOn;
import org.schema.game.common.controller.elements.ShieldContainerInterface;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.manager.ConfigManager;
import videogoose.combattweaks.system.armor.ArmorHPCollection;
import videogoose.combattweaks.system.aura.AuraProjectorAddOn;

public class DamageReductionByArmorCalcListenerImpl implements DamageReductionByArmorCalcListener {
	@Override
	public float onDamageReductionByArmor(SegmentController controller, float damageBeforeReduction, float damageAfterReduction) {
		if(controller instanceof ManagedUsableSegmentController<?>) {
			// Only spend Armor HP (and bleed aura power) when the target's shields are actually down. The engine
			// fires this armor-reduction calc post-shield for cannons/beams, but for MISSILE explosions it runs
			// as a pre-pass BEFORE shields are applied — so without this guard armor HP would drain "through" a
			// full shield. When shields are up, leave the damage to the shield and don't touch armor HP.
			if(hasActiveShields(controller)) {
				return damageAfterReduction;
			}
			attriteAura(controller, damageBeforeReduction);
			ArmorHPCollection armorHPCollection = ArmorHPCollection.getCollection(controller);
			if(armorHPCollection != null) {
				return armorHPCollection.processDamageToArmor(damageBeforeReduction, damageAfterReduction);
			}
		}
		return damageAfterReduction;
	}

	/** Whether the target structure currently has shields up (damage should hit the shield, not Armor HP). */
	private boolean hasActiveShields(SegmentController controller) {
		try {
			SegmentController root = controller.railController != null && controller.railController.getRoot() != null
					? controller.railController.getRoot() : controller;
			if(!(root instanceof ManagedUsableSegmentController<?>)) {
				return false;
			}
			Object man = ((ManagedUsableSegmentController<?>) root).getManagerContainer();
			if(man instanceof ShieldContainerInterface) {
				ShieldAddOn shield = ((ShieldContainerInterface) man).getShieldAddOn();
				if(shield == null) {
					return false;
				}
				return root.isUsingLocalShields() ? shield.isUsingLocalShieldsAtLeastOneActive() : shield.getShields() > 0;
			}
		} catch(Exception ignored) {
		}
		return false;
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
