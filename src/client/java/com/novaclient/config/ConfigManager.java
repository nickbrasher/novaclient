package com.novaclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novaclient.NovaClient;
import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves and loads config/novaclient.json, shaped like:
 * { "modules": { "FPS Display": { "enabled": true, "settings": { "Scale": 1.0 } } } }
 */
public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file = FabricLoader.getInstance().getConfigDir().resolve(NovaClient.MOD_ID + ".json");
	private final ModuleManager modules;

	public ConfigManager(ModuleManager modules) {
		this.modules = modules;
	}

	public void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonObject modulesJson = root.getAsJsonObject("modules");
			if (modulesJson == null) {
				return;
			}
			for (Module module : modules.getAll()) {
				if (modulesJson.get(module.getName()) instanceof JsonObject moduleJson) {
					loadModule(module, moduleJson);
				}
			}
		} catch (IOException | RuntimeException e) {
			// A broken file shouldn't crash the game; fall back to defaults.
			NovaClient.LOGGER.error("Failed to load {}, using defaults", file, e);
		}
	}

	private void loadModule(Module module, JsonObject json) {
		if (json.get("enabled") instanceof JsonElement enabled && enabled.isJsonPrimitive()) {
			module.setEnabled(enabled.getAsBoolean());
		}
		JsonObject settingsJson = json.getAsJsonObject("settings");
		if (settingsJson == null) {
			return;
		}
		for (Setting<?> setting : module.getSettings()) {
			JsonElement value = settingsJson.get(setting.getName());
			if (value != null) {
				setting.fromJson(value);
			}
		}
	}

	public void save() {
		JsonObject modulesJson = new JsonObject();
		for (Module module : modules.getAll()) {
			JsonObject settingsJson = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				settingsJson.add(setting.getName(), setting.toJson());
			}
			JsonObject moduleJson = new JsonObject();
			moduleJson.addProperty("enabled", module.isEnabled());
			moduleJson.add("settings", settingsJson);
			modulesJson.add(module.getName(), moduleJson);
		}
		JsonObject root = new JsonObject();
		root.add("modules", modulesJson);

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root));
		} catch (IOException e) {
			NovaClient.LOGGER.error("Failed to save {}", file, e);
		}
	}
}
