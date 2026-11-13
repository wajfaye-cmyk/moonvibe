//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.Moonvibe;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import net.minecraft.client.gui.hud.InGameHud;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** HUD-Module zeichnen (1.13; bis 1.12 über die Legacy-Fabric-API). */
//$$ @Mixin(InGameHud.class)
//$$ public class HudMixin {
//$$ 	@Inject(method = "render", at = @At("TAIL"))
//$$ 	private void moonvibe$hud(float tickDelta, CallbackInfo ci) {
//$$ 		Moonvibe.renderHud(MinecraftClient.getInstance());
//$$ 	}
//$$ }
//#endif
