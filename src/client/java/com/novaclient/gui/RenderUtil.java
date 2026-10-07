package com.novaclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

/**
 * Drawing helpers. Shapes are drawn in real screen pixels rather than GUI pixels
 * (which are 2-3x bigger), and rounded corners get partially transparent edge pixels
 * (anti-aliasing), so curves look smooth instead of stair-stepped.
 *
 * Everything drawn through here is multiplied by a global alpha, which is how the
 * whole menu fades in and out together.
 */
public final class RenderUtil {
	public static final int TOP_LEFT = 1;
	public static final int TOP_RIGHT = 2;
	public static final int BOTTOM_LEFT = 4;
	public static final int BOTTOM_RIGHT = 8;
	public static final int ALL_CORNERS = TOP_LEFT | TOP_RIGHT | BOTTOM_LEFT | BOTTOM_RIGHT;

	public static final int TOGGLE_WIDTH = 22;
	public static final int TOGGLE_HEIGHT = 12;

	private static float alpha = 1f;

	private RenderUtil() {
	}

	public static void setAlpha(float value) {
		alpha = Math.clamp(value, 0f, 1f);
	}

	public static float getAlpha() {
		return alpha;
	}

	private static int fade(int color) {
		return Theme.withAlpha(color, alpha);
	}

	private static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	public static void roundedRect(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, int color) {
		roundedRect(graphics, x, y, w, h, radius, color, ALL_CORNERS);
	}

	/** A filled rectangle. corners picks which corners are rounded (e.g. TOP_LEFT | BOTTOM_LEFT). */
	public static void roundedRect(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius,
			int color, int corners) {
		int c = fade(color);
		if ((c >>> 24) == 0) {
			return;
		}
		float s = guiScale();
		int x0 = Math.round(x * s);
		int y0 = Math.round(y * s);
		int x1 = Math.round((x + w) * s);
		int y1 = Math.round((y + h) * s);
		if (x1 <= x0 || y1 <= y0) {
			return;
		}
		float r = Math.min(radius * s, Math.min(x1 - x0, y1 - y0) / 2f);
		int band = (int) Math.ceil(r);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(1 / s, 1 / s);
		if (band == 0) {
			graphics.fill(x0, y0, x1, y1, c);
		} else {
			if (y1 - band > y0 + band) {
				graphics.fill(x0, y0 + band, x1, y1 - band, c);
			}
			int topEnd = Math.min(y0 + band, y1);
			for (int py = y0; py < topEnd; py++) {
				cornerRow(graphics, x0, x1, py, py - y0, r, c,
						(corners & TOP_LEFT) != 0, (corners & TOP_RIGHT) != 0);
			}
			for (int py = Math.max(y1 - band, topEnd); py < y1; py++) {
				cornerRow(graphics, x0, x1, py, y1 - 1 - py, r, c,
						(corners & BOTTOM_LEFT) != 0, (corners & BOTTOM_RIGHT) != 0);
			}
		}
		pose.popMatrix();
	}

	/**
	 * Draws one pixel row near the top or bottom edge. row is the distance from that edge.
	 * Pixels fully inside the curve become one solid run; edge pixels get alpha based on coverage.
	 */
	private static void cornerRow(GuiGraphicsExtractor graphics, int x0, int x1, int y, int row, float r, int color,
			boolean roundLeft, boolean roundRight) {
		int width = x1 - x0;
		int leftLimit = width / 2;
		int rightLimit = width - leftLimit;
		int leftStart = roundLeft ? solidStart(row, r, leftLimit) : 0;
		int rightStart = roundRight ? solidStart(row, r, rightLimit) : 0;

		if (x1 - rightStart > x0 + leftStart) {
			graphics.fill(x0 + leftStart, y, x1 - rightStart, y + 1, color);
		}
		for (int col = 0; col < leftStart; col++) {
			edgePixel(graphics, x0 + col, y, coverage(col, row, r), color);
		}
		for (int col = 0; col < rightStart; col++) {
			edgePixel(graphics, x1 - 1 - col, y, coverage(col, row, r), color);
		}
	}

	/** Index of the first fully-covered pixel in this row, counting in from the side. */
	private static int solidStart(int row, float r, int limit) {
		int col = 0;
		while (col < limit && coverage(col, row, r) < 0.999f) {
			col++;
		}
		return col;
	}

	/** How much of the pixel at (col, row) from the corner lies inside a circle of radius r. */
	private static float coverage(int col, int row, float r) {
		float dx = r - (col + 0.5f);
		float dy = r - (row + 0.5f);
		if (dx <= 0 || dy <= 0) {
			return 1f;
		}
		float distance = (float) Math.sqrt(dx * dx + dy * dy);
		return Math.clamp(r - distance + 0.5f, 0f, 1f);
	}

	private static void edgePixel(GuiGraphicsExtractor graphics, int x, int y, float coverage, int color) {
		if (coverage > 0.01f) {
			graphics.fill(x, y, x + 1, y + 1, Theme.withAlpha(color, coverage));
		}
	}

	/** A soft drop shadow made from a few stacked, slightly larger, very faint rounded rects. */
	public static void shadow(GuiGraphicsExtractor graphics, float x, float y, float w, float h, float radius, float strength) {
		if (strength <= 0) {
			return;
		}
		for (int i = 1; i <= 4; i++) {
			roundedRect(graphics, x - i, y - i + 2, w + i * 2, h + i * 2, radius + i,
					Theme.withAlpha(Theme.SHADOW, 0.07f * strength));
		}
	}

	/** Text without a drop shadow, snapped to whole screen pixels so it stays crisp. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color) {
		int c = fade(color);
		if ((c >>> 24) < 4) {
			return;
		}
		float s = guiScale();
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(Math.round(x * s) / s, Math.round(y * s) / s);
		graphics.text(font, text, 0, 0, c, false);
		pose.popMatrix();
	}

	/** Shortens text with "..." so it fits in maxWidth. */
	public static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
	}

	/** A pill switch. progress 0 = off (gray, knob left), 1 = on (accent, knob right), in-between while animating. */
	public static void toggleSwitch(GuiGraphicsExtractor graphics, float x, float y, float progress) {
		int track = Theme.lerp(Theme.TOGGLE_OFF, Theme.accent(), progress);
		roundedRect(graphics, x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, TOGGLE_HEIGHT / 2f, track);
		float knob = TOGGLE_HEIGHT - 4;
		float knobX = x + 2 + (TOGGLE_WIDTH - knob - 4) * progress;
		roundedRect(graphics, knobX, y + 2, knob, knob, knob / 2, Theme.TEXT);
	}

	/** A thin slider track filled up to progress (0 to 1), with a round knob. */
	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float width, double progress, int fillColor) {
		roundedRect(graphics, x, y, width, 4, 2, Theme.TOGGLE_OFF);
		float filled = (float) (width * progress);
		if (filled > 0.5f) {
			roundedRect(graphics, x, y, filled, 4, 2, fillColor);
		}
		roundedRect(graphics, x + filled - 4, y - 2, 8, 8, 4, Theme.TEXT);
	}
}
