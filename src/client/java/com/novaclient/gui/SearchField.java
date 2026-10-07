package com.novaclient.gui;

import com.novaclient.gui.anim.Animated;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * A minimal search box drawn with our own helpers, so it fades and scales with the
 * rest of the menu (vanilla's EditBox can't). Supports typing, backspace,
 * Ctrl/Cmd+Backspace to clear, and paste.
 */
public class SearchField {
	private static final int MAX_LENGTH = 40;
	private static final int RADIUS = 6;
	private static final int ICON_SPACE = 22;

	private final Consumer<String> onChange;
	private final Animated focusAnim = new Animated(0, 14f);
	private final Animated hoverAnim = new Animated(0, 14f);
	private String text;
	private boolean focused;

	public SearchField(String initialText, Consumer<String> onChange) {
		this.text = initialText;
		this.onChange = onChange;
	}

	public void draw(GuiGraphicsExtractor graphics, Font font, float x, float y, float w, float h,
			boolean hovered, float deltaSeconds) {
		float focus = focusAnim.update(focused ? 1 : 0, deltaSeconds);
		float hover = hoverAnim.update(hovered ? 1 : 0, deltaSeconds);

		// A 1px ring that fades to the accent color when focused, with the fill inset inside it.
		int ring = Theme.lerp(Theme.withAlpha(Theme.TEXT, 0.08f), Theme.accent(), focus);
		RenderUtil.roundedRect(graphics, x, y, w, h, RADIUS, ring);
		int fill = Theme.lerp(Theme.CARD, Theme.CARD_HOVER, Math.max(hover * 0.6f, focus));
		RenderUtil.roundedRect(graphics, x + 1, y + 1, w - 2, h - 2, RADIUS - 1, fill);

		drawIcon(graphics, x + 11, y + h / 2f, Theme.lerp(Theme.TEXT_DIM, Theme.accent(), focus), fill);

		float textX = x + ICON_SPACE;
		float textY = y + (h - 8) / 2f;
		int available = (int) (w - ICON_SPACE - 8);
		if (text.isEmpty()) {
			RenderUtil.text(graphics, font, "Search modules...", textX, textY, Theme.withAlpha(Theme.TEXT_DIM, 0.8f));
		} else {
			// When the text is too long, show its end so the caret stays visible.
			String visible = font.plainSubstrByWidth(text, available, true);
			RenderUtil.text(graphics, font, visible, textX, textY, Theme.TEXT);
			textX += font.width(visible);
		}

		boolean caretOn = (Util.getMillis() / 530) % 2 == 0;
		if (focused && caretOn) {
			RenderUtil.roundedRect(graphics, textX + (text.isEmpty() ? 0 : 1), textY - 1, 1, 10, 0, Theme.accent());
		}
	}

	/** A magnifying glass: a ring plus a short rotated handle. */
	private static void drawIcon(GuiGraphicsExtractor graphics, float cx, float cy, int color, int background) {
		float lensX = cx - 1;
		float lensY = cy - 1;
		RenderUtil.roundedRect(graphics, lensX - 3.5f, lensY - 3.5f, 7, 7, 3.5f, color);
		RenderUtil.roundedRect(graphics, lensX - 2.5f, lensY - 2.5f, 5, 5, 2.5f, background);

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(lensX + 2.3f, lensY + 2.3f);
		pose.rotate((float) Math.toRadians(45));
		RenderUtil.roundedRect(graphics, 0, -0.6f, 3.2f, 1.2f, 0.6f, color);
		pose.popMatrix();
	}

	public boolean charTyped(CharacterEvent event) {
		if (!focused || !event.isAllowedChatCharacter() || text.length() >= MAX_LENGTH) {
			return false;
		}
		setText(text + event.codepointAsString());
		return true;
	}

	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
			if (event.hasControlDownWithQuirk() || event.hasAltDown()) {
				setText("");
			} else if (!text.isEmpty()) {
				setText(text.substring(0, text.offsetByCodePoints(text.length(), -1)));
			}
			return true;
		}
		if (event.isPaste()) {
			String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[\\r\\n]", "");
			String combined = text + clipboard;
			setText(combined.length() > MAX_LENGTH ? combined.substring(0, MAX_LENGTH) : combined);
			return true;
		}
		if (event.isEscape() || event.isConfirmation()) {
			focused = false;
			return true;
		}
		return false;
	}

	private void setText(String newText) {
		if (!newText.equals(text)) {
			text = newText;
			onChange.accept(text);
		}
	}

	public boolean isFocused() {
		return focused;
	}

	public void setFocused(boolean focused) {
		this.focused = focused;
	}
}
