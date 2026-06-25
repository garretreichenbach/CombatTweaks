package videogoose.combattweaks.system.weapon.auradisruptor;

import api.utils.game.module.CustomModuleUtils;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelpManager;
import org.schema.game.client.view.gui.shiphud.newhud.HudContextHelperContainer;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.controller.elements.beam.BeamCollectionManager;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.player.ControllerStateUnit;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.core.MouseEvent;
import org.schema.schine.graphicsengine.core.settings.ContextFilter;
import org.schema.schine.input.InputType;

/**
 * Collection manager for an Aura Disruptor weapon group; owns the {@link AuraDisruptorBeamHandler} that drains
 * aura power on hit. Ported from BetterChambers.
 */
public class AuraDisruptorBeamCollectionManager extends BeamCollectionManager<AuraDisruptorBeamUnit, AuraDisruptorBeamCollectionManager, AuraDisruptorBeamElementManager> {

	private final AuraDisruptorBeamHandler handler;

	public AuraDisruptorBeamCollectionManager(SegmentPiece segmentPiece, SegmentController segmentController, AuraDisruptorBeamElementManager elementManager) {
		super(segmentPiece, elementManager.getModuleInfo().getId(), segmentController, elementManager);
		handler = new AuraDisruptorBeamHandler(segmentController, this);
		CustomModuleUtils.setCollectionManager(this, elementManager.getModuleInfo().getId());
	}

	@Override
	public AuraDisruptorBeamHandler getHandler() {
		return handler;
	}

	@Override
	public void addHudConext(ControllerStateUnit controllerStateUnit, HudContextHelpManager contextManager, HudContextHelperContainer.Hos hos) {
		contextManager.addHelper(InputType.MOUSE, MouseEvent.ShootButton.PRIMARY_FIRE.getButton(), Lng.str("Fire"), hos, ContextFilter.IMPORTANT);
	}

	@Override
	protected Class<AuraDisruptorBeamUnit> getType() {
		return AuraDisruptorBeamUnit.class;
	}

	@Override
	public AuraDisruptorBeamUnit getInstance() {
		return new AuraDisruptorBeamUnit();
	}

	@Override
	public String getModuleName() {
		return "Aura Disruptor Beam";
	}
}
