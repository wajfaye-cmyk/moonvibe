package net.moonvibe.mixin;

import net.moonvibe.Mv;
import net.moonvibe.module.Modules;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if MC >= 1.9
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#endif
//#if MC >= 1.9 && MC < 1.13
//$$ import net.minecraft.client.util.Window;
//#endif

/** Eigenes Fadenkreuz ersetzt das normale, wenn das Modul an ist. */
@Mixin(InGameHud.class)
public class InGameHudMixin {
	//#if MC >= 1.13
	//$$ @Inject(method = "method_18366", at = @At("HEAD"), cancellable = true) // Fadenkreuz und Angriffsanzeige
	//$$ private void moonvibe$crosshair(float tickDelta, CallbackInfo ci) {
	//$$ 	if (Modules.CROSSHAIR.active(Mv.mc())) ci.cancel();
	//$$ }
	//#elseif MC >= 1.9
	//$$ // ab 1.9 zeichnet diese Methode Fadenkreuz und Angriffsanzeige
	//$$ @Inject(method = "method_12164", at = @At("HEAD"), cancellable = true)
	//$$ private void moonvibe$crosshair(float tickDelta, Window window, CallbackInfo ci) {
	//$$ 	if (Modules.CROSSHAIR.active(Mv.mc())) ci.cancel();
	//$$ }
	//#else
	@Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
	private void moonvibe$crosshair(CallbackInfoReturnable<Boolean> cir) {
		if (Modules.CROSSHAIR.active(Mv.mc())) cir.setReturnValue(false);
	}
	//#endif
}
