package com.novaclient.gui;

import com.novaclient.NovaClient;
import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import com.novaclient.module.setting.NumberSetting;
import com.novaclient.module.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * The main Nova Client menu: category sidebar, search bar, module card grid,
 * and a settings panel that replaces the grid when a card is clicked.
 *
 * Every frame, the draw code also records "hitboxes" (clickable rectangles).
 * mouseClicked() checks those instead of re-calculating the layout.
 */
public class NovaMenuScreen extends Screen {
	private static final int SIDEBAR_WIDTH = 100;
	private static final int PADDING = 10;
	private static final int HEADER_HEIGHT = 18;
	private static final int TAB_HEIGHT = 18;
	private static final int CARD_MIN_WIDTH = 110;
	private static final int CARD_HEIGHT = 48;
	private static final int CARD_GAP = 8;
	private static final int ROW_GAP = 6;
	private static final int SCROLL_SPEED = 12;

	// The four color channels, as {bit shift, slider color}, in display order R, G, B, A.
	private static final String[] CHANNEL_LABELS = {"R", "G", "B", "A"};
	private static final int[] CHANNEL_SHIFTS = {16, 8, 0, 24};
	private static final int[] CHANNEL_COLORS = {0xFFFF5555, 0xFF55DD55, 0xFF5599FF, 0xFFCCCCCC};

	// static = shared across every NovaMenuScreen, so the menu reopens where you left it.
	private static Category selectedCategory = null; // null means "All"
	private static String searchQuery = "";

	private final ModuleManager modules = NovaClient.getModules();
	private final List<Hitbox> hitboxes = new ArrayList<>();

	private Module openModule; // non-null while the settings panel is showing
	private EditBox searchBox;
	private SliderDrag activeDrag;
	private double scroll;
	private int maxScroll;

	// Layout, recalculated in init() whenever the window is resized.
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int contentX;
	private int contentW;

	public NovaMenuScreen() {
		super(Component.literal("Nova Client"));
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - 40, 480);
		panelH = Math.min(this.height - 40, 300);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		contentX = panelX + SIDEBAR_WIDTH + PADDING;
		contentW = panelW - SIDEBAR_WIDTH - PADDING * 2;

		// EditBox is Minecraft's built-in text field. We hide its border and draw our own background.
		searchBox = new EditBox(this.font, contentX + 8, panelY + PADDING + 5, contentW - 16, this.font.lineHeight,
				Component.literal("Search"));
		searchBox.setBordered(false);
		searchBox.setMaxLength(50);
		searchBox.setTextColor(Theme.TEXT);
		searchBox.setHint(Component.literal("Search modules...").withColor(Theme.TEXT_DIM));
		searchBox.setValue(searchQuery);
		searchBox.setResponder(text -> {
			searchQuery = text;
			scroll = 0;
		});
		searchBox.visible = openModule == null;
		addRenderableWidget(searchBox);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, Theme.OVERLAY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		hitboxes.clear();
		RenderUtil.roundedRect(graphics, panelX, panelY, panelW, panelH, 6, Theme.PANEL);
		drawSidebar(graphics, mouseX, mouseY);
		if (openModule == null) {
			drawModuleGrid(graphics, mouseX, mouseY);
		} else {
			drawSettingsPanel(graphics, mouseX, mouseY);
		}
		// Draws the widgets (just the search box) on top of our panel.
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	// ---------------------------------------------------------------- sidebar

	private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		RenderUtil.roundedRect(graphics, panelX, panelY, SIDEBAR_WIDTH, panelH, 6, Theme.SIDEBAR);
		// Square off the sidebar's right edge where it meets the content area.
		graphics.fill(panelX + SIDEBAR_WIDTH - 6, panelY, panelX + SIDEBAR_WIDTH, panelY + panelH, Theme.SIDEBAR);

		int titleWidth = this.font.width("Nova Client");
		int titleX = panelX + (SIDEBAR_WIDTH - titleWidth) / 2;
		graphics.text(this.font, "Nova", titleX, panelY + 14, Theme.ACCENT, false);
		graphics.text(this.font, " Client", titleX + this.font.width("Nova"), panelY + 14, Theme.TEXT, false);

