package com.novaclient.module.impl;

import com.mojang.blaze3d.platform.InputConstants;
import com.novaclient.gui.anim.Animated;
import com.novaclient.module.Category;
import com.novaclient.module.Module;
import com.novaclient.module.setting.BooleanSetting;
import com.novaclient.module.setting.KeybindSetting;
import com.novaclient.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the zoom key to zoom in, like OptiFine's zoom.
 *
 * The actual zooming happens in CameraMixin, which multiplies the game's field of view
 * by getFovMultiplier() every frame. MouseHandlerMixin uses the same value to slow the
 * mouse down while zoomed, so aiming doesn't feel twitchy.
 */
public class ZoomModule extends Module {
	private static final float SMOOTH_SPEED = 12f;

	private final KeybindSetting zoomKey = addSetting(new KeybindSetting("Zoom Key", GLFW.GLFW_KEY_C));
	private final NumberSetting zoomFactor = addSetting(new NumberSetting("Zoom Amount", 4, 1.5, 10, 0.5));
	private final BooleanSetting smoothZoom = addSetting(new BooleanSetting("Smooth Zoom", true));
	private final BooleanSetting reduceSensitivity = addSetting(new BooleanSetting("Reduce Sensitivity", true));

	// 0 = not zoomed, 1 = fully zoomed. Animated so zooming eases in and out.
	private final Animated progress = new Animated(0, SMOOTH_SPEED);
	private long lastFrameNanos;

	public ZoomModule() {
		super("Zoom", "Hold a key to zoom in.", Category.VISUAL);
	}

	/** True while the zoom key is physically held in-game (not in a menu or chat). */
	private boolean isZoomHeld() {
		Minecraft client = Minecraft.getInstance();
		return isEnabled()
				&& zoomKey.isBound()
				&& client.gui.screen() == null
				&& client.player != null
				&& InputConstants.isKeyDown(client.getWindow(), zoomKey.get());
	}

	/**
	 * Called once per frame by CameraMixin. Returns what to multiply the FOV by:
	 * 1.0 when not zoomed, 1 / zoomAmount when fully zoomed.
	 */
	public float updateFovMultiplier() {
		long now = Util.getNanos();
		float deltaSeconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.1f);
		lastFrameNanos = now;

		float target = isZoomHeld() ? 1 : 0;
		if (smoothZoom.get()) {
			progress.update(target, deltaSeconds);
		} else {
			progress.snap(target);
		}
		return getFovMultiplier();
	}

	/** The current multiplier without advancing the animation. */
	public float getFovMultiplier() {
		float zoomed = 1f / zoomFactor.get().floatValue();
		return 1f + (zoomed - 1f) * progress.get();
	}

	/** How much to scale mouse movement by right now (1.0 = unchanged). */
	public double getSensitivityMultiplier() {
		return reduceSensitivity.get() ? getFovMultiplier() : 1.0;
	}

	@Override
	protected void onDisable() {
		progress.snap(0);
	}
}
