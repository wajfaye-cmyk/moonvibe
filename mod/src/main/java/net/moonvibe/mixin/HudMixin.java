//#if MC < 1.15
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.Moonvibe;
//$$ import net.minecraft.client.gui.Gui;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** HUD-Module zeichnen – die Fabric-API für 1.14 hat dafür noch kein Ereignis. */
//$$ @Mixin(Gui.class)
//$$ public class HudMixin {
//$$ 	@Inject(method = "render", at = @At("TAIL"))
//$$ 	private void moonvibe$hud(float partialTick, CallbackInfo ci) {
//$$ 		Moonvibe.renderHud(partialTick);
//$$ 	}
//$$ }
//#endif
