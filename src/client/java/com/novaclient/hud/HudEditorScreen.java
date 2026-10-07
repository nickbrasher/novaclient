package com.novaclient.hud;

import com.novaclient.NovaClient;
import com.novaclient.gui.NovaMenuScreen;
import com.novaclient.gui.RenderUtil;
import com.novaclient.gui.Theme;
import com.novaclient.gui.anim.Animated;
import com.novaclient.gui.anim.Tween;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drag HUD elements around, resize them from a corner, and snap them to the screen
 * edges, center lines, and each other. Positions save automatically.
 */
public class HudEditorScreen extends Screen {
	private static final float SNAP_DISTANCE = 4f;
	private static final float HANDLE_SIZE = 5f;
	private static final float HANDLE_REACH = 5f; // how close to a corner counts as grabbing its handle
	private static final int BUTTON_HEIGHT = 18;
	private static final String[] HINT_LINES = {
			"Drag to move · Drag a corner to resize · Right-click for settings",
			"R to reset the hovered element · Hold Shift to skip snapping"
	};

	private final Tween openTween = new Tween(0.18f, 0f, true);
	private final Map<Object, Animated> hoverAnims = new HashMap<>();
	private final Animated toolbarVisibility = new Animated(1, 10f);
	private final List<Button> buttons = new ArrayList<>();

	private HudElement dragging;
	private boolean resizing;
	private float grabOffsetX;
	private float grabOffsetY;
	private float anchorX; // while resizing: the corner opposite the handle, which stays put
	private float anchorY;
	private boolean resizeFromLeft;
	private boolean resizeFromTop;
	private Float guideX; // snap guide lines currently shown (null = none)
	private Float guideY;

	private boolean closing;
	private boolean closeScheduled;
	private long lastFrameNanos;
	private float deltaSeconds;
	private float lastMouseX;
	private float lastMouseY;

	public HudEditorScreen() {
		super(Component.literal("Edit HUD"));
	}

	private List<HudElement> visibleElements() {
		List<HudElement> visible = new ArrayList<>();
		for (HudElement element : NovaClient.getModules().getHudElements()) {
			if (element.isEnabled()) {
				visible.add(element);
			}
		}
		return visible;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = Util.getNanos();
		deltaSeconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.1f);
		lastFrameNanos = now;
		openTween.update(deltaSeconds);
		if (closing && openTween.isFinished() && !closeScheduled) {
			closeScheduled = true;
			this.minecraft.schedule(() -> {
				if (this.minecraft.gui.screen() == this) {
					super.onClose();
				}
			});
		}

