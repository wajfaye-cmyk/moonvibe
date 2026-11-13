//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.cosmetics.CosmeticsFeature;
//$$ import net.minecraft.client.render.entity.EntityRenderDispatcher;
//$$ import net.minecraft.client.render.entity.PlayerEntityRenderer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Cosmetics-Ebene an Spieler hängen (1.13; bis 1.12 über die Legacy-Fabric-API). */
//$$ @Mixin(PlayerEntityRenderer.class)
//$$ public class PlayerRendererMixin {
//$$ 	@Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
//$$ 	private void moonvibe$addCosmetics(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
//$$ 		((LivingEntityRendererAccessor) this).moonvibe$addFeature(new CosmeticsFeature((PlayerEntityRenderer) (Object) this));
//$$ 	}
//$$ }
//#endif
