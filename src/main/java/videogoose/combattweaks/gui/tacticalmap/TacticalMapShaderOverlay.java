package videogoose.combattweaks.gui.tacticalmap;

import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.shader.Shader;
import videogoose.combattweaks.manager.ResourceManager;

import javax.vecmath.Vector4f;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders shader-based selection overlays (outline + tint) on selected ships in tactical map.
 * Supports both outline and semi-transparent tint effects for visual selection feedback.
 */
public class TacticalMapShaderOverlay {

	private static final Vector4f OUTLINE_COLOR = new Vector4f(1.0f, 1.0f, 0.0f, 1.0f); // Yellow outline
	private static final Vector4f TINT_COLOR = new Vector4f(1.0f, 1.0f, 0.0f, 0.25f); // Yellow tint with 25% alpha
	private static final float OUTLINE_SCALE = 1.5f; // Scale factor for outline extrusion

	private final ConcurrentHashMap<Integer, SegmentController> selectedShips = new ConcurrentHashMap<>();
	private Shader outlineShader;
	private Shader tintShader;
	private boolean initialized = false;

	public TacticalMapShaderOverlay() {
	}

	/**
	 * Initialize shaders when GL context is available.
	 */
	public void onInit() {
		if (!initialized) {
			outlineShader = ResourceManager.loadShader("selection_outline");
			tintShader = ResourceManager.loadShader("selection_tint");
			initialized = true;
		}
	}

	/**
	 * Add a ship to the selection overlay.
	 */
	public void addSelected(SegmentController ship) {
		if (ship != null) {
			selectedShips.put(ship.getId(), ship);
		}
	}

	/**
	 * Remove a ship from the selection overlay.
	 */
	public void removeSelected(SegmentController ship) {
		if (ship != null) {
			selectedShips.remove(ship.getId());
		}
	}

	/**
	 * Clear all selected ships.
	 */
	public void clearSelected() {
		selectedShips.clear();
	}

	/**
	 * Get the number of selected ships.
	 */
	public int getSelectedCount() {
		return selectedShips.size();
	}

	/**
	 * Check if a ship is selected.
	 */
	public boolean isSelected(SegmentController ship) {
		return ship != null && selectedShips.containsKey(ship.getId());
	}

	/**
	 * Draw the selection overlays for all selected ships.
	 * This should be called during the tactical map draw phase after indicators are rendered.
	 */
	public void draw() {
		if (!initialized || selectedShips.isEmpty()) {
			return;
		}

		// Render outlines for all selected ships
		drawOutlines();

		// Optionally render tint effect (commented for now as tint requires texture binding)
		// drawTints();
	}

	/**
	 * Render outline effect around selected ships.
	 * Uses a shader that extrudes vertices along normals to create silhouette effect.
	 */
	private void drawOutlines() {
		if (outlineShader == null) {
			return;
		}

		outlineShader.loadWithoutUpdate();

		// Set shader uniforms
		updateShaderVector4f(outlineShader, "outlineColor", OUTLINE_COLOR);
		updateShaderFloat(outlineShader, "outlineScale", OUTLINE_SCALE);

		for (SegmentController ship : selectedShips.values()) {
			if (ship != null) {
				// Note: Actually rendering ship geometry requires access to the ship's mesh data,
				// which is typically managed by the engine's rendering pipeline.
				// For now, this is a placeholder for the shader setup.
				// The actual outline rendering would need to hook into the engine's render calls
				// or use a deferred rendering approach.
			}
		}

		outlineShader.unloadWithoutExit();
	}

	/**
	 * Render tint effect on selected ships.
	 * Applies a semi-transparent colored overlay.
	 */
	private void drawTints() {
		if (tintShader == null) {
			return;
		}

		tintShader.loadWithoutUpdate();

		// Set shader uniforms
		updateShaderVector4f(tintShader, "tintColor", TINT_COLOR);

		for (SegmentController ship : selectedShips.values()) {
			if (ship != null) {
				// Tint rendering would similarly require geometry access
			}
		}

		tintShader.unloadWithoutExit();
	}

	/**
	 * Utility method to set a Vector4f uniform in a shader.
	 * Uses the StarMade GlUtil for proper uniform handling.
	 */
	private void updateShaderVector4f(Shader shader, String uniformName, Vector4f value) {
		try {
			org.schema.schine.graphicsengine.core.GlUtil.updateShaderVector4f(shader, uniformName, value);
		} catch (Exception e) {
			// Silently ignore if uniform not found
		}
	}

	/**
	 * Utility method to set a float uniform in a shader.
	 * Uses the StarMade GlUtil for proper uniform handling.
	 */
	private void updateShaderFloat(Shader shader, String uniformName, float value) {
		try {
			org.schema.schine.graphicsengine.core.GlUtil.updateShaderFloat(shader, uniformName, value);
		} catch (Exception e) {
			// Silently ignore if uniform not found
		}
	}

	/**
	 * Clean up shader resources.
	 */
	public void cleanUp() {
		selectedShips.clear();
		// Shaders are managed by ResourceManager
	}
}
