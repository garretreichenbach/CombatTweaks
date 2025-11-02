package thederpgamer.combattweaks.system.armor;

import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.structurecontrol.ControllerManagerGUI;
import org.schema.game.common.controller.elements.ControlBlockElementCollectionManager;
import org.schema.game.common.controller.elements.VoidElementManager;
import org.schema.game.common.data.element.ElementCollection;
import org.schema.schine.common.language.Lng;

public class ArmorHPUnit extends ElementCollection<ArmorHPUnit, ArmorHPCollection, VoidElementManager<ArmorHPUnit, ArmorHPCollection>> {

	@Override
	public ControllerManagerGUI createUnitGUI(GameClientState gameClientState, ControlBlockElementCollectionManager<?, ?, ?> supportCol, ControlBlockElementCollectionManager<?, ?, ?> effectCol) {
		return ControllerManagerGUI.create(gameClientState, Lng.str("Armor HP System"), this);
	}
}
