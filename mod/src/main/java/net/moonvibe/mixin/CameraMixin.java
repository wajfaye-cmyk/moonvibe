package net.moonvibe.mixin;

import net.moonvibe.module.Modules;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC < 26.1
//$$ import net.minecraft.client.renderer.GameRenderer;
//#endif

/** Zoom: verkleinert das Sichtfeld, solange die Zoom-Taste gedrückt ist (ab 26.1 rechnet die Kamera das Sichtfeld aus, davor der GameRenderer). */
//#if MC >= 26.1
@Mixin(Camera.class)
public class CameraMixin {
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void moonvibe$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(Modules.ZOOM.apply(cir.getReturnValueF()));
	}
}
//#else
//$$ @Mixin(GameRenderer.class)
//$$ public class CameraMixin {
//$$ 	// nur das Sichtfeld der Welt (useFovSetting), nicht das der Hand
//$$ 	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	//#if MC >= 1.21.11
//$$ 	private void moonvibe$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
//$$ 		if (useFovSetting) cir.setReturnValue(Modules.ZOOM.apply(cir.getReturnValueF()));
//$$ 	}
	//#else
//$$ 	private void moonvibe$zoom(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
//$$ 		if (useFovSetting) cir.setReturnValue((double) Modules.ZOOM.apply((float) cir.getReturnValueD()));
//$$ 	}
	//#endif
//$$ }
//#endif
