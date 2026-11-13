//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.moonvibe.Moonvibe;
//$$ import java.util.Arrays;
//$$ import java.util.Map;
//$$ import net.minecraft.client.option.GameOptions;
//$$ import net.minecraft.client.option.KeyBinding;
//$$ import org.apache.commons.lang3.ArrayUtils;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$
//$$ /** Eigene Tastenbelegungen in Minecrafts Liste aufnehmen (1.13; bis 1.12 über die Legacy-Fabric-API). */
//$$ @Mixin(GameOptions.class)
//$$ public class GameOptionsMixin {
//$$ 	@Shadow
//$$ 	public KeyBinding[] allKeys;
//$$
//$$ 	@Inject(method = "load", at = @At("HEAD"))
//$$ 	private void moonvibe$addKeys(CallbackInfo ci) {
//$$ 		Map<String, Integer> order = KeyBindingAccessor.moonvibe$categoryOrder();
//$$ 		if (!order.containsKey(Moonvibe.CATEGORY)) order.put(Moonvibe.CATEGORY, order.size() + 1);
//$$ 		for (KeyBinding key : Moonvibe.keyBindings()) {
//$$ 			if (!Arrays.asList(allKeys).contains(key)) allKeys = ArrayUtils.add(allKeys, key);
//$$ 		}
//$$ 	}
//$$ }
//#endif
