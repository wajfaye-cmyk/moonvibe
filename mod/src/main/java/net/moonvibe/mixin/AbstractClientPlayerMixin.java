package net.moonvibe.mixin;

import net.moonvibe.cosmetics.CosmeticsManager;
import net.minecraft.client.player.AbstractClientPlayer;
//#if MC >= 1.20.2
import net.minecraft.world.entity.player.PlayerSkin;
//#else
//$$ import net.minecraft.resources.ResourceLocation;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hängt moonvibe-Capes an den Skin eines Spielers. */
@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {
	//#if MC >= 1.20.2
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void moonvibe$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		cir.setReturnValue(CosmeticsManager.applyCape(self.getUUID(), cir.getReturnValue()));
	}
	//#else
	//$$ @Inject(method = "getCloakTextureLocation", at = @At("RETURN"), cancellable = true)
	//$$ private void moonvibe$cape(CallbackInfoReturnable<ResourceLocation> cir) {
	//$$ 	ResourceLocation cape = CosmeticsManager.cape(((AbstractClientPlayer) (Object) this).getUUID());
	//$$ 	if (cape != null) cir.setReturnValue(cape);
	//$$ }
	//#endif
}
