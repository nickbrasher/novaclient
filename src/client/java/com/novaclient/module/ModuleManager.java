package com.novaclient.module;

import com.novaclient.hud.HudElement;
import com.novaclient.module.impl.ActiveModsModule;
import com.novaclient.module.impl.FpsDisplayModule;
import com.novaclient.module.impl.MenuSettingsModule;
import com.novaclient.module.impl.ToggleSprintModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Holds every module. Add new modules in the constructor. */
public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final MenuSettingsModule menuSettings = new MenuSettingsModule();

	public ModuleManager() {
		register(menuSettings);
		register(new ActiveModsModule());
		register(new FpsDisplayModule());
		register(new ToggleSprintModule());
	}

	private void register(Module module) {
		modules.add(module);
	}

	public MenuSettingsModule getMenuSettings() {
		return menuSettings;
	}

	/** Every module, including hidden ones. Used for saving. */
	public List<Module> getAll() {
		return Collections.unmodifiableList(modules);
	}

	/** Every HUD element, enabled or not. */
	public List<HudElement> getHudElements() {
		List<HudElement> elements = new ArrayList<>();
		for (Module module : modules) {
			if (module instanceof HudElement element) {
				elements.add(element);
			}
		}
		return elements;
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
