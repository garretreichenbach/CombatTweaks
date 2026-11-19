package videogoose.combattweaks.gui.tacticalmap;

import org.lwjgl.opengl.GL11;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.forms.debug.DebugLineRenderer;

import java.util.Arrays;

/**
 * Stand-in for GL immediate mode ({@code glBegin}/{@code glVertex}/{@code glEnd}), which the engine no longer
 * has: raw GL11 calls have no context on the Vulkan backend (they abort the JVM) and {@code GlUtil.glBegin} is an
 * empty stub. Vertices are buffered between {@link #begin} and {@link #end} and then drawn through the engine's
 * {@link DebugLineRenderer} (a {@code GraphicsDevice} batch with per-vertex colour, under the current
 * {@link GlUtil} matrices and blend/depth state), so callers keep their immediate-mode shape. Each vertex takes
 * the current {@link GlUtil#glColor4f} colour, as {@code glVertex} did. Render thread only.
 * <p>
 * Supported modes: LINES, LINE_STRIP, LINE_LOOP, TRIANGLES, TRIANGLE_FAN, QUADS. Filled primitives use their first
 * vertex's colour. Line width is ignored (Vulkan has no portable wide lines); {@link #dashes} replaces line stipple.
 */
final class Immediate {

	private static final int STRIDE = 7; // x, y, z, r, g, b, a
	/** DebugLineRenderer drops line/triangle vertices past 32768 per batch; flush a little before that. */
	private static final int BATCH_LIMIT = 32000;

	private static int mode = -1;
	private static float[] verts = new float[STRIDE * 1024];
	private static int count;
	private static int dashCount;
	private static float dashDuty;
	private static int batchLineVerts;
	private static int batchTriVerts;

	private Immediate() {
	}

	/**
	 * Draw the next batch's lines as {@code count} dashes per segment, each {@code duty} (0–1) of its slot long —
	 * the world-space replacement for {@code glLineStipple}. Cleared by {@link #end}.
	 */
	static void dashes(int count, float duty) {
		dashCount = count;
		dashDuty = duty;
	}

	static void begin(int glMode) {
		mode = glMode;
		count = 0;
	}

	static void vertex(float x, float y) {
		vertex(x, y, 0.0f);
	}

	static void vertex(float x, float y, float z) {
		if((count + 1) * STRIDE > verts.length) {
			verts = Arrays.copyOf(verts, verts.length * 2);
		}
		int o = count * STRIDE;
		verts[o] = x;
		verts[o + 1] = y;
		verts[o + 2] = z;
		verts[o + 3] = GlUtil.getColorR();
		verts[o + 4] = GlUtil.getColorG();
		verts[o + 5] = GlUtil.getColorB();
		verts[o + 6] = GlUtil.getColorA();
		count++;
	}

	static void end() {
		int m = mode;
		mode = -1;
		if(count == 0 || m < 0) {
			dashCount = 0;
			return;
		}
		DebugLineRenderer out = DebugLineRenderer.get();
		out.begin();
		batchLineVerts = 0;
		batchTriVerts = 0;
		switch(m) {
			case GL11.GL_LINES -> {
				for(int i = 0; i + 1 < count; i += 2) line(out, i, i + 1);
			}
			case GL11.GL_LINE_STRIP -> {
				for(int i = 0; i + 1 < count; i++) line(out, i, i + 1);
			}
			case GL11.GL_LINE_LOOP -> {
				for(int i = 0; i + 1 < count; i++) line(out, i, i + 1);
				if(count > 2) line(out, count - 1, 0);
			}
			case GL11.GL_TRIANGLES -> {
				for(int i = 0; i + 2 < count; i += 3) tri(out, i, i + 1, i + 2);
			}
			case GL11.GL_TRIANGLE_FAN -> {
				for(int i = 1; i + 1 < count; i++) tri(out, 0, i, i + 1);
			}
			case GL11.GL_QUADS -> {
				for(int i = 0; i + 3 < count; i += 4) {
					tri(out, i, i + 1, i + 2);
					tri(out, i, i + 2, i + 3);
				}
			}
			default -> throw new IllegalArgumentException("Immediate: unsupported primitive mode " + m);
		}
		out.end();
		count = 0;
		dashCount = 0;
	}

	private static void room(DebugLineRenderer out, int lineVerts, int triVerts) {
		if(batchLineVerts + lineVerts > BATCH_LIMIT || batchTriVerts + triVerts > BATCH_LIMIT) {
			out.end();
			out.begin();
			batchLineVerts = 0;
			batchTriVerts = 0;
		}
		batchLineVerts += lineVerts;
		batchTriVerts += triVerts;
	}

	private static void line(DebugLineRenderer out, int a, int b) {
		int oa = a * STRIDE, ob = b * STRIDE;
		room(out, 2 * Math.max(1, dashCount), 0);
		if(dashCount <= 0) {
			out.line(verts[oa], verts[oa + 1], verts[oa + 2], verts[oa + 3], verts[oa + 4], verts[oa + 5], verts[oa + 6],
					verts[ob], verts[ob + 1], verts[ob + 2], verts[ob + 3], verts[ob + 4], verts[ob + 5], verts[ob + 6]);
			return;
		}
		for(int d = 0; d < dashCount; d++) {
			float t0 = (float) d / dashCount;
			float t1 = (d + dashDuty) / dashCount;
			out.line(lerp(oa, ob, 0, t0), lerp(oa, ob, 1, t0), lerp(oa, ob, 2, t0), lerp(oa, ob, 3, t0), lerp(oa, ob, 4, t0), lerp(oa, ob, 5, t0), lerp(oa, ob, 6, t0),
					lerp(oa, ob, 0, t1), lerp(oa, ob, 1, t1), lerp(oa, ob, 2, t1), lerp(oa, ob, 3, t1), lerp(oa, ob, 4, t1), lerp(oa, ob, 5, t1), lerp(oa, ob, 6, t1));
		}
	}

	private static float lerp(int oa, int ob, int c, float t) {
		return verts[oa + c] + (verts[ob + c] - verts[oa + c]) * t;
	}

	private static void tri(DebugLineRenderer out, int a, int b, int c) {
		int oa = a * STRIDE, ob = b * STRIDE, oc = c * STRIDE;
		room(out, 0, 3);
		out.tri(verts[oa], verts[oa + 1], verts[oa + 2], verts[ob], verts[ob + 1], verts[ob + 2], verts[oc], verts[oc + 1], verts[oc + 2],
				verts[oa + 3], verts[oa + 4], verts[oa + 5], verts[oa + 6]);
	}
}
