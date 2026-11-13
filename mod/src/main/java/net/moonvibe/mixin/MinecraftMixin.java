//#if MC >= 1.15
// (1.14 hat noch keine eigene Methode für den Fenstertitel)
package net.moonvibe.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fenstertitel: "moonvibe | Minecraft 26.3 - Einzelspieler" */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void moonvibe$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue("Moonvibe | " + cir.getReturnValue().replaceFirst("^Minecraft\\*?", "Minecraft"));
	}
}
//#endif
