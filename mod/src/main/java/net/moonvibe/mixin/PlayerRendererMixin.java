//#if MC < 1.17
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.cosmetics.CosmeticsLayer;
//$$ import net.minecraft.client.model.PlayerModel;
//$$ import net.minecraft.client.player.AbstractClientPlayer;
//$$ import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//$$ import net.minecraft.client.renderer.entity.LivingEntityRenderer;
//$$ import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Cosmetics-Ebene an Spieler hängen – die Fabric-API kann das erst ab 1.17. */
//$$ @Mixin(PlayerRenderer.class)
//$$ public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
//$$ 	private PlayerRendererMixin(EntityRenderDispatcher dispatcher, PlayerModel<AbstractClientPlayer> model, float shadow) {
//$$ 		super(dispatcher, model, shadow);
//$$ 	}
//$$
//$$ 	@Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
//$$ 	private void moonvibe$addCosmetics(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
//$$ 		addLayer(new CosmeticsLayer((PlayerRenderer) (Object) this));
//$$ 	}
//$$ }
//#endif
