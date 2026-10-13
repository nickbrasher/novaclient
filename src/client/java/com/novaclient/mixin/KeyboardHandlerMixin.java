package com.novaclient.mixin;

import com.novaclient.NovaClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mixin patches a Minecraft class at load time. This one runs our code at the start
 * of KeyboardHandler.keyPress, which GLFW calls for every key press, release, and repeat.
 * We only look at the event; vanilla still handles the key as usual.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void novaclient$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		// action is GLFW_PRESS, GLFW_RELEASE, or GLFW_REPEAT (held down). Only fresh presses toggle.
		if (action == GLFW.GLFW_PRESS) {
			NovaClient.onKeyPressed(event.key());
		}
	}
}
