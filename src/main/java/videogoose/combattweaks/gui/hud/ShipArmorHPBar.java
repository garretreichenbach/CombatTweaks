package videogoose.combattweaks.gui.hud;

import api.common.GameClient;
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
import videogoose.combattweaks.system.armor.ArmorHPCollection;

import javax.vecmath.Vector2f;
import java.awt.*;

public class ShipArmorHPBar extends FillableBarOne {

	@ConfigurationElement(name = "Color")
	public static Vector4i COLOR = new Vector4i(98, 210, 66, 0);

	@ConfigurationElement(name = "Position")
	public static GUIPosition POSITION = new GUIPosition();

	@ConfigurationElement(name = "Offset")
	public static Vector2f OFFSET = new Vector2f(115, 0);

	@ConfigurationElement(name = "FlipX")
	public static boolean FLIPX = true;

	@ConfigurationElement(name = "FlipY")
	public static boolean FLIPY;

	@ConfigurationElement(name = "FillStatusTextOnTop")
	public static boolean FILL_ON_TOP;

	@ConfigurationElement(name = "OffsetText")
	public static Vector2f OFFSET_TEXT = new Vector2f(8, 0);

	public ShipArmorHPBar(InputState inputState) {
		super(inputState);
		POSITION.value = ORIENTATION_VERTICAL_MIDDLE | ORIENTATION_LEFT;
	}

	@Override
	public void draw() {
		if(!GameClient.getClientState().isInFlightMode()) {
			return;
		}
		super.draw();
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
					hp += (float) armorHPCollection.getCurrentHP();
					maxHP += (float) armorHPCollection.getMaxHP();
				}
			}
			// Guard the divide: maxHP is 0 for an unarmored ship and on the client until the first armor-HP
			// sync arrives, so hp/maxHP would be 0/0 = NaN (which renders as a NaN-wide / NaN%% bar). The
			// `> 0` test also rejects a NaN maxHP. Empty/unsynced armor reads as an empty bar.
			return maxHP > 0 ? Math.max(0, Math.min(1, hp / maxHP)) : 0;
		} else {
			return 0;
		}
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
					hp += (float) armorHPCollection.getCurrentHP();
					maxHP += (float) armorHPCollection.getMaxHP();
				}
			}
			// Guard the divide (see getFilledOne): 0/0 would print "NaN%". Also round so the readout is a
			// clean integer percent instead of a long fractional tail.
			float pct = maxHP > 0 ? (hp / maxHP) * 100f : 0f;
			return Math.round(pct) + "%";
		} else {
			return "0%";
		}
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
		if(OFFSET.x == 115) {
			OFFSET.x = 78;
		}
		return OFFSET;
	}

	@Override
	protected String getTag() {
		return "ShipArmorHPBar";
	}
}
