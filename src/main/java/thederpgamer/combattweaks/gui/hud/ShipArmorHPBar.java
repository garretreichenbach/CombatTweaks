package thederpgamer.combattweaks.gui.hud;

import api.utils.game.SegmentControllerUtils;
import org.schema.common.util.linAlg.Vector4i;
import org.schema.game.client.data.GameClientState;
import org.schema.game.client.view.gui.shiphud.newhud.FillableBarOne;
import org.schema.game.client.view.gui.shiphud.newhud.GUIPosition;
import org.schema.game.common.controller.ManagedUsableSegmentController;
import org.schema.game.common.controller.elements.ElementCollectionManager;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;
import org.schema.schine.common.language.Lng;
import org.schema.schine.input.InputState;
import thederpgamer.combattweaks.manager.ConfigManager;
import thederpgamer.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector2f;

/**
 * [Description]
 *
 * @author TheDerpGamer (TheDerpGamer#0027)
 */
public class ShipArmorHPBar extends FillableBarOne {
	public ShipArmorHPBar(InputState inputState) {
		super(inputState);
	}

	@Override
	public void draw() {
		super.draw();
		SimpleTransformableSendableObject<?> currentPlayerObject = ((GameClientState) getState()).getCurrentPlayerObject();
		if(currentPlayerObject instanceof ManagedUsableSegmentController<?>) drawText();
	}

	@Override
	public float getFilledOne() {
		SimpleTransformableSendableObject<?> currentPlayerObject = ((GameClientState) getState()).getCurrentPlayerObject();
		if(currentPlayerObject instanceof ManagedUsableSegmentController<?>) {
			float hp = 0;
			float maxHP = 0;
			/**/for(ElementCollectionManager<?, ?, ?> collection : SegmentControllerUtils.getCollectionManagers((ManagedUsableSegmentController<?>) currentPlayerObject, ArmorHPCollection.class)) {
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
		return ConfigManager.getHudConfig().getBoolean("ship-armor-hp-bar-flipped-x");
	}

	@Override
	public boolean isBarFlippedY() {
		return ConfigManager.getHudConfig().getBoolean("ship-armor-hp-bar-flipped-y");
	}

	@Override
	public boolean isFillStatusTextOnTop() {
		return ConfigManager.getHudConfig().getBoolean("ship-armor-hp-bar-text-on-top");
	}

	@Override
	public Vector2f getOffsetText() {
		String textPos = ConfigManager.getHudConfig().getString("ship-armor-hp-bar-text-pos");
		String[] textPosSplit = textPos.split(", ");
		return new Vector2f(Float.parseFloat(textPosSplit[0]), Float.parseFloat(textPosSplit[1]));
	}

	@Override
	public String getText(int i) {
		return "";
	}

	@Override
	public Vector4i getConfigColor() {
		String hex = ConfigManager.getHudConfig().getString("ship-armor-hp-bar-color");
		return new Vector4i(Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16), Integer.parseInt(hex.substring(6, 8), 16));
	}

	@Override
	public GUIPosition getConfigPosition() {
		return null;
	}

	@Override
	public Vector2f getConfigOffset() {
		String offset = ConfigManager.getHudConfig().getString("ship-armor-hp-bar-offset");
		String[] offsetSplit = offset.split(", ");
		return new Vector2f(Float.parseFloat(offsetSplit[0]), Float.parseFloat(offsetSplit[1]));
	}

	@Override
	protected String getTag() {
		return "ArmorHPBar";
	}
}
