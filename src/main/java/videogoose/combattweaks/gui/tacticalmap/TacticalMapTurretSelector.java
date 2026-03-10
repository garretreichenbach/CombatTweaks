package videogoose.combattweaks.gui.tacticalmap;

import org.schema.common.util.StringTools;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.Ship;
import org.schema.game.common.controller.rails.RailRelation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles selection of individual turrets (rails) for targeting.
 * Allows players to select specific turrets on a ship for attack/defense orders.
 */
public class TacticalMapTurretSelector {

	private final TacticalMapGUIDrawer drawer;
	private final SegmentController entity;
	private final List<TurretInfo> availableTurrets = new ArrayList<>();

	public TacticalMapTurretSelector(TacticalMapGUIDrawer drawer, SegmentController entity) {
		this.drawer = drawer;
		this.entity = entity;
		collectAvailableTurrets();
	}

	/**
	 * Collect available turrets (rails) from the entity.
	 * For now, this creates placeholder turrets. In a real implementation,
	 * this would extract actual rail data from the SegmentController.
	 */
	private void collectAvailableTurrets() {
		if(entity == null) {
			return;
		}

		int i = 0;
		for(RailRelation child : entity.railController.next) {
			if(child.docked.getSegmentController() instanceof Ship) {
				Ship dockedShip = (Ship) child.docked.getSegmentController();
				if(dockedShip.isAIControlled()) {
					availableTurrets.add(new TurretInfo("turret_" + i, "Turret " + (i + 1) + " (" + StringTools.massFormat(dockedShip.getMassWithDocks()) + ")", i));
					i ++;
				}
			}
		}
	}

	/**
	 * Toggle selection of a turret.
	 */
	public void toggleTurretSelection(String turretId) {
		if(drawer.isTurretSelected(turretId)) {
			drawer.removeTurretSelection(turretId);
		} else {
			drawer.addTurretSelection(turretId);
		}
	}

	/**
	 * Get the list of available turrets.
	 */
	public List<TurretInfo> getAvailableTurrets() {
		return availableTurrets;
	}

	/**
	 * Check if there are any turrets available.
	 */
	public boolean hasTurrets() {
		return !availableTurrets.isEmpty();
	}

	/**
	 * Activate turret selection mode.
	 * This can display a menu or interface for turret selection.
	 */
	public void activate() {
		if(!hasTurrets()) {
			// No turrets available on this entity
			return;
		}

		// Toggle turret selection for the first available turret as a simple test
		if(!availableTurrets.isEmpty()) {
			toggleTurretSelection(availableTurrets.get(0).turretId);
		}
	}

	/**
	 * Simple data class to hold turret information.
	 */
	public static class TurretInfo {
		public final String turretId;
		public final String displayName;
		public final int railIndex;

		public TurretInfo(String turretId, String displayName, int railIndex) {
			this.turretId = turretId;
			this.displayName = displayName;
			this.railIndex = railIndex;
		}
	}
}
