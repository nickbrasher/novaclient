package com.novaclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.novaclient.config.ConfigManager;
import com.novaclient.gui.NovaMenuScreen;
import com.novaclient.hud.HudEditorScreen;
import com.novaclient.hud.HudRenderer;
import com.novaclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. Fabric calls onInitializeClient() once at startup because
 * fabric.mod.json lists this class under "entrypoints" -> "client".
 */
public class NovaClient implements ClientModInitializer {
	public static final String MOD_ID = "novaclient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Shows up as its own section in Options > Controls > Key Binds.
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private static KeyMapping openMenuKey;
	private static KeyMapping openHudEditorKey;
	private static ModuleManager modules;
	private static ConfigManager config;

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		config = new ConfigManager(modules);
		config.load();

		// The string is a translation key; the visible text lives in lang/en_us.json.
		openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.novaclient.open_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				CATEGORY
		));

		// Right Option on a Mac keyboard. Change it in Options > Controls > Key Binds.
		openHudEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.novaclient.open_hud_editor",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_ALT,
				CATEGORY
		));

		// Runs at the end of every client tick (20x per second).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				client.setScreenAndShow(new NovaMenuScreen());
			}
			while (openHudEditorKey.consumeClick()) {
				client.setScreenAndShow(new HudEditorScreen());
			}
		});

		HudRenderer.register();

		LOGGER.info("Nova Client initialized with {} modules", modules.getAll().size());
	}

	public static KeyMapping getOpenMenuKey() {
		return openMenuKey;
	}

	public static KeyMapping getOpenHudEditorKey() {
		return openHudEditorKey;
	}

	public static ModuleManager getModules() {
		return modules;
	}

	public static ConfigManager getConfig() {
		return config;
	}
}
