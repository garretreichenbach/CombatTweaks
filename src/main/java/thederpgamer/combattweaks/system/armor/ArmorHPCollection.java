package thederpgamer.combattweaks.system.armor;

import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.GUIKeyValueEntry;
import org.schema.game.client.view.gui.structurecontrol.ModuleValueEntry;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.Timer;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ArmorHPCollection extends ElementCollectionManager<ArmorHPUnit, ArmorHPCollection, VoidElementManager<ArmorHPUnit, ArmorHPCollection>> {

	private boolean flagCollectionChanged;
	private double currentHP;
	private double maxHP;

	public ArmorHPCollection(SegmentController segmentController, VoidElementManager<ArmorHPUnit, ArmorHPCollection> armorHPManager) {
		super(ElementKeyMap.HULL_ID, segmentController, armorHPManager);
	}

	@Override
	public int getMargin() {
		return 0;
	}

	@Override
	protected Class<ArmorHPUnit> getType() {
		return ArmorHPUnit.class;
	}

	@Override
	public boolean needsUpdate() {
		return true;
	}

	@Override
	public ArmorHPUnit getInstance() {
		return new ArmorHPUnit();
	}

	@Override
	protected void onChangedCollection() {
		if(!getSegmentController().isOnServer()) ((GameClientState) getSegmentController().getState()).getWorldDrawer().getGuiDrawer().managerChanged(this);
		flagCollectionChanged = true;
	}

	@Override
	public GUIKeyValueEntry[] getGUICollectionStats() {
		return new GUIKeyValueEntry[] {
			new ModuleValueEntry(Lng.str("HP Status"), currentHP + "/" + maxHP + " [" + getHPPercentage() + "%]")
		};
	}

	@Override
	public String getModuleName() {
		return Lng.str("Armor HP System");
	}

	@Override
	public void update(Timer timer) {
		super.update(timer);
		if(flagCollectionChanged && getSegmentController().isOnServer() && getSegmentController().isFullyLoadedWithDock()) {
			currentHP = 0;
			maxHP = 0;
			for(ArmorHPUnit unit : getElementCollections()) {
				currentHP += unit.size() * unit.getElementCollectionId().getHitpointsFull();
				maxHP += unit.size() * unit.getElementCollectionId().getInfo().getMaxHitPointsFull();
			}
			if(currentHP < 0) currentHP = 0;
			if(maxHP < 0) maxHP = 0;
			if(currentHP > maxHP) currentHP = maxHP;
			flagCollectionChanged = false;
		}
	}

	public double getCurrentHP() {
		return currentHP;
	}

	public void setCurrentHP(double currentHP) {
		this.currentHP = Math.max(0, currentHP);
	}

	public double getMaxHP() {
		return maxHP;
	}

	public double getHPPercentage() {
		return currentHP / maxHP * 100;
	}
}
