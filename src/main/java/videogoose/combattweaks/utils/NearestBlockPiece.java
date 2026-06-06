package videogoose.combattweaks.utils;

import com.bulletphysics.linearmath.Transform;
import org.schema.common.util.linAlg.Vector3b;
import org.schema.game.common.controller.SegmentBufferIteratorInterface;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.world.Segment;
import org.schema.game.common.data.world.SegmentData;

import javax.vecmath.Vector3f;

/**
 * Segment-buffer iterator that finds the valid block on a structure nearest a given point.
 *
 * <p>Kept as a normal top-level class (not nested inside a Mixin) because SpongePowered Mixin cannot
 * reliably relocate inner classes declared inside a {@code @Mixin} type — doing so produces a mangled
 * {@code $<hash>} class that fails to load at runtime ({@code NoClassDefFoundError}).</p>
 */
public final class NearestBlockPiece implements SegmentBufferIteratorInterface {

	private final Vector3f point = new Vector3f();
	private Transform worldTransform;
	private final Vector3b helper = new Vector3b();
	private final SegmentPiece tmp = new SegmentPiece();
	private final Vector3f cand = new Vector3f();

	/** The segment + position of the nearest block found (valid only when {@link #found} is true). */
	public Segment bestSegment;
	public final Vector3b bestPos = new Vector3b();
	public boolean found;
	private float bestDistSq;

	/** Reset for a fresh search from {@code point}, with the structure's world transform. */
	public void reset(Vector3f point, Transform worldTransform) {
		this.point.set(point);
		this.worldTransform = worldTransform;
		this.bestSegment = null;
		this.found = false;
		this.bestDistSq = Float.MAX_VALUE;
	}

	@Override
	public boolean handle(Segment s, long lastChanged) {
		SegmentData data = s.getSegmentData();
		if(data == null) {
			return false;
		}
		for(int i = 0; i < SegmentData.BLOCK_COUNT; i++) {
			if(ElementKeyMap.isValidType(data.getType(i))) {
				SegmentData.getPositionFromIndex(i, helper);
				tmp.setByReference(s, helper);
				tmp.getAbsolutePos(cand);
				cand.x -= SegmentData.SEG_HALF;
				cand.y -= SegmentData.SEG_HALF;
				cand.z -= SegmentData.SEG_HALF;
				worldTransform.transform(cand);
				float dx = cand.x - point.x;
				float dy = cand.y - point.y;
				float dz = cand.z - point.z;
				float distSq = dx * dx + dy * dy + dz * dz;
				if(distSq < bestDistSq) {
					bestDistSq = distSq;
					bestSegment = s;
					bestPos.set(helper);
					found = true;
				}
				break; // first valid block per segment is enough to rank segments by nearness
			}
		}
		return false;
	}
}
