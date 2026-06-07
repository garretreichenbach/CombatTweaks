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
 * Segment-buffer iterator that finds the valid block on a structure <em>nearest</em> a given point.
 *
 * <p>It checks <em>every</em> valid block, not just the first per segment. Picking only the first block
 * of the nearest segment aims at an arbitrary corner of a 32&sup3; segment — up to ~55 blocks off the
 * real nearest surface — so salvage beams rayed toward it skim past the rock and "shoot near it" without
 * hitting blocks. Comparison is done in the structure's <em>local</em> frame: the query point is
 * inverse-transformed once in {@link #reset}, then each block is compared in local space (rigid
 * transforms preserve distance, so the nearest block is the same in both frames). That avoids a
 * per-block world-transform, which keeps the full scan cheap enough to run each mining tick.</p>
 *
 * <p>Kept as a normal top-level class (not nested inside a Mixin) because SpongePowered Mixin cannot
 * reliably relocate inner classes declared inside a {@code @Mixin} type — doing so produces a mangled
 * {@code $<hash>} class that fails to load at runtime ({@code NoClassDefFoundError}).</p>
 */
public final class NearestBlockPiece implements SegmentBufferIteratorInterface {

	/** Query point expressed in the structure's local frame (set once in {@link #reset}). */
	private final Vector3f localPoint = new Vector3f();
	private final Vector3b helper = new Vector3b();
	private final SegmentPiece tmp = new SegmentPiece();
	private final Vector3f cand = new Vector3f();

	/** The segment + position of the nearest block found (valid only when {@link #found} is true). */
	public Segment bestSegment;
	public final Vector3b bestPos = new Vector3b();
	public boolean found;
	private float bestDistSq;

	/** Reset for a fresh search from {@code point} (world space), with the structure's world transform. */
	public void reset(Vector3f point, Transform worldTransform) {
		// Transform the query point into the structure's local frame once, so per-block comparisons need
		// no transform. getAbsolutePos returns local (structure-relative) coordinates.
		Transform inv = new Transform(worldTransform);
		inv.inverse();
		this.localPoint.set(point);
		inv.transform(this.localPoint);
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
				float dx = cand.x - localPoint.x;
				float dy = cand.y - localPoint.y;
				float dz = cand.z - localPoint.z;
				float distSq = dx * dx + dy * dy + dz * dz;
				if(distSq < bestDistSq) {
					bestDistSq = distSq;
					bestSegment = s;
					bestPos.set(helper);
					found = true;
				}
			}
		}
		return false;
	}
}
