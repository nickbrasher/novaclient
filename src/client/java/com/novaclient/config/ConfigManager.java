package com.novaclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novaclient.NovaClient;
import com.novaclient.hud.HudElement;
import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Saves and loads config/novaclient.json, shaped like:
 * { "modules": { "FPS Display": { "enabled": true, "settings": { ... }, "hud": { "x": 0.0, "y": 0.0, "scale": 1.0 } } } }
 * HUD x/y are fractions of the screen (see HudElement), so they survive window resizes.
 */
public class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DELAY_MILLIS = 500;

	// One background thread for disk writes, so saving never stalls a frame.
	// Daemon = it won't keep the game process alive on exit.
	private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Nova Client config writer");
		thread.setDaemon(true);
		return thread;
	});
	private ScheduledFuture<?> pendingSave;
	private final Object writeLock = new Object();

	// Every snapshot gets a number when it's taken. A write is skipped if a newer snapshot
	// was already written, so a slow background save can't overwrite the final save on exit.
	private long snapshotCounter;
	private long lastWrittenSnapshot = -1;

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
		if (module instanceof HudElement element && json.get("hud") instanceof JsonObject hudJson) {
			element.setAnchor(getFloat(hudJson, "x", element.getAnchorX()), getFloat(hudJson, "y", element.getAnchorY()));
			element.setScale(getFloat(hudJson, "scale", element.getScale()));
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

	private static float getFloat(JsonObject json, String key, float fallback) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : fallback;
	}

	/**
	 * Saves half a second after the last call. Many quick changes (like dragging a
	 * slider) collapse into a single write. Safe to call as often as you like.
	 */
	public synchronized void saveSoon() {
		if (pendingSave != null) {
			pendingSave.cancel(false);
		}
		// When the delay is up, read module state on the game thread (where it's changed),
		// then hand the finished text to the writer thread.
		pendingSave = writer.schedule(
				() -> Minecraft.getInstance().execute(() -> {
					String json = toJson();
					long snapshot = nextSnapshot();
					writer.execute(() -> write(json, snapshot));
				}),
				SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS);
	}

	/** Saves right now on the calling thread. Used when the game is closing. */
	public synchronized void saveNow() {
		if (pendingSave != null) {
			pendingSave.cancel(false);
			pendingSave = null;
		}
		write(toJson(), nextSnapshot());
	}

	private synchronized long nextSnapshot() {
		return snapshotCounter++;
	}

	private String toJson() {
		JsonObject modulesJson = new JsonObject();
		for (Module module : modules.getAll()) {
			JsonObject settingsJson = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				settingsJson.add(setting.getName(), setting.toJson());
			}
			JsonObject moduleJson = new JsonObject();
			moduleJson.addProperty("enabled", module.isEnabled());
			moduleJson.add("settings", settingsJson);
			if (module instanceof HudElement element) {
				JsonObject hudJson = new JsonObject();
				hudJson.addProperty("x", element.getAnchorX());
				hudJson.addProperty("y", element.getAnchorY());
				hudJson.addProperty("scale", element.getScale());
				moduleJson.add("hud", hudJson);
			}
			modulesJson.add(module.getName(), moduleJson);
		}
		JsonObject root = new JsonObject();
		root.add("modules", modulesJson);
		return GSON.toJson(root);
	}

	/** Writes to a temp file first, then swaps it in, so a crash mid-write can't corrupt the config. */
	private void write(String json, long snapshot) {
		// Its own lock, so a slow disk never blocks the game thread calling saveSoon().
		synchronized (writeLock) {
			if (snapshot < lastWrittenSnapshot) {
				return;
			}
			lastWrittenSnapshot = snapshot;
			writeFile(json);
		}
	}

	private void writeFile(String json) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, json);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			NovaClient.LOGGER.error("Failed to save {}", file, e);
		}
	}
}
