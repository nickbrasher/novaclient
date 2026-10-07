package com.novaclient.hud;

import com.novaclient.module.Category;
import com.novaclient.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/**
 * Base class for anything drawn on the in-game HUD. Subclasses only describe their
 * size and how to draw themselves at (0, 0); this class handles position, scale,
 * the HUD editor, and saving.
 *
 * Position is stored as a fraction (0 to 1) of the free space on screen, not pixels.
 * x = 0 means flush left, x = 1 means flush right, x = 0.5 means centered. That keeps
 * the layout intact when the window or GUI scale changes.
 */
public abstract class HudElement extends Module {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3f;

	private final float defaultX;
	private final float defaultY;
	private float x;
	private float y;
	private float scale = 1f;
	private long lastFrameNanos;

	protected HudElement(String name, String description, float defaultX, float defaultY) {
		this(name, description, Category.HUD, defaultX, defaultY);
	}

	/** For modules that draw on the HUD but belong in another category (e.g. Toggle Sprint in Utility). */
	protected HudElement(String name, String description, Category category, float defaultX, float defaultY) {
		super(name, description, category);
		this.defaultX = defaultX;
		this.defaultY = defaultY;
		this.x = defaultX;
		this.y = defaultY;
	}

	/** Width before scaling, in GUI pixels. */
	public abstract int getWidth();

	/** Height before scaling, in GUI pixels. */
	public abstract int getHeight();

	/**
	 * Draws the element with its top-left corner at (0, 0), unscaled.
	 * editing is true inside the HUD editor, e.g. to show sample content.
	 */
	protected abstract void render(GuiGraphicsExtractor graphics, float deltaSeconds, boolean editing);

	/** Positions, scales, and draws the element. Called by the HUD and by the editor. */
	public final void draw(GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, boolean editing) {
		long now = Util.getNanos();
		float deltaSeconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.1f);
		lastFrameNanos = now;

		// Same math as getBounds(), inlined so drawing doesn't create an object every frame.
		float left = x * Math.max(0, screenWidth - getWidth() * scale);
		float top = y * Math.max(0, screenHeight - getHeight() * scale);
		// Snap to whole screen pixels so text stays crisp.
		float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(left * guiScale) / guiScale, Math.round(top * guiScale) / guiScale);
		pose.scale(scale, scale);
		render(graphics, deltaSeconds, editing);
		pose.popMatrix();
	}

	/** Where the element is on screen right now, in GUI pixels, after scaling. */
	public Bounds getBounds(int screenWidth, int screenHeight) {
		float w = getWidth() * scale;
		float h = getHeight() * scale;
		return new Bounds(x * Math.max(0, screenWidth - w), y * Math.max(0, screenHeight - h), w, h);
	}

	/** Moves the element so its top-left corner is at (left, top) in GUI pixels. */
	public void setPosition(float left, float top, int screenWidth, int screenHeight) {
		float freeX = screenWidth - getWidth() * scale;
		float freeY = screenHeight - getHeight() * scale;
		x = freeX > 0 ? Math.clamp(left / freeX, 0f, 1f) : 0;
		y = freeY > 0 ? Math.clamp(top / freeY, 0f, 1f) : 0;
	}

	public void resetPosition() {
		x = defaultX;
		y = defaultY;
		scale = 1f;
	}

	public float getScale() {
		return scale;
	}

	/** Scale snaps to steps of 0.05 so text doesn't end up at awkward, blurry sizes. */
	public void setScale(float scale) {
		this.scale = Math.round(Math.clamp(scale, MIN_SCALE, MAX_SCALE) * 20) / 20f;
	}

	/** The stored fractions, for the config file. */
	public float getAnchorX() {
		return x;
	}

	public float getAnchorY() {
		return y;
	}

	public void setAnchor(float x, float y) {
		this.x = Math.clamp(x, 0f, 1f);
		this.y = Math.clamp(y, 0f, 1f);
	}

	/** A rectangle on screen. */
	public record Bounds(float x, float y, float w, float h) {
		public float right() {
			return x + w;
		}

		public float bottom() {
			return y + h;
		}

		public float centerX() {
			return x + w / 2;
		}

		public float centerY() {
			return y + h / 2;
		}

		public boolean contains(float px, float py, float margin) {
			return px >= x - margin && px < right() + margin && py >= y - margin && py < bottom() + margin;
		}
	}
}