		int tabY = panelY + 40;
		drawTab(graphics, null, "All", tabY, mouseX, mouseY);
		for (Category category : Category.values()) {
			tabY += TAB_HEIGHT + 4;
			drawTab(graphics, category, category.getDisplayName(), tabY, mouseX, mouseY);
		}
	}

	private void drawTab(GuiGraphicsExtractor graphics, Category category, String label, int y, int mouseX, int mouseY) {
		int x = panelX + 8;
		int w = SIDEBAR_WIDTH - 16;
		boolean selected = category == selectedCategory && openModule == null;
		boolean hovered = isInside(mouseX, mouseY, x, y, w, TAB_HEIGHT);

		if (selected || hovered) {
			RenderUtil.roundedRect(graphics, x, y, w, TAB_HEIGHT, 4, selected ? Theme.CARD_HOVER : Theme.CARD);
		}
		if (selected) {
			graphics.fill(x, y + 4, x + 2, y + TAB_HEIGHT - 4, Theme.ACCENT);
		}
		graphics.text(this.font, label, x + 10, y + 5, selected ? Theme.TEXT : Theme.TEXT_DIM, false);

		addHitbox(x, y, w, TAB_HEIGHT, () -> {
			selectedCategory = category;
			closeSettings();
		});
	}

	// ---------------------------------------------------------------- module grid

	private void drawModuleGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int searchY = panelY + PADDING;
		RenderUtil.roundedRect(graphics, contentX, searchY, contentW, HEADER_HEIGHT, 4,
				searchBox.isFocused() ? Theme.CARD_HOVER : Theme.CARD);

		int top = searchY + HEADER_HEIGHT + PADDING;
		int bottom = panelY + panelH - PADDING;
		List<Module> results = modules.search(searchQuery, selectedCategory);
		if (results.isEmpty()) {
			maxScroll = 0;
			graphics.centeredText(this.font, "No modules found", contentX + contentW / 2, top + 20, Theme.TEXT_DIM);
			return;
		}

		int columns = Math.max(1, (contentW + CARD_GAP) / (CARD_MIN_WIDTH + CARD_GAP));
		int cardW = (contentW - CARD_GAP * (columns - 1)) / columns;
		int rows = (results.size() + columns - 1) / columns;
		updateScrollLimit(rows * (CARD_HEIGHT + CARD_GAP) - CARD_GAP, bottom - top);

		// Scissor = only draw inside this rectangle, so scrolled cards get clipped at the edges.
		graphics.enableScissor(contentX, top, contentX + contentW, bottom);
		for (int i = 0; i < results.size(); i++) {
			int x = contentX + (i % columns) * (cardW + CARD_GAP);
			int y = top + (i / columns) * (CARD_HEIGHT + CARD_GAP) - (int) scroll;
			drawCard(graphics, results.get(i), x, y, cardW, mouseX, mouseY, top, bottom);
		}
		graphics.disableScissor();
	}

	private void drawCard(GuiGraphicsExtractor graphics, Module module, int x, int y, int w,
			int mouseX, int mouseY, int clipTop, int clipBottom) {
		boolean hovered = mouseY >= clipTop && mouseY < clipBottom && isInside(mouseX, mouseY, x, y, w, CARD_HEIGHT);
		RenderUtil.roundedRect(graphics, x, y, w, CARD_HEIGHT, 5, hovered ? Theme.CARD_HOVER : Theme.CARD);

		graphics.text(this.font, ellipsize(module.getName(), w - 16), x + 8, y + 8, Theme.TEXT, false);
		graphics.text(this.font, ellipsize(module.getDescription(), w - 16), x + 8, y + 20, Theme.TEXT_DIM, false);

		int toggleX = x + w - 8 - RenderUtil.TOGGLE_WIDTH;
		int toggleY = y + CARD_HEIGHT - 8 - RenderUtil.TOGGLE_HEIGHT;
		graphics.text(this.font, module.getCategory().getDisplayName(), x + 8, toggleY + 1, Theme.ACCENT, false);
		RenderUtil.toggleSwitch(graphics, toggleX, toggleY, module.isEnabled());

		// The toggle is added after the card, so it wins when both contain the click.
		addClippedHitbox(x, y, w, CARD_HEIGHT, clipTop, clipBottom, () -> openSettings(module));
		addClippedHitbox(toggleX - 2, toggleY - 2, RenderUtil.TOGGLE_WIDTH + 4, RenderUtil.TOGGLE_HEIGHT + 4,
				clipTop, clipBottom, module::toggle);
	}

	// ---------------------------------------------------------------- settings panel

	private void drawSettingsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Module module = openModule;
		int headerY = panelY + PADDING;

		String back = "< Back";
		int backW = this.font.width(back) + 12;
		boolean backHovered = isInside(mouseX, mouseY, contentX, headerY, backW, HEADER_HEIGHT);
		RenderUtil.roundedRect(graphics, contentX, headerY, backW, HEADER_HEIGHT, 4, backHovered ? Theme.CARD_HOVER : Theme.CARD);
		graphics.text(this.font, back, contentX + 6, headerY + 5, Theme.TEXT, false);
		addHitbox(contentX, headerY, backW, HEADER_HEIGHT, this::closeSettings);

		graphics.text(this.font, module.getName(), contentX + backW + 10, headerY + 5, Theme.TEXT, false);

		int toggleX = contentX + contentW - RenderUtil.TOGGLE_WIDTH;
		int toggleY = headerY + (HEADER_HEIGHT - RenderUtil.TOGGLE_HEIGHT) / 2;
		RenderUtil.toggleSwitch(graphics, toggleX, toggleY, module.isEnabled());
		addHitbox(toggleX - 2, toggleY - 2, RenderUtil.TOGGLE_WIDTH + 4, RenderUtil.TOGGLE_HEIGHT + 4, module::toggle);

		graphics.text(this.font, ellipsize(module.getDescription(), contentW), contentX, headerY + HEADER_HEIGHT + 6,
				Theme.TEXT_DIM, false);

		int top = headerY + HEADER_HEIGHT + 22;
		int bottom = panelY + panelH - PADDING;
		List<Setting<?>> settings = module.getSettings();
		if (settings.isEmpty()) {
			maxScroll = 0;
			graphics.text(this.font, "This module has no settings.", contentX, top, Theme.TEXT_DIM, false);
			return;
		}

		int totalHeight = -ROW_GAP;
		for (Setting<?> setting : settings) {
			totalHeight += rowHeight(setting) + ROW_GAP;
		}
		updateScrollLimit(totalHeight, bottom - top);

		graphics.enableScissor(contentX, top, contentX + contentW, bottom);
		int y = top - (int) scroll;
		for (Setting<?> setting : settings) {
			int h = rowHeight(setting);
			RenderUtil.roundedRect(graphics, contentX, y, contentW, h, 5, Theme.CARD);
			graphics.text(this.font, setting.getName(), contentX + 8, y + 8, Theme.TEXT, false);

			// "instanceof X x" checks the type and gives you a variable of that type in one step.
			if (setting instanceof BooleanSetting bool) {
				drawBooleanRow(graphics, bool, y, h, top, bottom);
			} else if (setting instanceof NumberSetting number) {
				drawNumberRow(graphics, number, y, top, bottom);
			} else if (setting instanceof ColorSetting color) {
				drawColorRow(graphics, color, y, top, bottom);
			}
			y += h + ROW_GAP;
		}
		graphics.disableScissor();
	}

	private static int rowHeight(Setting<?> setting) {
		if (setting instanceof NumberSetting) {
			return 34;
		}
		if (setting instanceof ColorSetting) {
			return 24 + CHANNEL_SHIFTS.length * 14 + 4;
		}
		return 24;
	}

	private void drawBooleanRow(GuiGraphicsExtractor graphics, BooleanSetting setting, int y, int h, int clipTop, int clipBottom) {
		int toggleX = contentX + contentW - 8 - RenderUtil.TOGGLE_WIDTH;
		RenderUtil.toggleSwitch(graphics, toggleX, y + (h - RenderUtil.TOGGLE_HEIGHT) / 2, setting.get());
		addClippedHitbox(contentX, y, contentW, h, clipTop, clipBottom, setting::toggle);
	}

	private void drawNumberRow(GuiGraphicsExtractor graphics, NumberSetting setting, int y, int clipTop, int clipBottom) {
		String value = setting.getDisplayValue();
		graphics.text(this.font, value, contentX + contentW - 8 - this.font.width(value), y + 8, Theme.TEXT_DIM, false);

		int trackX = contentX + 8;
		int trackY = y + 22;
		int trackW = contentW - 16;
		RenderUtil.slider(graphics, trackX, trackY, trackW, setting.getProgress(), Theme.ACCENT);
		addSliderHitbox(trackX, trackY, trackW, clipTop, clipBottom, setting::setFromProgress);
	}

	private void drawColorRow(GuiGraphicsExtractor graphics, ColorSetting setting, int y, int clipTop, int clipBottom) {
		int swatchX = contentX + contentW - 8 - 20;
		graphics.outline(swatchX - 1, y + 6, 22, 12, Theme.TOGGLE_OFF);
		graphics.fill(swatchX, y + 7, swatchX + 20, y + 17, setting.get());
		String hex = setting.toHex();
		graphics.text(this.font, hex, swatchX - 6 - this.font.width(hex), y + 8, Theme.TEXT_DIM, false);

		int valueWidth = this.font.width("255");
		for (int i = 0; i < CHANNEL_SHIFTS.length; i++) {
			int shift = CHANNEL_SHIFTS[i];
			int rowY = y + 24 + i * 14;
			int channel = setting.getChannel(shift);
			graphics.text(this.font, CHANNEL_LABELS[i], contentX + 8, rowY, Theme.TEXT_DIM, false);

			int trackX = contentX + 22;
			int trackW = contentW - 22 - 14 - valueWidth;
			RenderUtil.slider(graphics, trackX, rowY + 2, trackW, channel / 255.0, CHANNEL_COLORS[i]);
			String channelText = String.valueOf(channel);
			graphics.text(this.font, channelText, contentX + contentW - 8 - this.font.width(channelText), rowY,
					Theme.TEXT_DIM, false);

			addSliderHitbox(trackX, rowY + 2, trackW, clipTop, clipBottom,
					progress -> setting.setChannel(shift, (int) Math.round(progress * 255)));
		}
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Let the search box handle clicks on itself first.
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		setFocused(null);
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		// Check the last-drawn hitbox first, since it's on top.
		for (int i = hitboxes.size() - 1; i >= 0; i--) {
			Hitbox hitbox = hitboxes.get(i);
			if (hitbox.contains(event.x(), event.y())) {
				hitbox.onClick().accept(event.x());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (activeDrag != null) {
			activeDrag.update(event.x());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		activeDrag = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.clamp(scroll - scrollY * SCROLL_SPEED, 0, maxScroll);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (openModule != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
			closeSettings();
			return true;
		}
		// Pressing the menu key again closes the menu, unless you're typing in the search box.
		if (!searchBox.isFocused() && NovaClient.getOpenMenuKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void removed() {
		// Called whenever this screen closes, however it was closed.
		NovaClient.getConfig().save();
	}

	@Override
	public boolean isPauseScreen() {
		// Keep the game running behind the menu, like a client overlay.
		return false;
	}

	// ---------------------------------------------------------------- helpers

	private void openSettings(Module module) {
		openModule = module;
		scroll = 0;
		activeDrag = null;
		searchBox.visible = false;
		setFocused(null);
	}

	private void closeSettings() {
		openModule = null;
		scroll = 0;
		activeDrag = null;
		searchBox.visible = true;
	}

	private void updateScrollLimit(int contentHeight, int viewHeight) {
		maxScroll = Math.max(0, contentHeight - viewHeight);
		scroll = Math.clamp(scroll, 0, maxScroll);
	}

	private String ellipsize(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("...")) + "...";
	}

	private static boolean isInside(double px, double py, int x, int y, int w, int h) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	private void addHitbox(int x, int y, int w, int h, Runnable action) {
		hitboxes.add(new Hitbox(x, y, w, h, mouseX -> action.run()));
	}

	/** Like addHitbox, but trimmed to the scrolling area so hidden content can't be clicked. */
	private void addClippedHitbox(int x, int y, int w, int h, int clipTop, int clipBottom, Runnable action) {
		int top = Math.max(y, clipTop);
		int bottom = Math.min(y + h, clipBottom);
		if (bottom > top) {
			hitboxes.add(new Hitbox(x, top, w, bottom - top, mouseX -> action.run()));
		}
	}

	/** A slider hitbox: clicking jumps to that spot, and dragging keeps updating until release. */
	private void addSliderHitbox(int trackX, int trackY, int trackW, int clipTop, int clipBottom, DoubleConsumer setProgress) {
		int top = Math.max(trackY - 4, clipTop);
		int bottom = Math.min(trackY + 8, clipBottom);
		if (bottom > top) {
			hitboxes.add(new Hitbox(trackX - 3, top, trackW + 6, bottom - top, mouseX -> {
				activeDrag = new SliderDrag(trackX, trackW, setProgress);
				activeDrag.update(mouseX);
			}));
		}
	}

	/** A record is a small immutable data class, like a TypeScript readonly object type. */
	private record Hitbox(int x, int y, int w, int h, DoubleConsumer onClick) {
		boolean contains(double px, double py) {
			return isInside(px, py, x, y, w, h);
		}
	}

	private record SliderDrag(int trackX, int trackW, DoubleConsumer setProgress) {
		void update(double mouseX) {
			setProgress.accept((mouseX - trackX) / trackW);
		}
	}
}
