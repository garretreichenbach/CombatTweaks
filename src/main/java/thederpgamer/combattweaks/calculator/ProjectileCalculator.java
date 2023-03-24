package thederpgamer.combattweaks.calculator;

import api.utils.game.SegmentControllerUtils;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.damage.projectile.ProjectileController;
import org.schema.game.common.controller.damage.projectile.ProjectileHandlerSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.physics.CubeRayCastResult;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector3f;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ProjectileCalculator {
	private static final float FULL_STOP_MODIFIER = 5.0f;
	private static final int MAX_THREADS = 8; //Todo: Make this configurable
	private static final float PROJECTILE_STOP_PERCENT = 0.3f;
	private static ExecutorService executor;

	public static void initialize() {
		executor = Executors.newFixedThreadPool(MAX_THREADS);
	}

	/**
	 * Calculates the impact angle and armor thickness at the contact point and determines if the projectile should be stopped.
	 *
	 * @param projectileController The projectile controller.
	 * @param posBeforeUpdate The position of the projectile before it was updated.
	 * @param postAfterUpdate The position of the projectile after it was updated.
	 * @param cubeRayCastResult The cube ray cast result.
	 * @return The projectile handle state.
	 */
	public static ProjectileController.ProjectileHandleState calculate(final ProjectileController projectileController, final Vector3f posBeforeUpdate, final Vector3f postAfterUpdate, final CubeRayCastResult cubeRayCastResult, final ProjectileHandlerSegmentController.ShotHandler shotHandler) throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
		assert executor != null;
		return executor.submit(new Callable<ProjectileController.ProjectileHandleState>() {
			@Override
			public ProjectileController.ProjectileHandleState call() {
				try {
					long index = projectileController.getBlockHit().block;
					if(projectileController.getBlockHit().getSegmentData().getSegmentController().getSegmentBuffer().existsPointUnsave(index)) {
						SegmentPiece segmentPiece = projectileController.getBlockHit().getSegmentData().getSegmentController().getSegmentBuffer().getPointUnsave(index);
						if(segmentPiece.getType() == 14) { //Check if hit block was a warhead, if so, have it act as ERA and stop the projectile
							segmentPiece.setActive(true); //Activate the Warhead, exploding it if not already done
							segmentPiece.getSegmentController().sendBlockActivation(index);
							System.err.println("Warhead hit - Stopping projectile");
							segmentPiece.getSegmentController().sendBlockKill(segmentPiece);
							return ProjectileController.ProjectileHandleState.PROJECTILE_NO_HIT_STOP;
						} else if(segmentPiece.getInfo().isArmor()) {
							ArrayList<ElementCollectionManager<?, ?, ?>> managers = SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) segmentPiece.getSegmentController(), ArmorHPCollection.class);
							for(ElementCollectionManager<?, ?, ?> manager : managers) {
								if(manager instanceof ArmorHPCollection) {
									ArmorHPCollection armorHPCollection = (ArmorHPCollection) manager;
									double currentHP = armorHPCollection.getCurrentHP();
									double maxHP = armorHPCollection.getMaxHP();
									double armorHP = currentHP / maxHP;
									Field damageField = shotHandler.getClass().getDeclaredField("dmg");
									damageField.setAccessible(true);
									float damage = damageField.getFloat(shotHandler);
									if(armorHP >= PROJECTILE_STOP_PERCENT) {
										System.err.println("Armor HP is " + armorHP + " - Stopping projectile");
										armorHPCollection.setCurrentHP(currentHP - damage);
										damageField.setFloat(shotHandler, 0);
										return ProjectileController.ProjectileHandleState.PROJECTILE_HIT_STOP_INVULNERABLE;
									} else if(armorHP > 0) {
										System.err.println("Armor HP is " + armorHP + " - Stopping projectile with reduced damage");
										armorHPCollection.setCurrentHP(currentHP - damage);
										damageField.setFloat(shotHandler, (float) (damage - (damage * armorHP)));
										return ProjectileController.ProjectileHandleState.PROJECTILE_HIT_STOP;
									} else {
										System.err.println("Armor HP is " + armorHP + " - Stopping projectile with full damage");
										return ProjectileController.ProjectileHandleState.PROJECTILE_HIT_STOP;
									}
								}
							}
							//Calculate the impact angle and armor thickness at the contact point
//							float impactAngle = (float) Math.toDegrees(Math.acos(posBeforeUpdate.dot(postAfterUpdate) / (posBeforeUpdate.length() * postAfterUpdate.length())));
//							if(cubeRayCastResult.innerSegmentIterator instanceof ArmorCheckTraverseHandler) {
//								ArmorCheckTraverseHandler armorCheckTraverseHandler = (ArmorCheckTraverseHandler) cubeRayCastResult.innerSegmentIterator;
//								ArmorValue armorValue = armorCheckTraverseHandler.armorValue;
//								armorValue.calculate();
//								float armorThickness = armorValue.totalArmorValue;
//								float initialArmorThickness = armorThickness;
								//Add more armor thickness the more extreme the impact angle is
//								armorThickness += armorThickness * (FastMath.PI / impactAngle) * (cubeRayCastResult.getBlockDeepness() * 3.5f);
								//t = t * (PI / a) * (c * 3.5)
//								Field damageField = shotHandler.getClass().getDeclaredField("dmg");
//								damageField.setAccessible(true);
//								float damage = damageField.getFloat(shotHandler); //If the armor thickness is much greater than the damage, stop the projectile completely
//								armorValue.totalArmorValue = armorThickness;
//								if(armorThickness > damage * FULL_STOP_MODIFIER) {
//									System.err.println("Armor Calculation: Initial Thickness = " + initialArmorThickness + " Total Thickness = " + armorThickness + " Impact Angle = " + impactAngle + " Result = FULL STOP");
//									return ProjectileController.ProjectileHandleState.PROJECTILE_NO_HIT_STOP;
//								} else if(armorThickness > damage) {
//									System.err.println("Armor Calculation: Initial Thickness = " + initialArmorThickness + " Total Thickness = " + armorThickness + " Impact Angle = " + impactAngle + " Result = STOP WITH DAMAGE");
//									return ProjectileController.ProjectileHandleState.PROJECTILE_HIT_STOP;
//								} else {
//									System.err.println("Armor Calculation: Initial Thickness = " + initialArmorThickness + " Total Thickness = " + armorThickness + " Impact Angle = " + impactAngle + " Result = NO STOP");
//									return ProjectileController.ProjectileHandleState.PROJECTILE_HIT_CONTINUE;
//								}
							}
						}
//					}
					System.err.println("No Armor Calculation");
				} catch(Exception exception) {
					System.err.println("Error in Armor Calculation!");
					exception.printStackTrace();
				}
				return shotHandler.getResult();
			}
		}).get();
	}
}
