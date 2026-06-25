package videogoose.combattweaks.element.block;

import api.config.BlockConfig;
import org.schema.game.common.data.element.ElementInformation;
import videogoose.combattweaks.element.block.chamber.defense.ArmorHPAbsorptionChamber1;
import videogoose.combattweaks.element.block.chamber.defense.ArmorHPAbsorptionChamber2;
import videogoose.combattweaks.element.block.chamber.offense.aura.OffenseAuraChamber;
import videogoose.combattweaks.element.block.chamber.offense.aura.TargetingJammerAuraChamber1;
import videogoose.combattweaks.element.block.chamber.offense.aura.TargetingJammerAuraChamber2;
import videogoose.combattweaks.element.block.chamber.offense.aura.range.OffenseAuraRangeBoostChamber1;
import videogoose.combattweaks.element.block.chamber.offense.aura.range.OffenseAuraRangeBoostChamber2;
import videogoose.combattweaks.element.block.chamber.offense.warhead.WarheadPreChargerChamber1;
import videogoose.combattweaks.element.block.chamber.offense.warhead.WarheadPreChargerChamber2;
import videogoose.combattweaks.element.block.chamber.support.aura.SupportAuraChamber;
import videogoose.combattweaks.element.block.chamber.support.aura.range.AuraRangeBoostChamber1;
import videogoose.combattweaks.element.block.chamber.support.aura.range.AuraRangeBoostChamber2;
import videogoose.combattweaks.element.block.chamber.support.aura.shieldaura.ShieldAuraCapacityChamber1;
import videogoose.combattweaks.element.block.chamber.support.aura.shieldaura.ShieldAuraCapacityChamber2;
import videogoose.combattweaks.element.block.reactor.ReactorOffenseChamber;
import videogoose.combattweaks.element.block.reactor.ReactorSupportChamber;
import videogoose.combattweaks.element.block.weapon.AuraDisruptorComputer;
import videogoose.combattweaks.element.block.weapon.AuraDisruptorModule;

/**
 * Central registry that defines and registers all mod blocks/chambers/weapons.
 * <p>
 * Enum order is the {@code initData()} order, so any block referencing another block's id in its own
 * {@code initData()} must appear after it — the reactor parent chambers therefore precede their sub-chambers.
 */
public enum BlockRegistry {
	//Defense chambers
	ARMOR_HP_ABSORPTION_CHAMBER_1(new ArmorHPAbsorptionChamber1()),
	ARMOR_HP_ABSORPTION_CHAMBER_2(new ArmorHPAbsorptionChamber2()),

	//Generic reactor chambers (parents — must precede their sub-chambers below)
	REACTOR_OFFENSE_CHAMBER(new ReactorOffenseChamber()),
	REACTOR_SUPPORT_CHAMBER(new ReactorSupportChamber()),

	//Offense chambers
	WARHEAD_PRE_CHARGER_CHAMBER_1(new WarheadPreChargerChamber1()),
	WARHEAD_PRE_CHARGER_CHAMBER_2(new WarheadPreChargerChamber2()),

	//Support chambers — Support Aura (buffs allies)
	SUPPORT_AURA_CHAMBER(new SupportAuraChamber()),
	SHIELD_AURA_CAPACITY_CHAMBER_1(new ShieldAuraCapacityChamber1()),
	SHIELD_AURA_CAPACITY_CHAMBER_2(new ShieldAuraCapacityChamber2()),
	AURA_RANGE_BOOST_CHAMBER_1(new AuraRangeBoostChamber1()),
	AURA_RANGE_BOOST_CHAMBER_2(new AuraRangeBoostChamber2()),

	//Offense chambers — Offense Aura (ECW: jams enemy AI targeting). Base must precede its sub-chambers.
	OFFENSE_AURA_CHAMBER(new OffenseAuraChamber()),
	TARGETING_JAMMER_AURA_CHAMBER_1(new TargetingJammerAuraChamber1()),
	TARGETING_JAMMER_AURA_CHAMBER_2(new TargetingJammerAuraChamber2()),
	OFFENSE_AURA_RANGE_BOOST_CHAMBER_1(new OffenseAuraRangeBoostChamber1()),
	OFFENSE_AURA_RANGE_BOOST_CHAMBER_2(new OffenseAuraRangeBoostChamber2()),

	//Weapons
	AURA_DISRUPTOR_COMPUTER(new AuraDisruptorComputer()),
	AURA_DISRUPTOR_MODULE(new AuraDisruptorModule());

	public final ElementInterface elementInterface;

	BlockRegistry(ElementInterface elementInterface) {
		this.elementInterface = elementInterface;
	}

	public static void registerBlocks() {
		for(BlockRegistry registry : values()) {
			registry.elementInterface.initData();
		}
		for(BlockRegistry registry : values()) {
			registry.elementInterface.postInitData();
		}
		for(BlockRegistry registry : values()) {
			registry.elementInterface.initResources();
		}
		for(BlockRegistry registry : values()) {
			BlockConfig.add(registry.getInfo());
		}
		BlockConfig.registerComputerModulePair(AURA_DISRUPTOR_COMPUTER.elementInterface.getId(), AURA_DISRUPTOR_MODULE.elementInterface.getId());
	}

	public ElementInformation getInfo() {
		return elementInterface.getInfo();
	}

	public short getId() {
		return getInfo().id;
	}
}
