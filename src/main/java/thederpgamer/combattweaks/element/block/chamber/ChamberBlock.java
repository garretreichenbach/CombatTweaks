package thederpgamer.combattweaks.element.block.chamber;

import api.config.BlockConfig;
import api.utils.element.Blocks;
import org.schema.game.common.data.element.ElementInformation;
import thederpgamer.combattweaks.CombatTweaks;
import thederpgamer.combattweaks.element.block.ElementInterface;

public abstract class ChamberBlock implements ElementInterface {

	protected String name;
	protected ElementInformation blockInfo;

	protected ChamberBlock(String name) {
		this.name = name;
	}

	@Override
	public short getId() {
		return blockInfo.id;
	}

	@Override
	public ElementInformation getInfo() {
		return blockInfo;
	}

	@Override
	public void initData() {
		blockInfo = BlockConfig.newChamber(CombatTweaks.getInstance(), name, Blocks.DEFENCE_CHAMBER.getId());
		blockInfo.volume = Blocks.BASE_SHIELD_ENHANCEMENT.getInfo().volume;
		blockInfo.mass = Blocks.BASE_SHIELD_ENHANCEMENT.getInfo().mass;
		Blocks.DEFENCE_CHAMBER.getInfo().chamberChildren.remove(getId());
	}

	public void addChildren(ChamberBlock... children) {
		for(ChamberBlock child : children) {
			child.blockInfo.chamberParent = getId();
			child.blockInfo.chamberPrerequisites.add(getId());
			blockInfo.chamberChildren.add(child.getId());
		}
	}

	public void setUpgrade(ChamberBlock upgrade) {
		addChildren(upgrade);
		upgrade.blockInfo.chamberMutuallyExclusive.addAll(blockInfo.chamberMutuallyExclusive);
		blockInfo.chamberUpgradesTo = upgrade.getId();
	}

	public void addExclusives(ChamberBlock... exclusives) {
		for(ChamberBlock exclusive : exclusives) {
			exclusive.blockInfo.chamberMutuallyExclusive.add(getId());
			blockInfo.chamberMutuallyExclusive.add(exclusive.getId());
		}
	}
}
