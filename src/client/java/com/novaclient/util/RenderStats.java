package com.novaclient.util;

import net.minecraft.util.Util;

/**
 * Measures how long Nova Client's drawing code takes per frame (CPU time spent
 * describing what to draw; the GPU work happens later inside Minecraft).
 * The label refreshes 4x per second so it's readable and cheap.
 */
public final class RenderStats {
	private static final UpdateTimer REFRESH = new UpdateTimer(250);
	private static long accumulatedNanos;
	private static int frames;
	private static String label = "Nova render: -";

	private RenderStats() {
	}

	public static long start() {
		return Util.getNanos();
	}

	public static void stop(long startNanos) {
		accumulatedNanos += Util.getNanos() - startNanos;
	}

	/** Called once per frame (by the HUD layer). */
	public static void endFrame() {
		frames++;
		if (REFRESH.ready() && frames > 0) {
			label = String.format("Nova render: %.3f ms/frame", accumulatedNanos / 1_000_000.0 / frames);
			accumulatedNanos = 0;
			frames = 0;
		}
	}

	public static String label() {
		return label;
	}
}
