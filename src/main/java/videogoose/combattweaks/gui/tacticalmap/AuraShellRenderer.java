package videogoose.combattweaks.gui.tacticalmap;

import org.lwjgl.BufferUtils;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.device.GraphicsDevice;
import org.schema.schine.graphicsengine.device.GraphicsDevice.BufferUsage;
import org.schema.schine.graphicsengine.device.GraphicsDevice.GpuBuffer;
import org.schema.schine.graphicsengine.device.GraphicsDevice.GpuVertexArray;
import org.schema.schine.graphicsengine.device.GraphicsDevice.PrimitiveType;
import org.schema.schine.graphicsengine.device.GraphicsDevice.VertexAttribType;
import org.schema.schine.graphicsengine.device.GraphicsDevice.VertexLayout;
import org.schema.schine.graphicsengine.shader.Shader;
import org.schema.schine.graphicsengine.shader.ShaderLibrary;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Draws translucent aura shells: one unit UV sphere, uploaded once, drawn through the {@link GraphicsDevice} with
 * the engine's {@code simpleColorShader} (position at location 0, flat {@code col} uniform, the tracked
 * modelview/projection). Works on both the OpenGL and Vulkan backends, unlike immediate mode, which the engine
 * no longer has. Callers place and size each shell with the {@link GlUtil} matrix stack and own the blend/depth state.
 */
final class AuraShellRenderer {

	private static final int LAT = 16;
	private static final int LON = 24;
	private static final int VERTICES = LAT * LON * 6;

	private static AuraShellRenderer instance;

	private GpuBuffer vbo;
	private GpuVertexArray vao;

	static AuraShellRenderer get() {
		if(instance == null) {
			instance = new AuraShellRenderer();
			instance.init();
		}
		return instance;
	}

	private void init() {
		ByteBuffer bytes = BufferUtils.createByteBuffer(VERTICES * 3 * 4);
		FloatBuffer data = bytes.asFloatBuffer();
		for(int i = 0; i < LAT; i++) {
			double lat0 = Math.PI * (-0.5 + (double) i / LAT);
			double lat1 = Math.PI * (-0.5 + (double) (i + 1) / LAT);
			for(int j = 0; j < LON; j++) {
				double lon0 = 2.0 * Math.PI * j / LON;
				double lon1 = 2.0 * Math.PI * (j + 1) / LON;
				// Two triangles per lat/lon cell.
				put(data, lat0, lon0);
				put(data, lat1, lon0);
				put(data, lat1, lon1);
				put(data, lat0, lon0);
				put(data, lat1, lon1);
				put(data, lat0, lon1);
			}
		}
		GraphicsDevice dev = GlUtil.getDevice();
		vbo = dev.createBuffer(BufferUsage.VERTEX, bytes.capacity(), bytes, false);
		vao = dev.createVertexArray(vbo, new VertexLayout(3 * 4).add(0, 3, VertexAttribType.FLOAT, false, 0));
	}

	private static void put(FloatBuffer data, double lat, double lon) {
		float ring = (float) Math.cos(lat);
		data.put((float) Math.cos(lon) * ring).put((float) Math.sin(lat)).put((float) Math.sin(lon) * ring);
	}

	/** Binds the colour shader; call once before a run of {@link #draw}, then {@link #end}. False if it isn't loaded. */
	boolean begin() {
		Shader shader = ShaderLibrary.simpleColorShader;
		if(shader == null) {
			return false;
		}
		shader.loadWithoutUpdate();
		return true;
	}

	/** Draws the unit sphere under the current modelview (scale it there) in the given colour. */
	void draw(float r, float g, float b, float a) {
		GlUtil.updateShaderVector4f(ShaderLibrary.simpleColorShader, "col", r, g, b, a);
		GraphicsDevice dev = GlUtil.getDevice();
		dev.bindVertexArray(vao);
		dev.draw(PrimitiveType.TRIANGLES, 0, VERTICES);
		dev.bindVertexArray(null);
	}

	void end() {
		ShaderLibrary.simpleColorShader.unloadWithoutExit();
	}
}
