package net.moonvibe.mixin;

import net.moonvibe.Mv;
import net.moonvibe.feature.CpsTracker;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.13
//$$ import net.minecraft.class_4107;
//#endif

/** CPS zählen – jeder Maus-Klick im Spiel (bis 1.12: links = -100, rechts = -99), nicht in Menüs. */
@Mixin(KeyBinding.class)
public class KeyBindingMixin {
	//#if MC >= 1.13
	//$$ @Inject(method = "method_18167", at = @At("HEAD")) // onKeyPressed(Key)
	//$$ private static void moonvibe$countClick(class_4107.class_4108 key, CallbackInfo ci) {
	//$$ 	if (Mv.mc().currentScreen != null || key.method_18158() != class_4107.class_4109.MOUSE) return; // Maustaste?
	//$$ 	if (key.method_18159() == 0) CpsTracker.click(CpsTracker.LEFT);
	//$$ 	else if (key.method_18159() == 1) CpsTracker.click(CpsTracker.RIGHT);
	//$$ }
	//#else
	@Inject(method = "onKeyPressed", at = @At("HEAD"))
	private static void moonvibe$countClick(int code, CallbackInfo ci) {
		if (Mv.mc().currentScreen != null) return;
		if (code == -100) CpsTracker.click(CpsTracker.LEFT);
		else if (code == -99) CpsTracker.click(CpsTracker.RIGHT);
	}
	//#endif
}
