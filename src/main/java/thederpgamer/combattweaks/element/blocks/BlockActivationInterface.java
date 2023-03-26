package thederpgamer.combattweaks.element.blocks;

import api.listener.events.block.SegmentPieceActivateByPlayer;
import api.listener.events.block.SegmentPieceActivateEvent;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public interface BlockActivationInterface {

	void onPlayerActivate(SegmentPieceActivateByPlayer event);

	void onLogicActivate(SegmentPieceActivateEvent event);
}
