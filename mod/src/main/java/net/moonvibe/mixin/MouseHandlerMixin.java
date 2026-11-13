package net.moonvibe.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.moonvibe.compat.Mc;
import net.moonvibe.feature.CpsTracker;
import net.moonvibe.module.Modules;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 1.21.11
import net.minecraft.client.input.MouseButtonInfo;
//#endif

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	/** CPS zählen – nur Klicks im Spiel, nicht in Menüs. */
	//#if MC >= 1.21.11
	@Inject(method = "onButton", at = @At("HEAD"))
	private void moonvibe$countClick(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		countClick(info.button(), action);
	}
	//#else
	//$$ @Inject(method = "onPress", at = @At("HEAD"))
	//$$ private void moonvibe$countClick(long handle, int button, int action, int mods, CallbackInfo ci) {
	//$$ 	countClick(button, action);
	//$$ }
	//#endif

	private static void countClick(int button, int action) {
		if (action != 1 || Mc.screen() != null) return;
		if (button == InputConstants.MOUSE_BUTTON_LEFT) CpsTracker.click(CpsTracker.LEFT);
		else if (button == InputConstants.MOUSE_BUTTON_RIGHT) CpsTracker.click(CpsTracker.RIGHT);
	}

	/** Beim Zoomen ändert das Mausrad die Zoomstärke statt den Hotbar-Slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void moonvibe$zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (yoffset != 0 && Modules.ZOOM.onScroll(yoffset)) ci.cancel();
	}
}
