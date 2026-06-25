package videogoose.combattweaks.gui.hud;

import api.common.GameClient;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GLFrame;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUIOverlay;
import org.schema.schine.input.InputState;
import videogoose.combattweaks.gui.tacticalmap.TacticalMapGUIDrawer;

import javax.vecmath.Vector4f;

/**
 * HUD icon that lights up when the local player's piloted ship is inside an aura field — one icon for a friendly
 * support (buff) aura, one for a hostile offense (ECW) aura. Reuses the engine's otherwise-unused
 * {@code HUD_Sprites-8x8-gui-} sheet (the old buff/debuff icons): hexagon sub-sprites are buffs, triangles are
 * debuffs, so index {@value #SUPPORT_SPRITE} (shield-buff hexagon) flags the support aura and index
 * {@value #OFFENSE_SPRITE} (scattered-shots triangle) flags the ECW aura.
 *
 * <p>Drawn directly as a {@link GUIElement} (not a {@code HudConfig}) so it needs no HUD-config XML tag; the
 * affected-status comes from {@link TacticalMapGUIDrawer#getLocalPlayerAuraStatus()} (client-side, from the synced
 * aura set).</p>
 */
public class AuraHudIndicator extends GUIElement {

	/** HUD_Sprites-8x8-gui- sub-index for the shield-buff hexagon (support aura). */
	private static final int SUPPORT_SPRITE = 17;
	/** HUD_Sprites-8x8-gui- sub-index for the scattered-shots triangle (offense / ECW aura). */
	private static final int OFFENSE_SPRITE = 29;
	private static final Vector4f SUPPORT_TINT = new Vector4f(0.3f, 1.0f, 0.45f, 1.0f);
	private static final Vector4f OFFENSE_TINT = new Vector4f(1.0f, 0.5f, 0.1f, 1.0f);
	private static final float ICON_SIZE = 32.0f;
	private static final float ICON_SPACING = 38.0f;
	/** Pixels from the top of the screen to the icon row. */
	private static final float TOP_MARGIN = 96.0f;

	private GUIOverlay icons;

	public AuraHudIndicator(InputState state) {
		super(state);
	}

	@Override
	public void onInit() {
		icons = new GUIOverlay(Controller.getResLoader().getSprite("HUD_Sprites-8x8-gui-"), getState());
		icons.getSprite().setTint(new Vector4f(1, 1, 1, 1));
		icons.onInit();
	}

	@Override
	public void draw() {
		if(icons == null || GameClient.getClientState() == null || !GameClient.getClientState().isInFlightMode()) {
			return;
		}
		TacticalMapGUIDrawer drawer = TacticalMapGUIDrawer.getInstance();
		if(drawer == null) {
			return;
		}
		boolean[] status = drawer.getLocalPlayerAuraStatus();
		if(!status[0] && !status[1]) {
			return;
		}
		int count = (status[0] ? 1 : 0) + (status[1] ? 1 : 0);
		float startX = GLFrame.getWidth() / 2.0f - (count * ICON_SPACING) / 2.0f;
		float x = startX;
		if(status[0]) {
			drawIcon(SUPPORT_SPRITE, SUPPORT_TINT, x);
			x += ICON_SPACING;
		}
		if(status[1]) {
			drawIcon(OFFENSE_SPRITE, OFFENSE_TINT, x);
		}
	}

	private void drawIcon(int subIndex, Vector4f tint, float x) {
		icons.setSpriteSubIndex(subIndex);
		icons.getSprite().getTint().set(tint);
		icons.getPos().set(x, TOP_MARGIN, 0);
		icons.draw();
	}

	@Override
	public void cleanUp() {
		if(icons != null) {
			icons.cleanUp();
		}
	}

	@Override
	public float getWidth() {
		return ICON_SIZE;
	}

	@Override
	public float getHeight() {
		return ICON_SIZE;
	}
}
