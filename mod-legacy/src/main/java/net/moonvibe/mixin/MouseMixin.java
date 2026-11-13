//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.module.Modules;
//$$ import net.minecraft.class_4112;
//$$ import net.minecraft.entity.player.PlayerInventory;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Redirect;
//$$
//$$ /** Beim Zoomen ändert das Mausrad die Zoomstärke statt den Hotbar-Slot (1.13: eigene Maus-Klasse). */
//$$ @Mixin(class_4112.class) // Mouse
//$$ public class MouseMixin {
//$$ 	@Redirect(method = "method_18241", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;method_15920(D)V")) // onScroll → scrollInHotbar
//$$ 	private void moonvibe$zoomScroll(PlayerInventory inventory, double amount) {
//$$ 		if (!Modules.ZOOM.onScroll(amount)) inventory.method_15920(amount);
//$$ 	}
//$$ }
//#endif
