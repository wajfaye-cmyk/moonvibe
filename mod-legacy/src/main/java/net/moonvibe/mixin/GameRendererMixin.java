package net.moonvibe.mixin;

import net.moonvibe.module.Modules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC >= 1.13
//$$ import net.minecraft.class_4218;
//#else
import net.minecraft.client.render.GameRenderer;
//#endif

/** Zoom: verkleinert das Sichtfeld, solange die Zoom-Taste gedrückt ist. */
//#if MC >= 1.13
//$$ @Mixin(class_4218.class) // GameRenderer
//#else
@Mixin(GameRenderer.class)
//#endif
public class GameRendererMixin {
	// nur das Sichtfeld der Welt (changingFov), nicht das der Hand
	//#if MC >= 1.13
	//$$ @Inject(method = "method_19062", at = @At("RETURN"), cancellable = true) // getFov
	//$$ private void moonvibe$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
	//$$ 	if (changingFov) cir.setReturnValue((double) Modules.ZOOM.apply((float) cir.getReturnValueD()));
	//$$ }
	//#else
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void moonvibe$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		if (changingFov) cir.setReturnValue(Modules.ZOOM.apply(cir.getReturnValueF()));
	}
	//#endif
}
