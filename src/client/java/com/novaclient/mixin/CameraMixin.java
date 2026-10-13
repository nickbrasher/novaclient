package com.novaclient.mixin;

import com.novaclient.NovaClient;
import com.novaclient.module.impl.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies Zoom by shrinking the field of view the game calculates each frame. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	// "RETURN" runs our code right before calculateFov returns, so we can change its result.
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void novaclient$applyZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		if (NovaClient.getModules() == null) {
			return;
		}
		float multiplier = NovaClient.getModules().get(ZoomModule.class).updateFovMultiplier();
		if (multiplier != 1f) {
			cir.setReturnValue(cir.getReturnValue() * multiplier);
		}
	}
}
