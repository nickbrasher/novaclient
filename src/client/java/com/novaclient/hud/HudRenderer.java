package com.novaclient.hud;

import com.novaclient.NovaClient;
import com.novaclient.gui.RenderUtil;
import com.novaclient.util.RenderStats;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Hooks into Minecraft's HUD (via Fabric API) and draws every enabled HudElement. */
public final class HudRenderer {
	private HudRenderer() {
	}

	public static void register() {
		// Drawn just before chat, so chat stays on top of our elements.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(NovaClient.MOD_ID, "hud"),
				(graphics, deltaTracker) -> render(graphics));
	}

	private static void render(GuiGraphicsExtractor graphics) {
		long start = RenderStats.start();
		Minecraft minecraft = Minecraft.getInstance();
		// The editor draws the elements itself (with outlines), so skip them here.
		if (!(minecraft.gui.screen() instanceof HudEditorScreen)) {
			int width = graphics.guiWidth();
			int height = graphics.guiHeight();
			// Indexed loop: no iterator object per frame. Disabled elements are skipped entirely.
			List<HudElement> elements = NovaClient.getModules().getHudElements();
			for (int i = 0; i < elements.size(); i++) {
				HudElement element = elements.get(i);
				if (element.isEnabled() && element.isVisibleOnHud()) {
					element.draw(graphics, width, height, false);
				}
			}
		}
		RenderStats.stop(start);
		RenderStats.endFrame();

		if (NovaClient.getModules().getMenuSettings().isShowRenderTimeEnabled()) {
			String label = RenderStats.label();
			RenderUtil.text(graphics, minecraft.font, label, graphics.guiWidth() - minecraft.font.width(label) - 4,
					graphics.guiHeight() - 12, 0xFFFFFFFF, true);
		}
	}
}