		if (this.minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		// A light tint so the game stays visible while you arrange things.
		graphics.fill(0, 0, this.width, this.height, Theme.withAlpha(0x59000000, openTween.eased()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		float open = openTween.eased();
		HudElement hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);

		// Elements always draw at full strength; only the editor chrome fades in.
		for (HudElement element : visibleElements()) {
			element.draw(graphics, this.width, this.height, true);
		}

		RenderUtil.setAlpha(open);
		drawGuides(graphics);
		for (HudElement element : visibleElements()) {
			drawOutline(graphics, element, element == hovered, mouseX, mouseY);
		}
		drawToolbar(graphics, mouseX, mouseY);
		RenderUtil.setAlpha(1f);
	}

	private void drawOutline(GuiGraphicsExtractor graphics, HudElement element, boolean hovered, int mouseX, int mouseY) {
		HudElement.Bounds b = element.getBounds(this.width, this.height);
		float hover = animate(element, hovered ? 1 : 0);
		int lineColor = Theme.lerp(Theme.withAlpha(Theme.TEXT, 0.35f), Theme.accent(), hover);

		RenderUtil.roundedRect(graphics, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, 2,
				Theme.withAlpha(Theme.accent(), 0.08f * hover));
		outline(graphics, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, lineColor);

		if (hover > 0.01f) {
			float previousAlpha = RenderUtil.getAlpha();
			RenderUtil.setAlpha(previousAlpha * hover);
			// Corner handles; the one under the mouse grows slightly.
			for (int corner = 0; corner < 4; corner++) {
				float cx = (corner % 2 == 0) ? b.x() - 2 : b.right() + 2;
				float cy = (corner < 2) ? b.y() - 2 : b.bottom() + 2;
				boolean overHandle = Math.abs(mouseX - cx) <= HANDLE_REACH && Math.abs(mouseY - cy) <= HANDLE_REACH;
				float size = overHandle || (resizing && dragging == element) ? HANDLE_SIZE + 2 : HANDLE_SIZE;
				RenderUtil.roundedRect(graphics, cx - size / 2, cy - size / 2, size, size, 1.5f, Theme.accent());
				RenderUtil.roundedRect(graphics, cx - size / 2 + 1, cy - size / 2 + 1, size - 2, size - 2, 1, Theme.TEXT);
			}

			// Name tag above the element (below it if there's no room above).
			String label = element.getName() + (element.getScale() != 1f ? "  " + formatScale(element.getScale()) : "");
			float tagW = this.font.width(label) + 10;
			float tagH = 13;
			float tagX = Math.clamp(b.centerX() - tagW / 2, 2, this.width - tagW - 2);
			float tagY = b.y() - tagH - 6 >= 2 ? b.y() - tagH - 6 : b.bottom() + 6;
			RenderUtil.roundedRect(graphics, tagX, tagY, tagW, tagH, 4, Theme.withAlpha(Theme.PANEL, 0.95f));
			RenderUtil.text(graphics, this.font, label, tagX + 5, tagY + 3, Theme.TEXT);
			RenderUtil.setAlpha(previousAlpha);
		}
	}

	private static void outline(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		RenderUtil.roundedRect(graphics, x, y, w, 1, 0, color);
		RenderUtil.roundedRect(graphics, x, y + h - 1, w, 1, 0, color);
		RenderUtil.roundedRect(graphics, x, y + 1, 1, h - 2, 0, color);
		RenderUtil.roundedRect(graphics, x + w - 1, y + 1, 1, h - 2, 0, color);
	}

	private void drawGuides(GuiGraphicsExtractor graphics) {
		if (dragging == null) {
			return;
		}
		int color = Theme.withAlpha(Theme.accent(), 0.9f);
		if (guideX != null) {
			RenderUtil.roundedRect(graphics, Math.clamp(guideX - 0.5f, 0, this.width - 1), 0, 1, this.height, 0, color);
		}
		if (guideY != null) {
			RenderUtil.roundedRect(graphics, 0, Math.clamp(guideY - 0.5f, 0, this.height - 1), this.width, 1, 0, color);
		}
	}

	private void drawToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		buttons.clear();
		// Fade the toolbar back while dragging so it doesn't get in the way.
		float visibility = toolbarVisibility.update(dragging != null ? 0.2f : 1f, deltaSeconds);
		float previousAlpha = RenderUtil.getAlpha();
		RenderUtil.setAlpha(previousAlpha * visibility);

		String reset = "Reset All";
		String done = "Done";
		float resetW = this.font.width(reset) + 20;
		float doneW = this.font.width(done) + 24;
		float gap = 6;
		float barW = resetW + doneW + gap + 8;
		float barX = (this.width - barW) / 2f;
		float barY = 8;

		RenderUtil.shadow(graphics, barX, barY, barW, BUTTON_HEIGHT + 8, 8, 0.8f);
		RenderUtil.roundedRect(graphics, barX, barY, barW, BUTTON_HEIGHT + 8, 8, Theme.withAlpha(Theme.PANEL, 0.95f));
		float buttonY = barY + 4;
		drawButton(graphics, "reset", reset, barX + 4, buttonY, resetW, false, mouseX, mouseY, this::resetAll);
		drawButton(graphics, "done", done, barX + 4 + resetW + gap, buttonY, doneW, true, mouseX, mouseY, this::onClose);

		float hintY = barY + BUTTON_HEIGHT + 14;
		for (String line : HINT_LINES) {
			float lineWidth = this.font.width(line);
			if (lineWidth < this.width - 16) {
				RenderUtil.text(graphics, this.font, line, (this.width - lineWidth) / 2f, hintY,
						Theme.withAlpha(Theme.TEXT, 0.7f), true);
			}
			hintY += 11;
		}
		RenderUtil.setAlpha(previousAlpha);
	}

