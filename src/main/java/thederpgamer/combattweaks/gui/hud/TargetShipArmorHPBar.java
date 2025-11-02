package thederpgamer.combattweaks.gui.hud;

import api.common.GameClient;
import api.utils.game.SegmentControllerUtils;
import org.schema.common.config.ConfigurationElement;
import org.schema.common.util.StringTools;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.newhud.FillableHorizontalBar;
import org.schema.game.client.view.gui.shiphud.newhud.GUIPosition;
import org.schema.game.client.view.gui.shiphud.newhud.TargetPanel;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.graphicsengine.forms.gui.GUIScrollablePanel;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.input.InputState;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector2f;
import java.awt.*;

public class TargetShipArmorHPBar extends FillableHorizontalBar {

	@ConfigurationElement(name = "Color")
	public static Vector4i COLOR = new Vector4i(98, 210, 66, 0);

	@ConfigurationElement(name = "Offset")
	public static Vector2f OFFSET = new Vector2f(0, 136);

	@ConfigurationElement(name = "FlipX")
	public static boolean FLIPX;

	@ConfigurationElement(name = "FlipY")
	public static boolean FLIPY;

	@ConfigurationElement(name = "FillStatusTextOnTop")
	public static boolean FILL_ON_TOP;

	@ConfigurationElement(name = "TextPos")
	public static Vector2f TEXT_POS = new Vector2f(200, 2);

	@ConfigurationElement(name = "TextDescPos")
	public static Vector2f TEXT_DESC_POS = new Vector2f(4, 2);

	private GUITextOverlay massTextOverlay;
	private GUITextOverlay speedTextOverlay;
	private GUIScrollablePanel factionPanel;

	public TargetShipArmorHPBar(InputState inputState) {
		super(inputState);
	}

	private TargetPanel getTargetPanel() {
		return GameClient.getClientState().getWorldDrawer().getGuiDrawer().getHud().getTargetPanel();
	}

	@Override
	public void onInit() {
		super.onInit();
		massTextOverlay = getTargetPanel().getMassTextOverlay();
		speedTextOverlay = getTargetPanel().getSpeedTextOverlay();
		factionPanel = getTargetPanel().getFactionScroller();
	}

	@Override
	public void draw() {
		if(!(GameClient.getClientState().getSelectedEntity() instanceof ManagedUsableSegmentController<?>)) {
			return;
		}
		setPos(OFFSET.x, OFFSET.y, 0);
		massTextOverlay.getPos().y = getPos().y + 25;
		speedTextOverlay.getPos().y = getPos().y + 25;
		factionPanel.getPos().y = getPos().y + 45;
		super.draw();
	}

	@Override
	public boolean isBarFlippedX() {
		return FLIPX;
	}

	@Override
	public boolean isBarFlippedY() {
		return FLIPY;
	}

	@Override
	public boolean isFillStatusTextOnTop() {
		return FILL_ON_TOP;
	}

	@Override
	public Vector2f getTextPos() {
		return new Vector2f(TEXT_POS.x, TEXT_POS.y - 7);
	}

	@Override
	public Vector2f getTextDescPos() {
		return new Vector2f(TEXT_DESC_POS.x, TEXT_DESC_POS.y - 7);
	}

	@Override
	public float getFilled() {
		SimpleTransformableSendableObject<?> targetObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
		if(targetObject instanceof ManagedUsableSegmentController<?>) {
			float hp = 0;
			float maxHP = 0;
			for(ElementCollectionManager<?, ?, ?> collection : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) targetObject, ArmorHPCollection.class)) {
				if(collection instanceof ArmorHPCollection) {
					ArmorHPCollection armorHPCollection = (ArmorHPCollection) collection;
					hp += (float) armorHPCollection.getCurrentHP();
					maxHP += (float) armorHPCollection.getMaxHP();
				}
			}
			return Math.max(0, Math.min(1, hp / maxHP));
		} else {
			return 0;
		}
	}

	@Override
	public String getText() {
		SimpleTransformableSendableObject<?> targetObject = ((GameClientState) getState()).getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getSelectedEntity();
		if(targetObject instanceof ManagedUsableSegmentController<?>) {
			float hp = 0;
			float maxHP = 0;
			for(ElementCollectionManager<?, ?, ?> collection : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) targetObject, ArmorHPCollection.class)) {
				if(collection instanceof ArmorHPCollection) {
					ArmorHPCollection armorHPCollection = (ArmorHPCollection) collection;
					hp += (float) armorHPCollection.getCurrentHP();
					maxHP += (float) armorHPCollection.getMaxHP();
				}
			}
			return Lng.str("Armor HP") + " " + StringTools.massFormat(hp) + " / " + StringTools.massFormat(maxHP);
		} else {
			return Lng.str("Armor N/A");
		}
	}

	@Override
	public Vector4i getConfigColor() {
		Color color = Color.decode("#7c97a3");
		return new Vector4i(color.getRed(), color.getGreen(), color.getBlue(), 255);
	}

	@Override
	public GUIPosition getConfigPosition() {
		return null;
	}

	@Override
	public Vector2f getConfigOffset() {
		return OFFSET;
	}

	@Override
	protected String getTag() {
		return "TargetArmorHPBar";
	}
}
