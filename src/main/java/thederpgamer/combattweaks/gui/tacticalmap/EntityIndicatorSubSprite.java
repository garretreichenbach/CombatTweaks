package thederpgamer.combattweaks.gui.tacticalmap;

import api.common.GameClient;
import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3fTools;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.forms.Sprite;
import org.schema.schine.graphicsengine.forms.TransformableSubSprite;

import javax.vecmath.Vector3f;

/**
 * <Description>
 *
 * @author TheDerpGamer
 * @since 08/24/2021
 */
public class EntityIndicatorSubSprite implements TransformableSubSprite {

    private final TacticalMapEntityIndicator indicator;

    public EntityIndicatorSubSprite(TacticalMapEntityIndicator indicator) {
        this.indicator = indicator;
    }

    @Override
    public float getScale(long l) {
        if(getCurrentEntity() != null) {
            Vector3f currentPos = getCurrentEntity().getWorldTransform().origin;
            Vector3f entityPos = indicator.getEntity().getWorldTransform().origin;
            float distance = Math.abs(Vector3fTools.distance(currentPos.x, currentPos.y, currentPos.z, entityPos.x, entityPos.y, entityPos.z));
            if(distance < 500) return 1.0f;
            else if(distance < 1000) return 0.8f;
            else if(distance < 3000) return 0.6f;
            else if(distance < 5000) return 0.4f;
            else if(distance < 10000) return 0.2f;
        }
        return 0.0f;
    }

    @Override
    public int getSubSprite(Sprite sprite) {
        return indicator.getSpriteIndex();
    }

    @Override
    public boolean canDraw() {
        return true;
    }

    @Override
    public Transform getWorldTransform() {
        return indicator.getEntity().getWorldTransform();
    }

    private SegmentController getCurrentEntity() {
        if(GameClient.getCurrentControl() != null && GameClient.getCurrentControl() instanceof SegmentController) {
            return (SegmentController) GameClient.getCurrentControl();
        } else return null;
    }
}