//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.Events;
//$$ import net.minecraft.client.MinecraftClient;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Tick, Start und Beenden für 1.13 (bis 1.12 kommen diese Ereignisse von der Legacy-Fabric-API). */
//$$ @Mixin(MinecraftClient.class)
//$$ public class ClientEventsMixin {
//$$ 	@Inject(method = "tick", at = @At("TAIL"))
//$$ 	private void moonvibe$tick(CallbackInfo ci) {
//$$ 		Events.fireTick((MinecraftClient) (Object) this);
//$$ 	}
//$$
//$$ 	@Inject(method = "initializeGame", at = @At("TAIL"))
//$$ 	private void moonvibe$started(CallbackInfo ci) {
//$$ 		Events.fireStarted();
//$$ 	}
//$$
//$$ 	@Inject(method = "stop", at = @At("HEAD"))
//$$ 	private void moonvibe$stopping(CallbackInfo ci) {
//$$ 		Events.fireStopping();
//$$ 	}
//$$ }
//#endif
