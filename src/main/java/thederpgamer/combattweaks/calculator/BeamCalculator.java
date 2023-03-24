package thederpgamer.combattweaks.calculator;

import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.BeamState;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.SegmentPiece;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class BeamCalculator {

	private static final int MAX_THREADS = 8; //Todo: Make this configurable
	private static final float BEAM_STOP_PERCENT = 0.5f;
	private static ExecutorService executor;

	public static void initialize() {
		executor = Executors.newFixedThreadPool(MAX_THREADS);
	}

	public static void calculate(final BeamState beamState, final SegmentPiece segmentPiece) {
		assert executor != null;
		executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					if(segmentPiece.getInfo().isArmor()) {
						ArrayList<ElementCollectionManager<?, ?, ?>> managers = SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) segmentPiece.getSegmentController(), ArmorHPCollection.class);
						for(ElementCollectionManager<?, ?, ?> manager : managers) {
							if(manager instanceof ArmorHPCollection) {
								ArmorHPCollection armorHPCollection = (ArmorHPCollection) manager;
								double currentHP = armorHPCollection.getCurrentHP();
								double maxHP = armorHPCollection.getMaxHP();
								double armorHP = currentHP / maxHP;
								if(armorHP >= BEAM_STOP_PERCENT) {
									System.err.println("Armor HP is " + armorHP + " - Stopping beam");
									armorHPCollection.setCurrentHP(currentHP - beamState.getPower());
									beamState.setPower(0);
								} else if(armorHP > 0) {
									System.err.println("Armor HP is " + armorHP + " - Stopping beam with reduced damage");
									armorHPCollection.setCurrentHP(currentHP - beamState.getPower());
									beamState.setPower((float) (beamState.getPower() - (beamState.getPower() * armorHP)));
								}
							}
						}
					}
				} catch(Exception exception) {
					exception.printStackTrace();
				}
			}
		});
	}
}
