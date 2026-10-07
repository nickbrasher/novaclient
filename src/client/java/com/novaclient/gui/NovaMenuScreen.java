package com.novaclient.gui;

import com.novaclient.NovaClient;
import com.novaclient.gui.anim.Animated;
import com.novaclient.gui.anim.SmoothScroller;
import com.novaclient.gui.anim.Tween;
import com.novaclient.hud.HudEditorScreen;
import com.novaclient.hud.HudElement;
import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.ColorSetting;
import com.novaclient.module.setting.ModeSetting;
import com.novaclient.module.setting.NumberSetting;
import com.novaclient.module.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The main Nova Client menu: category sidebar, search bar, module card grid,
 * and a settings page that slides in when a card is clicked.
 *
 * How the animation works: every frame we measure how much time passed (deltaSeconds)
 * and nudge each animation forward by that much. Drawing then reads the current
 * animation values, so motion is smooth at any frame rate.
 *
 * How clicks work: while drawing, we record "hitboxes" (clickable rectangles).
 * mouseClicked() checks those instead of re-calculating the layout.
 */
public class NovaMenuScreen extends Screen {
	// Layout, in GUI pixels.
	private static final int MAX_PANEL_WIDTH = 500;
	private static final int MAX_PANEL_HEIGHT = 310;
	private static final int SIDEBAR_WIDTH = 112;
	private static final int PADDING = 12;
	private static final int GAP = 8;
	private static final int PANEL_RADIUS = 10;
	private static final int CARD_RADIUS = 7;
	private static final int CONTROL_RADIUS = 6;
	private static final int HEADER_HEIGHT = 22;
	private static final int TAB_HEIGHT = 22;
	private static final int TAB_GAP = 4;
	private static final int TABS_TOP = 44;
	private static final int CARD_MIN_WIDTH = 124;
	private static final int CARD_HEIGHT = 56;
	private static final int TEXT_HEIGHT = 8;
	private static final int LIST_INSET = 4; // room for hover lift and shadows inside scroll areas

	// Animation timing.
	private static final float OPEN_SECONDS = 0.2f;
	private static final float PAGE_SECONDS = 0.25f;
	private static final float GRID_FADE_SECONDS = 0.18f;
	private static final float HOVER_SPEED = 14f;
	private static final float TOGGLE_SPEED = 14f;
	private static final float INDICATOR_SPEED = 18f;
	private static final float SCROLLBAR_SPEED = 8f;

	// The four color channels in display order R, G, B, A.
	private static final String[] CHANNEL_LABELS = {"R", "G", "B", "A"};
	private static final int[] CHANNEL_SHIFTS = {16, 8, 0, 24};
	private static final int[] CHANNEL_COLORS = {0xFFFF5C5C, 0xFF5CD65C, 0xFF5C9DFF, 0xFFCCCCCC};

	// Keys for animations that don't belong to a module or setting.
	private static final Object ALL_TAB = new Object();
	private static final Object SETTINGS_TAB = new Object();
	private static final Object BACK_BUTTON = new Object();
	private static final Object EDIT_HUD_BUTTON = new Object();
	private static final Object RESET_POSITION_ROW = new Object();

	// static = shared across every NovaMenuScreen, so the menu reopens where you left it.
	private static Category selectedCategory = null; // null means "All"
	private static String searchQuery = "";

	private final ModuleManager modules = NovaClient.getModules();
	private final List<Hitbox> hitboxes = new ArrayList<>();
	private final Map<Object, Animated> hoverAnims = new HashMap<>();
	private final Map<Object, Animated> toggleAnims = new HashMap<>();

	private final Tween openTween = new Tween(OPEN_SECONDS, 0f, true);
	private final Tween pageTween; // 0 = grid, 1 = settings page
	private final Tween gridFade = new Tween(GRID_FADE_SECONDS, 1f, true);
	private final Animated tabIndicator = new Animated(0, INDICATOR_SPEED);
	private final SmoothScroller gridScroll = new SmoothScroller();
	private final SmoothScroller settingsScroll = new SmoothScroller();
	private final SearchField search = new SearchField(searchQuery, text -> {
		searchQuery = text;
		gridScroll.reset();
	});

	private Module pageModule; // the module on the settings page (kept while it slides out)
	private final Screen returnTo; // screen to go back to when closing (e.g. the HUD editor), or null
	private Screen afterClose; // screen to show once the close animation finishes, or null for the game
	private SliderDrag activeDrag;
	private boolean closing;
	private boolean closeScheduled;
	private boolean indicatorPlaced;
	private long lastFrameNanos;
	private float deltaSeconds;

	// Layout, recalculated in init() whenever the window is resized.
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int contentX;
	private int contentY;
	private int contentW;

