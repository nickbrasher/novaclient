package com.novaclient.hud;

import com.novaclient.NovaClient;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

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
		// The editor draws the elements itself (with outlines), so skip them here.
		if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) {
			return;
		}
		for (HudElement element : NovaClient.getModules().getHudElements()) {
			if (element.isEnabled()) {
				element.draw(graphics, graphics.guiWidth(), graphics.guiHeight(), false);
			}
		}
	}
}