	private void drawButton(GuiGraphicsExtractor graphics, Object key, String label, float x, float y, float w,
			boolean primary, int mouseX, int mouseY, Runnable action) {
		boolean over = dragging == null && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
		float hover = animate(key, over ? 1 : 0);
		int base = primary ? Theme.accent() : Theme.CARD;
		int lifted = primary ? Theme.lerp(Theme.accent(), Theme.TEXT, 0.15f) : Theme.CARD_HOVER;
		RenderUtil.roundedRect(graphics, x, y, w, BUTTON_HEIGHT, 6, Theme.lerp(base, lifted, hover));
		RenderUtil.text(graphics, this.font, label, x + (w - this.font.width(label)) / 2f, y + (BUTTON_HEIGHT - 8) / 2f,
				primary ? Theme.TEXT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover));
		buttons.add(new Button(x, y, w, BUTTON_HEIGHT, action));
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) {
			return true;
		}
		float mx = (float) event.x();
		float my = (float) event.y();

		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			for (Button button : buttons) {
				if (mx >= button.x() && mx < button.x() + button.w() && my >= button.y() && my < button.y() + button.h()) {
					button.action().run();
					return true;
				}
			}
		}

		HudElement element = elementAt(mx, my);
		if (element == null) {
			return false;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			// Open the mod menu straight on this element's settings; closing it returns here.
			this.minecraft.setScreenAndShow(new NovaMenuScreen(element, this));
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}

		HudElement.Bounds b = element.getBounds(this.width, this.height);
		dragging = element;
		resizing = false;
		float[][] corners = {{b.x(), b.y()}, {b.right(), b.y()}, {b.x(), b.bottom()}, {b.right(), b.bottom()}};
		for (float[] corner : corners) {
			if (Math.abs(mx - corner[0]) <= HANDLE_REACH + 2 && Math.abs(my - corner[1]) <= HANDLE_REACH + 2) {
				resizing = true;
				resizeFromLeft = corner[0] == b.x();
				resizeFromTop = corner[1] == b.y();
				anchorX = resizeFromLeft ? b.right() : b.x();
				anchorY = resizeFromTop ? b.bottom() : b.y();
				break;
			}
		}
		grabOffsetX = mx - b.x();
		grabOffsetY = my - b.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) {
			return false;
		}
		float mx = (float) event.x();
		float my = (float) event.y();
		if (resizing) {
			resize(mx, my);
		} else {
			move(mx, my);
		}
		return true;
	}

	private void move(float mx, float my) {
		float w = dragging.getWidth() * dragging.getScale();
		float h = dragging.getHeight() * dragging.getScale();
		float left = Math.clamp(mx - grabOffsetX, 0, Math.max(0, this.width - w));
		float top = Math.clamp(my - grabOffsetY, 0, Math.max(0, this.height - h));

		guideX = null;
		guideY = null;
		if (!this.minecraft.hasShiftDown()) {
			left = snap(left, w, true);
			top = snap(top, h, false);
		}
		dragging.setPosition(left, top, this.width, this.height);
	}

	/** Scales around the opposite corner, keeping proportions. */
	private void resize(float mx, float my) {
		float baseW = Math.max(1, dragging.getWidth());
		float baseH = Math.max(1, dragging.getHeight());
		float scale = Math.max(Math.abs(mx - anchorX) / baseW, Math.abs(my - anchorY) / baseH);
		dragging.setScale(scale);

		float w = baseW * dragging.getScale();
		float h = baseH * dragging.getScale();
		float left = resizeFromLeft ? anchorX - w : anchorX;
		float top = resizeFromTop ? anchorY - h : anchorY;
		dragging.setPosition(left, top, this.width, this.height);
	}

	/**
	 * Snaps a box's start, center, or end to the screen edges, screen center, or any
	 * other element's edges/center, whichever is closest within SNAP_DISTANCE.
	 * Records the line it snapped to so it can be drawn as a guide.
	 */
	private float snap(float start, float size, boolean horizontal) {
		float screenSize = horizontal ? this.width : this.height;
		List<Float> targets = new ArrayList<>(List.of(0f, screenSize / 2, screenSize));
		for (HudElement other : visibleElements()) {
			if (other == dragging) {
				continue;
			}
			HudElement.Bounds b = other.getBounds(this.width, this.height);
			if (horizontal) {
				targets.addAll(List.of(b.x(), b.centerX(), b.right()));
			} else {
				targets.addAll(List.of(b.y(), b.centerY(), b.bottom()));
			}
		}

		float[] offsets = {0, size / 2, size};
		float best = SNAP_DISTANCE;
		Float snappedStart = null;
		Float guide = null;
		for (float offset : offsets) {
			for (float target : targets) {
				float distance = Math.abs(start + offset - target);
				if (distance < best) {
					best = distance;
					snappedStart = target - offset;
					guide = target;
				}
			}
		}
		if (horizontal) {
			guideX = guide;
		} else {
			guideY = guide;
		}
		return snappedStart != null ? snappedStart : start;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			guideX = null;
			guideY = null;
			NovaClient.getConfig().save();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (closing) {
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_R && dragging == null) {
			HudElement element = elementAt(lastMouseX, lastMouseY);
			if (element != null) {
				element.resetPosition();
				NovaClient.getConfig().save();
				return true;
			}
		}
		if (NovaClient.getOpenHudEditorKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	// ---------------------------------------------------------------- helpers

	private HudElement elementAt(float x, float y) {
		List<HudElement> elements = visibleElements();
		// Last drawn is on top, so check from the end.
		for (int i = elements.size() - 1; i >= 0; i--) {
			HudElement element = elements.get(i);
			if (element.getBounds(this.width, this.height).contains(x, y, HANDLE_REACH)) {
				return element;
			}
		}
		return null;
	}

	private void resetAll() {
		for (HudElement element : NovaClient.getModules().getHudElements()) {
			element.resetPosition();
		}
		NovaClient.getConfig().save();
	}

	private float animate(Object key, float target) {
		return hoverAnims.computeIfAbsent(key, k -> new Animated(target, 14f)).update(target, deltaSeconds);
	}

	private static String formatScale(float scale) {
		return String.format("%.2fx", scale);
	}

	/** Fades the editor out before closing. */
	@Override
	public void onClose() {
		if (!closing) {
			closing = true;
			dragging = null;
			openTween.play(false);
		}
	}

	@Override
	public void removed() {
		NovaClient.getConfig().save();
	}

	@Override
	public void added() {
		// Coming back from the settings menu: fade in again.
		closing = false;
		closeScheduled = false;
		openTween.play(true);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private record Button(float x, float y, float w, float h, Runnable action) {
	}
}
