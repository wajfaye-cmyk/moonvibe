//#if MC >= 1.13
//$$ package net.moonvibe.mixin;
//$$
//$$ import java.util.Map;
//$$ import net.minecraft.client.option.KeyBinding;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$
//$$ @Mixin(KeyBinding.class)
//$$ public interface KeyBindingAccessor {
//$$ 	/** Reihenfolge der Kategorien im Steuerungs-Menü (unbekannte Kategorien führen dort zum Absturz) */
//$$ 	@Accessor("field_15867")
//$$ 	static Map<String, Integer> moonvibe$categoryOrder() {
//$$ 		throw new AssertionError();
//$$ 	}
//$$ }
//#endif
