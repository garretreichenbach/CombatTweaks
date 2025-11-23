package thederpgamer.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import org.schema.game.client.view.gui.shiphud.HudIndicatorOverlay;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import thederpgamer.combattweaks.manager.ResourceManager;

import javax.vecmath.Vector4f;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple pool for sprites and label overlays used by tactical-map indicators.
 * Keeps a small concurrent pool and lazily creates instances on demand.
 */
public class TacticalMapIndicatorPool {
	private static final TacticalMapIndicatorPool INSTANCE = new TacticalMapIndicatorPool();
	private final ConcurrentLinkedQueue<Sprite> spritePool = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<GUITextOverlay> labelPool = new ConcurrentLinkedQueue<>();

	public static TacticalMapIndicatorPool getInstance() {
		return INSTANCE;
	}

	public Sprite acquireSprite() {
		Sprite s = spritePool.poll();
		if(s == null) {
			s = ResourceManager.getSprite("tactical-map-indicators");
			s.setMultiSpriteMax(8, 2);
			s.setWidth(s.getMaterial().getTexture().getWidth() / 8);
			s.setHeight(s.getMaterial().getTexture().getHeight() / 2);
			s.setPositionCenter(true);
			s.setSelectionAreaLength(15.0f);
			s.setBillboard(true);
			s.setDepthTest(false);
			s.setBlend(true);
			s.setFlip(true);
			s.onInit();
		}
		// Reset tint/transform/state that might have been left by previous owner
		try {
			s.setTint(new Vector4f(1.0f, 1.0f, 1.0f, 1.0f));
		} catch(Exception ignored) {
		}
		return s;
	}

	public void releaseSprite(Sprite s) {
		if(s == null) return;
		// Reset transform/state but do not call cleanUp() because we intend to reuse the object
		try {
			s.setTransform(null);
		} catch(Exception ignored) {
		}
		spritePool.offer(s);
	}

	public GUITextOverlay acquireLabelOverlay() {
		GUITextOverlay o = labelPool.poll();
		if(o == null) {
			// Try to get a reasonable GUI state (HUD indicator state) to attach overlays
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
			// Mirror previous behavior
			try {
				o.getScale().y *= -1;
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

