package net.moonvibe.mixin;

import net.moonvibe.cosmetics.CosmeticsManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hängt moonvibe-Capes an Spieler. Achtung: In den Namen für 1.8.9 heißt die Cape-Textur "getSkinId"
 * (und der Skin "getCapeId") – das ist in diesen Mappings vertauscht.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {
	@Inject(method = "getSkinId()Lnet/minecraft/util/Identifier;", at = @At("RETURN"), cancellable = true)
	private void moonvibe$cape(CallbackInfoReturnable<Identifier> cir) {
		AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
		Identifier cape = CosmeticsManager.cape(self.getUuid());
		if (cape != null) cir.setReturnValue(cape);
	}
}
