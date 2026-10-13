package com.novaclient.module;

import com.novaclient.hud.HudElement;
import com.novaclient.module.impl.ActiveModsModule;
import com.novaclient.module.impl.FpsDisplayModule;
import com.novaclient.module.impl.MenuSettingsModule;
import com.novaclient.module.impl.ToggleSprintModule;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Holds every module. Add new modules in the constructor. */
public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final MenuSettingsModule menuSettings = new MenuSettingsModule();

	private final List<HudElement> hudElements = new ArrayList<>();
	private final List<HudElement> hudElementsView = Collections.unmodifiableList(hudElements);

	// The last search, cached so the menu doesn't rebuild the list every frame.
	private String lastQuery;
	private Category lastCategory;
	private List<Module> lastResults = List.of();

	public ModuleManager() {
		register(menuSettings);
		register(new ActiveModsModule());
		register(new FpsDisplayModule());
		register(new ToggleSprintModule());
	}

	private void register(Module module) {
		modules.add(module);
		if (module instanceof HudElement element) {
			hudElements.add(element);
		}
	}

	/** Runs every module's per-tick logic. Called from NovaClient at the start of each client tick. */
	public void tick(Minecraft client) {
		for (int i = 0; i < modules.size(); i++) {
			modules.get(i).tick(client);
		}
	}

	/** Looks up a module by its class, e.g. get(ZoomModule.class). Throws if it was never registered. */
	public <T extends Module> T get(Class<T> type) {
		for (int i = 0; i < modules.size(); i++) {
			if (type.isInstance(modules.get(i))) {
				return type.cast(modules.get(i));
			}
		}
		throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
	}

	public MenuSettingsModule getMenuSettings() {
		return menuSettings;
	}

	/** Every module, including hidden ones. Used for saving. */
	public List<Module> getAll() {
		return Collections.unmodifiableList(modules);
	}

	/** Every HUD element, enabled or not. Built once at startup. */
	public List<HudElement> getHudElements() {
		return hudElementsView;
	}

	/**
	 * Optional is Java's way of saying "maybe a value, maybe not", instead of returning null.
	 * Use it like: getByName("FPS Display").ifPresent(Module::toggle);
	 */
	public Optional<Module> getByName(String name) {
		return modules.stream()
				.filter(module -> module.getName().equalsIgnoreCase(name))
				.findFirst();
	}

	public List<Module> getByCategory(Category category) {
		return modules.stream()
				.filter(module -> !module.isHidden() && module.getCategory() == category)
				.toList();
	}

	/**
	 * Modules matching a search query (name or description) within a category.
	 * A null category means all categories; an empty query matches everything.
	 */
	public List<Module> search(String query, Category category) {
		if (query.equals(lastQuery) && category == lastCategory) {
			return lastResults;
		}
		lastQuery = query;
		lastCategory = category;
		lastResults = computeSearch(query, category);
		return lastResults;
	}

	private List<Module> computeSearch(String query, Category category) {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		return modules.stream()
				.filter(module -> !module.isHidden())
				.filter(module -> category == null || module.getCategory() == category)
				.filter(module -> needle.isEmpty()
						|| module.getName().toLowerCase(Locale.ROOT).contains(needle)
						|| module.getDescription().toLowerCase(Locale.ROOT).contains(needle))
				.toList();
	}
}
