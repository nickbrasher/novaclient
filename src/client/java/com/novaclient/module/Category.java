package com.novaclient.module;

/**
 * An enum is a fixed set of named constants, similar to a TypeScript string union.
 * Each one carries a display name for the sidebar.
 */
public enum Category {
	HUD("HUD"),
	PVP("PvP"),
	VISUAL("Visual"),
	UTILITY("Utility");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