	// Per-frame state used while drawing.
	private float panelScale = 1f;
	private float panelMouseX;
	private float panelMouseY;
	private float hitOffsetX;
	private float hitOffsetY;
	private boolean interactive;

	public NovaMenuScreen() {
		this(null, null);
	}

	/**
	 * Opens straight onto a module's settings page. Closing (Esc or Back) returns to
	 * returnTo instead of the game. Used by the HUD editor's right-click.
	 */
	public NovaMenuScreen(Module startPage, Screen returnTo) {
		super(Component.literal("Nova Client"));
		this.returnTo = returnTo;
		this.afterClose = returnTo;
		this.pageModule = startPage;
		this.pageTween = new Tween(PAGE_SECONDS, startPage != null ? 1f : 0f, startPage != null);
	}

	@Override
	protected void init() {
		panelW = Math.min(this.width - 32, MAX_PANEL_WIDTH);
		panelH = Math.min(this.height - 32, MAX_PANEL_HEIGHT);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;
		contentX = panelX + SIDEBAR_WIDTH + PADDING;
		contentY = panelY + PADDING;
		contentW = panelW - SIDEBAR_WIDTH - PADDING * 2;
	}

	// ---------------------------------------------------------------- frame

	/** Background runs first each frame, so this is where the clock and timed animations advance. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = Util.getNanos();
		deltaSeconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.1f);
		lastFrameNanos = now;
		openTween.update(deltaSeconds);
		pageTween.update(deltaSeconds);
		gridFade.update(deltaSeconds);

		if (closing && openTween.isFinished() && !closeScheduled) {
			// Close on the next game loop iteration, not in the middle of drawing.
			closeScheduled = true;
			this.minecraft.schedule(this::finishClose);
		}

		// On the title screen there's no world behind us, so show the menu panorama instead.
		if (this.minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		// Uses the player's Menu Background Blur setting (Options > Accessibility).
		extractBlurredBackground(graphics);
		graphics.fill(0, 0, this.width, this.height, Theme.withAlpha(Theme.TINT, openTween.eased()));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		hitboxes.clear();
		float open = openTween.eased();
		panelScale = 0.9f + 0.1f * open;
		panelMouseX = toPanelX(mouseX);
		panelMouseY = toPanelY(mouseY);
		interactive = !closing;
		hitOffsetX = 0;
		hitOffsetY = 0;

		// Scale the whole panel around its center for the open/close animation.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		if (panelScale != 1f) {
			float centerX = panelX + panelW / 2f;
			float centerY = panelY + panelH / 2f;
			pose.translate(centerX, centerY);
			pose.scale(panelScale, panelScale);
			pose.translate(-centerX, -centerY);
		}
		RenderUtil.setAlpha(open);

		drawPanel(graphics);
		drawSidebar(graphics);
		drawContent(graphics);

		RenderUtil.setAlpha(1f);
		pose.popMatrix();
	}

	private void drawPanel(GuiGraphicsExtractor graphics) {
		for (int i = 1; i <= 8; i++) {
			float spread = i * 1.5f;
			RenderUtil.roundedRect(graphics, panelX - spread, panelY - spread + 4, panelW + spread * 2, panelH + spread * 2,
					PANEL_RADIUS + spread, Theme.withAlpha(Theme.SHADOW, 0.05f));
		}
		RenderUtil.roundedRect(graphics, panelX - 1, panelY - 1, panelW + 2, panelH + 2, PANEL_RADIUS + 1, Theme.BORDER);
		RenderUtil.roundedRect(graphics, panelX + SIDEBAR_WIDTH, panelY, panelW - SIDEBAR_WIDTH, panelH, PANEL_RADIUS,
				Theme.PANEL, RenderUtil.TOP_RIGHT | RenderUtil.BOTTOM_RIGHT);
	}

	// ---------------------------------------------------------------- sidebar

	private void drawSidebar(GuiGraphicsExtractor graphics) {
		RenderUtil.roundedRect(graphics, panelX, panelY, SIDEBAR_WIDTH, panelH, PANEL_RADIUS, Theme.SIDEBAR,
				RenderUtil.TOP_LEFT | RenderUtil.BOTTOM_LEFT);
		RenderUtil.roundedRect(graphics, panelX + SIDEBAR_WIDTH, panelY, 1, panelH, 0, Theme.DIVIDER);

		int titleX = panelX + (SIDEBAR_WIDTH - this.font.width("Nova Client")) / 2;
		RenderUtil.text(graphics, this.font, "Nova", titleX, panelY + PADDING + 6, Theme.accent());
		RenderUtil.text(graphics, this.font, " Client", titleX + this.font.width("Nova"), panelY + PADDING + 6, Theme.TEXT);

		// The highlight pill glides to whichever tab is selected.
		Object selectedKey = selectedTabKey();
		float targetY = tabY(selectedKey);
		if (!indicatorPlaced) {
			tabIndicator.snap(targetY);
			indicatorPlaced = true;
		}
		float indicatorY = tabIndicator.update(targetY, deltaSeconds);
		float tabX = panelX + 8;
		float tabW = SIDEBAR_WIDTH - 16;
		RenderUtil.roundedRect(graphics, tabX, indicatorY, tabW, TAB_HEIGHT, CONTROL_RADIUS, Theme.TAB_SELECTED);
		RenderUtil.roundedRect(graphics, tabX + 5, indicatorY + 6, 3, TAB_HEIGHT - 12, 1.5f, Theme.accent());

		drawTab(graphics, ALL_TAB, "All", selectedKey, () -> selectCategory(null));
		for (Category category : Category.values()) {
			drawTab(graphics, category, category.getDisplayName(), selectedKey, () -> selectCategory(category));
		}
		drawTab(graphics, SETTINGS_TAB, "Settings", selectedKey, () -> openPage(modules.getMenuSettings()));
	}

	private void drawTab(GuiGraphicsExtractor graphics, Object key, String label, Object selectedKey, Runnable onClick) {
		float x = panelX + 8;
		float y = tabY(key);
		float w = SIDEBAR_WIDTH - 16;
		boolean selected = key == selectedKey;
		float hover = animate(hoverAnims, key, hovered(x, y, w, TAB_HEIGHT) && !selected ? 1 : 0, HOVER_SPEED);

		RenderUtil.roundedRect(graphics, x, y, w, TAB_HEIGHT, CONTROL_RADIUS, Theme.withAlpha(Theme.TAB_SELECTED, hover * 0.6f));
		int color = selected ? Theme.TEXT : Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover);
		RenderUtil.text(graphics, this.font, label, x + 15 + hover * 2, y + (TAB_HEIGHT - TEXT_HEIGHT) / 2f, color);
		addHitbox(x, y, w, TAB_HEIGHT, onClick);
	}

	private Object selectedTabKey() {
		if (pageTween.isForward() && pageModule == modules.getMenuSettings()) {
			return SETTINGS_TAB;
		}
		return selectedCategory == null ? ALL_TAB : selectedCategory;
	}

	private float tabY(Object key) {
		if (key == SETTINGS_TAB) {
			return panelY + panelH - PADDING - TAB_HEIGHT;
		}
		int index = key == ALL_TAB ? 0 : ((Category) key).ordinal() + 1;
		return panelY + TABS_TOP + index * (TAB_HEIGHT + TAB_GAP);
	}

	// ---------------------------------------------------------------- content pages

	private void drawContent(GuiGraphicsExtractor graphics) {
		float page = pageTween.eased();
		graphics.enableScissor(panelX + SIDEBAR_WIDTH + 1, panelY, panelX + panelW, panelY + panelH);
		if (page < 0.99f) {
			// The grid drifts left and fades out quickly so it doesn't clutter the incoming page.
			drawPage(graphics, -page * 40, Math.max(0, 1 - page * 1.8f), !pageTween.isForward(), this::drawGridPage);
		}
		if (page > 0.01f && pageModule != null) {
			// The settings page slides in from the right edge of the panel.
			drawPage(graphics, (1 - page) * (contentW + PADDING), page, pageTween.isForward(), this::drawSettingsPage);
		}
		graphics.disableScissor();
	}

	private void drawPage(GuiGraphicsExtractor graphics, float offsetX, float alpha, boolean active,
			Consumer<GuiGraphicsExtractor> drawer) {
		float baseAlpha = RenderUtil.getAlpha();
		RenderUtil.setAlpha(baseAlpha * alpha);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(offsetX, 0);
		hitOffsetX = offsetX;
		interactive = active && !closing;

		drawer.accept(graphics);

		interactive = !closing;
		hitOffsetX = 0;
		pose.popMatrix();
		RenderUtil.setAlpha(baseAlpha);
	}

	// ---------------------------------------------------------------- module grid

	private void drawGridPage(GuiGraphicsExtractor graphics) {
		String editLabel = "Edit HUD";
		float editW = this.font.width(editLabel) + 22;
		float searchW = contentW - editW - GAP;
		boolean searchHovered = hovered(contentX, contentY, searchW, HEADER_HEIGHT);
		search.draw(graphics, this.font, contentX, contentY, searchW, HEADER_HEIGHT, searchHovered, deltaSeconds);
		addHitbox(contentX, contentY, searchW, HEADER_HEIGHT, () -> search.setFocused(true));

		float editX = contentX + contentW - editW;
		float editHover = animate(hoverAnims, EDIT_HUD_BUTTON, hovered(editX, contentY, editW, HEADER_HEIGHT) ? 1 : 0, HOVER_SPEED);
		RenderUtil.roundedRect(graphics, editX, contentY, editW, HEADER_HEIGHT, CONTROL_RADIUS,
				Theme.lerp(Theme.accent(), Theme.TEXT, editHover * 0.15f));
		RenderUtil.text(graphics, this.font, editLabel, editX + (editW - this.font.width(editLabel)) / 2f,
				contentY + (HEADER_HEIGHT - TEXT_HEIGHT) / 2f, Theme.TEXT);
		addHitbox(editX, contentY, editW, HEADER_HEIGHT, () -> closeTo(new HudEditorScreen()));

		int top = contentY + HEADER_HEIGHT + GAP;
		int bottom = panelY + panelH - PADDING;

		// Switching categories fades the grid in with a slight upward drift.
		float fade = gridFade.eased();
		float rise = (1 - fade) * 8;
		float baseAlpha = RenderUtil.getAlpha();
		RenderUtil.setAlpha(baseAlpha * fade);

		List<Module> results = modules.search(searchQuery, selectedCategory);
		if (results.isEmpty()) {
			gridScroll.update(deltaSeconds, 0);
			String message = "No modules found";
			RenderUtil.text(graphics, this.font, message, contentX + (contentW - this.font.width(message)) / 2f,
					top + 24 + rise, Theme.TEXT_DIM);
			RenderUtil.setAlpha(baseAlpha);
			return;
		}

		int columns = Math.max(1, (contentW + GAP) / (CARD_MIN_WIDTH + GAP));
		float cardW = (contentW - GAP * (columns - 1f)) / columns;
		int rows = (results.size() + columns - 1) / columns;
		float listHeight = LIST_INSET * 2 + rows * (CARD_HEIGHT + GAP) - GAP;
		gridScroll.update(deltaSeconds, listHeight - (bottom - top));

		// Scissor = only draw inside this rectangle, so scrolled cards get clipped at the edges.
		graphics.enableScissor(contentX - 6, top, contentX + contentW + 6, bottom);
		for (int i = 0; i < results.size(); i++) {
			float x = contentX + (i % columns) * (cardW + GAP);
			float y = top + LIST_INSET + (i / columns) * (CARD_HEIGHT + GAP) - gridScroll.get() + rise;
			if (y + CARD_HEIGHT > top - 8 && y < bottom + 8) {
				drawCard(graphics, results.get(i), x, y, cardW, top, bottom);
			}
		}
		graphics.disableScissor();
		drawScrollbar(graphics, gridScroll, top, bottom - top, listHeight);
		RenderUtil.setAlpha(baseAlpha);
	}

	private void drawCard(GuiGraphicsExtractor graphics, Module module, float x, float y, float w, int clipTop, int clipBottom) {
		boolean hovered = hovered(x, y, w, CARD_HEIGHT) && insideClip(clipTop, clipBottom);
		float hover = animate(hoverAnims, module, hovered ? 1 : 0, HOVER_SPEED);
		float on = animate(toggleAnims, module, module.isEnabled() ? 1 : 0, TOGGLE_SPEED);

		// Hovered cards lift 2px, cast a stronger shadow, and brighten.
		float cardY = y - hover * 2;
		RenderUtil.shadow(graphics, x, cardY, w, CARD_HEIGHT, CARD_RADIUS, 0.35f + hover * 0.65f);
		RenderUtil.roundedRect(graphics, x - 1, cardY - 1, w + 2, CARD_HEIGHT + 2, CARD_RADIUS + 1,
				Theme.withAlpha(Theme.accent(), on * 0.35f));
		RenderUtil.roundedRect(graphics, x, cardY, w, CARD_HEIGHT, CARD_RADIUS, Theme.lerp(Theme.CARD, Theme.CARD_HOVER, hover));

		int textWidth = (int) w - 20;
		RenderUtil.text(graphics, this.font, RenderUtil.ellipsize(this.font, module.getName(), textWidth),
				x + 10, cardY + 10, Theme.TEXT);
		RenderUtil.text(graphics, this.font, RenderUtil.ellipsize(this.font, module.getDescription(), textWidth),
				x + 10, cardY + 22, Theme.TEXT_DIM);

		float toggleX = x + w - 10 - RenderUtil.TOGGLE_WIDTH;
		float toggleY = cardY + CARD_HEIGHT - 10 - RenderUtil.TOGGLE_HEIGHT;
		RenderUtil.text(graphics, this.font, module.getCategory().getDisplayName(), x + 10, toggleY + 2,
				Theme.withAlpha(Theme.accent(), 0.85f));
		RenderUtil.toggleSwitch(graphics, toggleX, toggleY, on);

		// The toggle is added after the card, so it wins when both contain the click.
		addClippedHitbox(x, y, w, CARD_HEIGHT, clipTop, clipBottom, () -> openPage(module));
		addClippedHitbox(toggleX - 3, toggleY - 3, RenderUtil.TOGGLE_WIDTH + 6, RenderUtil.TOGGLE_HEIGHT + 6,
				clipTop, clipBottom, module::toggle);
	}

	// ---------------------------------------------------------------- settings page

	private void drawSettingsPage(GuiGraphicsExtractor graphics) {
		Module module = pageModule;

		String back = "< Back";
		float backW = this.font.width(back) + 20;
		float backHover = animate(hoverAnims, BACK_BUTTON, hovered(contentX, contentY, backW, HEADER_HEIGHT) ? 1 : 0, HOVER_SPEED);
		RenderUtil.roundedRect(graphics, contentX, contentY, backW, HEADER_HEIGHT, CONTROL_RADIUS,
				Theme.lerp(Theme.CARD, Theme.CARD_HOVER, backHover));
		RenderUtil.text(graphics, this.font, back, contentX + 10, contentY + (HEADER_HEIGHT - TEXT_HEIGHT) / 2f,
				Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, backHover));
		addHitbox(contentX, contentY, backW, HEADER_HEIGHT, this::closePage);

		RenderUtil.text(graphics, this.font, module.getName(), contentX + backW + 10,
				contentY + (HEADER_HEIGHT - TEXT_HEIGHT) / 2f, Theme.TEXT);
		if (module.isToggleable()) {
			float on = animate(toggleAnims, module, module.isEnabled() ? 1 : 0, TOGGLE_SPEED);
			float toggleX = contentX + contentW - RenderUtil.TOGGLE_WIDTH;
			float toggleY = contentY + (HEADER_HEIGHT - RenderUtil.TOGGLE_HEIGHT) / 2f;
			RenderUtil.toggleSwitch(graphics, toggleX, toggleY, on);
			addHitbox(toggleX - 3, toggleY - 3, RenderUtil.TOGGLE_WIDTH + 6, RenderUtil.TOGGLE_HEIGHT + 6, module::toggle);
		}
		RenderUtil.text(graphics, this.font, RenderUtil.ellipsize(this.font, module.getDescription(), contentW),
				contentX, contentY + HEADER_HEIGHT + GAP, Theme.TEXT_DIM);

		int top = contentY + HEADER_HEIGHT + GAP + TEXT_HEIGHT + GAP + 2;
		int bottom = panelY + panelH - PADDING;
		List<Setting<?>> settings = module.getSettings();
		boolean hasPositionRow = module instanceof HudElement;
		if (settings.isEmpty() && !hasPositionRow) {
			settingsScroll.update(deltaSeconds, 0);
			RenderUtil.text(graphics, this.font, "This module has no settings.", contentX, top + LIST_INSET, Theme.TEXT_DIM);
			return;
		}

		float listHeight = LIST_INSET * 2 - GAP;
		for (Setting<?> setting : settings) {
			listHeight += rowHeight(setting) + GAP;
		}
		if (hasPositionRow) {
			listHeight += POSITION_ROW_HEIGHT + GAP;
		}
		settingsScroll.update(deltaSeconds, listHeight - (bottom - top));

		graphics.enableScissor(contentX - 6, top, contentX + contentW + 6, bottom);
		float y = top + LIST_INSET - settingsScroll.get();
		for (Setting<?> setting : settings) {
			int h = rowHeight(setting);
			if (y + h > top - 8 && y < bottom + 8) {
				drawSettingRow(graphics, setting, y, h, top, bottom);
			}
			y += h + GAP;
		}
		if (module instanceof HudElement element && y + POSITION_ROW_HEIGHT > top - 8 && y < bottom + 8) {
			drawPositionRow(graphics, element, y, top, bottom);
		}
		graphics.disableScissor();
		drawScrollbar(graphics, settingsScroll, top, bottom - top, listHeight);
	}

	private static final int POSITION_ROW_HEIGHT = 28;

	/** For HUD elements: shows the current scale and a button to put the element back where it started. */
	private void drawPositionRow(GuiGraphicsExtractor graphics, HudElement element, float y, int clipTop, int clipBottom) {
		RenderUtil.roundedRect(graphics, contentX, y, contentW, POSITION_ROW_HEIGHT, CARD_RADIUS, Theme.CARD);
		RenderUtil.text(graphics, this.font, "Position", contentX + 10, y + 10, Theme.TEXT);
		String scale = String.format("Scale %.2fx", element.getScale());
		RenderUtil.text(graphics, this.font, scale, contentX + 10 + this.font.width("Position") + 8, y + 10, Theme.TEXT_DIM);

		String label = "Reset Position";
		float buttonW = this.font.width(label) + 16;
		float buttonH = 18;
		float buttonX = contentX + contentW - 5 - buttonW;
		float buttonY = y + (POSITION_ROW_HEIGHT - buttonH) / 2f;
		boolean over = hovered(buttonX, buttonY, buttonW, buttonH) && insideClip(clipTop, clipBottom);
		float hover = animate(hoverAnims, RESET_POSITION_ROW, over ? 1 : 0, HOVER_SPEED);
		RenderUtil.roundedRect(graphics, buttonX, buttonY, buttonW, buttonH, CONTROL_RADIUS,
				Theme.lerp(Theme.CARD_HOVER, Theme.withAlpha(Theme.accent(), 0.9f), hover));
		RenderUtil.text(graphics, this.font, label, buttonX + 8, buttonY + 5, Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, hover));
		addClippedHitbox(buttonX, buttonY, buttonW, buttonH, clipTop, clipBottom, () -> {
			element.resetPosition();
			NovaClient.getConfig().save();
		});
	}

	private static int rowHeight(Setting<?> setting) {
		if (setting instanceof NumberSetting) {
			return 40;
		}
		if (setting instanceof ColorSetting) {
			return 30 + CHANNEL_SHIFTS.length * 16;
		}
		return 28;
	}

	private void drawSettingRow(GuiGraphicsExtractor graphics, Setting<?> setting, float y, int h, int clipTop, int clipBottom) {
		boolean hovered = hovered(contentX, y, contentW, h) && insideClip(clipTop, clipBottom);
		float hover = animate(hoverAnims, setting, hovered ? 1 : 0, HOVER_SPEED);
		RenderUtil.roundedRect(graphics, contentX, y, contentW, h, CARD_RADIUS, Theme.lerp(Theme.CARD, Theme.CARD_HOVER, hover * 0.6f));
		RenderUtil.text(graphics, this.font, setting.getName(), contentX + 10, y + 10, Theme.TEXT);

		// "instanceof X x" checks the type and gives you a variable of that type in one step.
		if (setting instanceof BooleanSetting bool) {
			float on = animate(toggleAnims, bool, bool.get() ? 1 : 0, TOGGLE_SPEED);
			RenderUtil.toggleSwitch(graphics, contentX + contentW - 10 - RenderUtil.TOGGLE_WIDTH,
					y + (h - RenderUtil.TOGGLE_HEIGHT) / 2f, on);
			addClippedHitbox(contentX, y, contentW, h, clipTop, clipBottom, bool::toggle);
		} else if (setting instanceof NumberSetting number) {
			drawNumberRow(graphics, number, y, clipTop, clipBottom);
		} else if (setting instanceof ColorSetting color) {
			drawColorRow(graphics, color, y, clipTop, clipBottom);
		} else if (setting instanceof ModeSetting mode) {
			drawModeRow(graphics, mode, y, h, clipTop, clipBottom);
		}
	}

	/** A segmented control: one pill per option, with a highlight that slides to the selected one. */
	private void drawModeRow(GuiGraphicsExtractor graphics, ModeSetting setting, float y, int h, int clipTop, int clipBottom) {
		List<String> modes = setting.getModes();
		float segmentH = 16;
		float[] widths = new float[modes.size()];
		float totalW = 4;
		for (int i = 0; i < modes.size(); i++) {
			widths[i] = this.font.width(modes.get(i)) + 16;
			totalW += widths[i];
		}
		float trackX = contentX + contentW - 6 - totalW;
		float trackY = y + (h - segmentH - 4) / 2f;
		RenderUtil.roundedRect(graphics, trackX, trackY, totalW, segmentH + 4, CONTROL_RADIUS, Theme.TOGGLE_OFF);

		float selectedX = trackX + 2;
		float selectedW = widths[0];
		float x = trackX + 2;
		for (int i = 0; i < modes.size(); i++) {
			if (setting.is(modes.get(i))) {
				selectedX = x;
				selectedW = widths[i];
			}
			x += widths[i];
		}
		float pillX = animate(toggleAnims, setting, selectedX - trackX, INDICATOR_SPEED);
		RenderUtil.roundedRect(graphics, trackX + pillX, trackY + 2, selectedW, segmentH, CONTROL_RADIUS - 1, Theme.accent());

		x = trackX + 2;
		for (int i = 0; i < modes.size(); i++) {
			String mode = modes.get(i);
			int color = setting.is(mode) ? Theme.TEXT : Theme.TEXT_DIM;
			RenderUtil.text(graphics, this.font, mode, x + (widths[i] - this.font.width(mode)) / 2f, trackY + 6, color);
			addClippedHitbox(x, trackY, widths[i], segmentH + 4, clipTop, clipBottom, () -> setting.set(mode));
			x += widths[i];
		}
	}

	private void drawNumberRow(GuiGraphicsExtractor graphics, NumberSetting setting, float y, int clipTop, int clipBottom) {
		String value = setting.getDisplayValue();
		RenderUtil.text(graphics, this.font, value, contentX + contentW - 10 - this.font.width(value), y + 10, Theme.TEXT_DIM);

		float trackX = contentX + 10;
		float trackY = y + 27;
		float trackW = contentW - 20;
		RenderUtil.slider(graphics, trackX, trackY, trackW, setting.getProgress(), Theme.accent());
		addSliderHitbox(trackX, trackY, trackW, clipTop, clipBottom, setting::setFromProgress);
	}

	private void drawColorRow(GuiGraphicsExtractor graphics, ColorSetting setting, float y, int clipTop, int clipBottom) {
		float swatchX = contentX + contentW - 10 - 22;
		RenderUtil.roundedRect(graphics, swatchX - 1, y + 7, 24, 14, 4, Theme.withAlpha(Theme.TEXT, 0.15f));
		RenderUtil.roundedRect(graphics, swatchX, y + 8, 22, 12, 3, Theme.CARD);
		RenderUtil.roundedRect(graphics, swatchX, y + 8, 22, 12, 3, setting.get());
		String hex = setting.toHex();
		RenderUtil.text(graphics, this.font, hex, swatchX - 8 - this.font.width(hex), y + 10, Theme.TEXT_DIM);

		int valueWidth = this.font.width("255");
		float trackX = contentX + 24;
		float trackW = contentW - 24 - 10 - valueWidth - 8;
		for (int i = 0; i < CHANNEL_SHIFTS.length; i++) {
			int shift = CHANNEL_SHIFTS[i];
			float rowY = y + 30 + i * 16;
			int channel = setting.getChannel(shift);
			RenderUtil.text(graphics, this.font, CHANNEL_LABELS[i], contentX + 10, rowY, Theme.TEXT_DIM);
			RenderUtil.slider(graphics, trackX, rowY + 2, trackW, channel / 255.0, CHANNEL_COLORS[i]);
			String channelText = String.valueOf(channel);
			RenderUtil.text(graphics, this.font, channelText, contentX + contentW - 10 - this.font.width(channelText), rowY,
					Theme.TEXT_DIM);
			addSliderHitbox(trackX, rowY + 2, trackW, clipTop, clipBottom,
					progress -> setting.setChannel(shift, (int) Math.round(progress * 255)));
		}
	}

	/** A thin scrollbar in the right padding that brightens while the list is moving. */
	private void drawScrollbar(GuiGraphicsExtractor graphics, SmoothScroller scroller, float top, float height, float listHeight) {
		float active = animate(hoverAnims, scroller, scroller.isMoving() ? 1 : 0, SCROLLBAR_SPEED);
		if (scroller.getMax() <= 0) {
			return;
		}
		float thumbH = Math.max(16, height * height / listHeight);
		float thumbY = top + (height - thumbH) * (scroller.get() / scroller.getMax());
		RenderUtil.roundedRect(graphics, contentX + contentW + 4, thumbY, 3, thumbH, 1.5f,
				Theme.withAlpha(Theme.TEXT, 0.15f + 0.3f * active));
	}

	// ---------------------------------------------------------------- navigation

	private void selectCategory(Category category) {
		boolean changed = category != selectedCategory;
		selectedCategory = category;
		if (pageTween.isForward()) {
			closePage();
		}
		if (changed) {
			gridFade.restart();
			gridScroll.reset();
		}
	}

	private void openPage(Module module) {
		if (module != pageModule || !pageTween.isForward()) {
			settingsScroll.reset();
		}
		pageModule = module;
		pageTween.play(true);
		activeDrag = null;
		search.setFocused(false);
	}

	private void closePage() {
		if (returnTo != null) {
			// Opened from the HUD editor: "back" means back to the editor.
			onClose();
			return;
		}
		pageTween.play(false);
		activeDrag = null;
	}

	/** Plays the close animation, then shows next instead of returning to the game. */
	private void closeTo(Screen next) {
		afterClose = next;
		onClose();
	}

	/** Esc, Right Shift, etc. call this. Instead of closing right away, play the open animation in reverse. */
	@Override
	public void onClose() {
		if (closing) {
			return;
		}
		closing = true;
		activeDrag = null;
		search.setFocused(false);
		openTween.play(false);
	}

	private void finishClose() {
		if (this.minecraft.gui.screen() != this) {
			return;
		}
		if (afterClose != null) {
			this.minecraft.setScreenAndShow(afterClose);
		} else {
			super.onClose();
		}
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

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return true;
		}
		float x = toPanelX(event.x());
		float y = toPanelY(event.y());
		search.setFocused(false);
		// Check the last-drawn hitbox first, since it's on top.
		for (int i = hitboxes.size() - 1; i >= 0; i--) {
			Hitbox hitbox = hitboxes.get(i);
			if (hitbox.contains(x, y)) {
				hitbox.onClick().accept(x);
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (activeDrag != null) {
			activeDrag.update(toPanelX(event.x()));
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		activeDrag = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!closing) {
			(pageTween.isForward() ? settingsScroll : gridScroll).impulse(-scrollY);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (closing) {
			return true;
		}
		if (search.keyPressed(event)) {
			return true;
		}
		if (event.isEscape()) {
			if (pageTween.isForward()) {
				closePage();
			} else {
				onClose();
			}
			return true;
		}
		// Pressing the menu key again closes the menu, unless you're typing in the search box.
		if (!search.isFocused() && NovaClient.getOpenMenuKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (closing || pageTween.isForward()) {
			return false;
		}
		// Start typing anywhere on the grid to search.
		if (!search.isFocused() && event.isAllowedChatCharacter() && !Character.isWhitespace(event.codepoint())) {
			search.setFocused(true);
		}
		return search.charTyped(event);
	}

	// ---------------------------------------------------------------- helpers

	/** Undoes the open/close scale so mouse positions line up with what's drawn. */
	private float toPanelX(double screenX) {
		float centerX = panelX + panelW / 2f;
		return centerX + ((float) screenX - centerX) / panelScale;
	}

	private float toPanelY(double screenY) {
		float centerY = panelY + panelH / 2f;
		return centerY + ((float) screenY - centerY) / panelScale;
	}

	/** Advances a per-object animation toward target and returns its current value. */
	private float animate(Map<Object, Animated> anims, Object key, float target, float speed) {
		return anims.computeIfAbsent(key, k -> new Animated(target, speed)).update(target, deltaSeconds);
	}

	private boolean hovered(float x, float y, float w, float h) {
		return interactive && isInside(panelMouseX - hitOffsetX, panelMouseY - hitOffsetY, x, y, w, h);
	}

	private boolean insideClip(int clipTop, int clipBottom) {
		float y = panelMouseY - hitOffsetY;
		return y >= clipTop && y < clipBottom;
	}

	private static boolean isInside(float px, float py, float x, float y, float w, float h) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	private void addHitbox(float x, float y, float w, float h, Runnable action) {
		if (interactive) {
			hitboxes.add(new Hitbox(x + hitOffsetX, y + hitOffsetY, w, h, mouseX -> action.run()));
		}
	}

	/** Like addHitbox, but trimmed to the scrolling area so hidden content can't be clicked. */
	private void addClippedHitbox(float x, float y, float w, float h, int clipTop, int clipBottom, Runnable action) {
		float top = Math.max(y, clipTop);
		float bottom = Math.min(y + h, clipBottom);
		if (bottom > top) {
			addHitbox(x, top, w, bottom - top, action);
		}
	}

	/** A slider hitbox: clicking jumps to that spot, and dragging keeps updating until release. */
	private void addSliderHitbox(float trackX, float trackY, float trackW, int clipTop, int clipBottom,
			DoubleConsumer setProgress) {
		float top = Math.max(trackY - 5, clipTop);
		float bottom = Math.min(trackY + 9, clipBottom);
		if (!interactive || bottom <= top) {
			return;
		}
		float screenTrackX = trackX + hitOffsetX;
		hitboxes.add(new Hitbox(screenTrackX - 4, top + hitOffsetY, trackW + 8, bottom - top, mouseX -> {
			activeDrag = new SliderDrag(screenTrackX, trackW, setProgress);
			activeDrag.update(mouseX);
		}));
	}

	/** A record is a small immutable data class, like a TypeScript readonly object type. */
	private record Hitbox(float x, float y, float w, float h, DoubleConsumer onClick) {
		boolean contains(float px, float py) {
			return isInside(px, py, x, y, w, h);
		}
	}

	private record SliderDrag(float trackX, float trackW, DoubleConsumer setProgress) {
		void update(double mouseX) {
			setProgress.accept((mouseX - trackX) / trackW);
		}
	}
}
