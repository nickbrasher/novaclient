package com.novaclient.util;

import net.minecraft.util.Util;

/** Says "ready" at most once per interval. Used to refresh values like FPS a few times per second. */
public final class UpdateTimer {
	private final long intervalNanos;
	private long lastNanos;

	public UpdateTimer(long intervalMillis) {
		this.intervalNanos = intervalMillis * 1_000_000L;
	}

	public boolean ready() {
		long now = Util.getNanos();
		if (now - lastNanos >= intervalNanos) {
			lastNanos = now;
			return true;
		}
		return false;
	}
}
