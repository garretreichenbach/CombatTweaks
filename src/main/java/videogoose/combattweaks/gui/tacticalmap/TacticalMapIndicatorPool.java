package videogoose.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple pool for label overlays used by tactical-map indicators.
 * Keeps a small concurrent pool and lazily creates instances on demand.
 * Sprite indicators are no longer used (ring indicators are rendered via shaders instead).
 */
public class TacticalMapIndicatorPool {

	private static final TacticalMapIndicatorPool INSTANCE = new TacticalMapIndicatorPool();
	private final ConcurrentLinkedQueue<GUITextOverlay> labelPool = new ConcurrentLinkedQueue<>();

	public static TacticalMapIndicatorPool getInstance() {
		return INSTANCE;
	}

	public GUITextOverlay acquireLabelOverlay() {
		GUITextOverlay o = labelPool.poll();
		if(o == null) {
			HudIndicatorOverlay hud = null;
			try {
				hud = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getIndicator();
			} catch(Exception ignored) {
			}
			if(hud != null) {
				o = new GUITextOverlay(32, 32, FontLibrary.FontSize.MEDIUM.getFont(), hud.getState());
			} else {
				// Fallback to constructing with null state (might be initialized later by caller)
				o = new GUITextOverlay(32, 32, FontLibrary.FontSize.MEDIUM.getFont(), null);
			}
			try {
				o.onInit();
			} catch(Exception ignored) {
			}
		}
		return o;
	}

	public void releaseLabelOverlay(GUITextOverlay o) {
		if(o == null) {
			return;
		}
		try {
			// Reset text to avoid stale contents
			o.setTextSimple("");
		} catch(Exception ignored) {
		}
		labelPool.offer(o);
	}
}

