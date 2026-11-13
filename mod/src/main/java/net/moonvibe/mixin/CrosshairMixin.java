//#if MC < 1.21.11
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.module.Modules;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.Gui;
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
	//#if MC < 1.20
//$$ import com.mojang.blaze3d.vertex.PoseStack;
	//#endif
//$$
//$$ /** Eigenes Fadenkreuz statt des normalen (neuere Versionen ersetzen es über die Fabric-API, siehe Moonvibe). */
//$$ @Mixin(Gui.class)
//$$ public class CrosshairMixin {
//$$ 	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	//#if MC >= 1.20
//$$ 	private void moonvibe$crosshair(GuiGraphicsExtractor g, float partialTick, CallbackInfo ci) {
	//#elseif MC >= 1.16
//$$ 	private void moonvibe$crosshair(PoseStack pose, CallbackInfo ci) {
//$$ 		GuiGraphicsExtractor g = new GuiGraphicsExtractor(pose);
	//#else
//$$ 	private void moonvibe$crosshair(CallbackInfo ci) {
//$$ 		GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//#endif
//$$ 		if (Modules.CROSSHAIR.render(g, Minecraft.getInstance())) ci.cancel();
//$$ 	}
//$$ }
//#endif
