package thederpgamer.combattweaks.gui.hud;

import api.utils.game.SegmentControllerUtils;
import org.schema.common.config.ConfigurationElement;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.newhud.FillableHorizontalBar;
import org.schema.game.client.view.gui.shiphud.newhud.GUIPosition;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.input.InputState;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector2f;
import java.awt.*;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class TargetShipArmorHPBar extends FillableHorizontalBar {

	@ConfigurationElement(name = "Color")
	public static Vector4i COLOR;

	@ConfigurationElement(name = "Offset")
	public static Vector2f OFFSET;

	@ConfigurationElement(name = "FlipX")
	public static boolean FLIPX;
	@ConfigurationElement(name = "FlipY")
	public static boolean FLIPY;

	@ConfigurationElement(name = "FillStatusTextOnTop")
	public static boolean FILL_ON_TOP;

	@ConfigurationElement(name = "TextPos")
	public static Vector2f TEXT_POS;

	@ConfigurationElement(name = "TextDescPos")
	public static Vector2f TEXT_DESC_POS;


	public TargetShipArmorHPBar(InputState inputState) {
		super(inputState);
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
					hp += armorHPCollection.getCurrentHP();
					maxHP += armorHPCollection.getMaxHP();
				}
			}
			return hp / maxHP;
		} else return 0;
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
					hp += armorHPCollection.getCurrentHP();
					maxHP += armorHPCollection.getMaxHP();
				}
			}
			return Lng.str("Armor HP") + " " + ((int) (hp / maxHP * 100)) + "%";
		} else return Lng.str("Armor n/a");
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
		if(OFFSET.y == 65) OFFSET.y = 136;
		return OFFSET;
	}

	@Override
	protected String getTag() {
		return "TargetArmorHPBar";
	}
}
