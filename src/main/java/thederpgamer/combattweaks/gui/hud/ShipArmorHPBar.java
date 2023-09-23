package thederpgamer.combattweaks.gui.hud;

import api.utils.game.SegmentControllerUtils;
import org.schema.common.config.ConfigurationElement;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.newhud.FillableBarOne;
import org.schema.game.client.view.gui.shiphud.newhud.GUIPosition;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.ManagedSegmentController;
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
public class ShipArmorHPBar extends FillableBarOne {
	@ConfigurationElement(name = "Color")
	public static Vector4i COLOR;
	@ConfigurationElement(name = "Position")
	public static GUIPosition POSITION;
	@ConfigurationElement(name = "Offset")
	public static Vector2f OFFSET;
	@ConfigurationElement(name = "FlipX")
	public static boolean FLIPX;
	@ConfigurationElement(name = "FlipY")
	public static boolean FLIPY;
	@ConfigurationElement(name = "FillStatusTextOnTop")
	public static boolean FILL_ON_TOP;
	@ConfigurationElement(name = "OffsetText")
	public static Vector2f OFFSET_TEXT;

	public ShipArmorHPBar(InputState inputState) {
		super(inputState);
	}

	@Override
	public float getFilledOne() {
		SimpleTransformableSendableObject<?> currentPlayerObject = ((GameClientState) getState()).getCurrentPlayerObject();
		if(currentPlayerObject instanceof ManagedUsableSegmentController<?>) {
			float hp = 0;
			float maxHP = 0;
			for(ElementCollectionManager<?, ?, ?> collection : SegmentControllerUtils.getCollectionManagers((ManagedSegmentController<?>) currentPlayerObject, ArmorHPCollection.class)) {
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
	protected String getDisplayTitle() {
		return Lng.str("Armor HP");
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
	public Vector2f getOffsetText() {
		return new Vector2f(OFFSET_TEXT.x - 95, OFFSET_TEXT.y - 7);
	}

	@Override
	public String getText(int i) {
		SimpleTransformableSendableObject<?> currentPlayerObject = ((GameClientState) getState()).getCurrentPlayerObject();
		if(currentPlayerObject instanceof ManagedUsableSegmentController<?>) {
			float hp = 0;
			float maxHP = 0;
			for(ElementCollectionManager<?, ?, ?> collection : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) currentPlayerObject, ArmorHPCollection.class)) {
				if(collection instanceof ArmorHPCollection) {
					ArmorHPCollection armorHPCollection = (ArmorHPCollection) collection;
					hp += armorHPCollection.getCurrentHP();
					maxHP += armorHPCollection.getMaxHP();
				}
			}
			return (hp / maxHP) * 100 + "%";
		} else return "0%";
	}

	@Override
	public Vector4i getConfigColor() {
		Color color = Color.decode("#7c97a3");
		return new Vector4i(color.getRed(), color.getGreen(), color.getBlue(), 255);
	}

	@Override
	public GUIPosition getConfigPosition() {
		return POSITION;
	}

	@Override
	public Vector2f getConfigOffset() {
		if(OFFSET.x == 115) OFFSET.x = 78;
		return OFFSET;
	}

	@Override
	protected String getTag() {
		return "ShipArmorHPBar";
	}
}
