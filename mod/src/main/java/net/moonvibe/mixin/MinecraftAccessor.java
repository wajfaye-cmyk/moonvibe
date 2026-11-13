//#if MC < 1.19
//$$ package net.moonvibe.mixin;
//$$
//$$ import net.minecraft.client.Minecraft;
	//#if MC < 1.16
//$$ import net.minecraft.client.gui.font.FontManager;
	//#endif
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$
//$$ /** Private Felder von Minecraft, die ältere Versionen noch nicht freigeben. */
//$$ @Mixin(Minecraft.class)
//$$ public interface MinecraftAccessor {
//$$ 	/** Bilder pro Sekunde (vor 1.19) */
//$$ 	@Accessor("fps")
//$$ 	static int moonvibe$fps() {
//$$ 		throw new AssertionError();
//$$ 	}
	//#if MC < 1.16
//$$
//$$ 	/** Schriften (vor 1.16, um die Inter-Schrift direkt zu holen) */
//$$ 	@Accessor("fontManager")
//$$ 	FontManager moonvibe$fontManager();
	//#endif
//$$ }
//#endif
