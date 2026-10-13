package com.novaclient.mixin;

import com.novaclient.NovaClient;
import com.novaclient.module.impl.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	// @Shadow gives us access to MouseHandler's own private fields: how far the mouse
	// moved since the camera last turned.
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	/** While zoomed, scale down mouse movement before the camera turns. */
	@Inject(method = "turnPlayer", at = @At("HEAD"))
	private void novaclient$zoomSensitivity(double movementTime, CallbackInfo ci) {
		if (NovaClient.getModules() == null) {
			return;
		}
		double multiplier = NovaClient.getModules().get(ZoomModule.class).getSensitivityMultiplier();
		if (multiplier != 1.0) {
			accumulatedDX *= multiplier;
			accumulatedDY *= multiplier;
		}
	}
}
