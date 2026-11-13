package net.moonvibe.mixin;

import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
//#if MC >= 1.13
//$$ import net.minecraft.class_4218;
//#else
import net.minecraft.client.render.GameRenderer;
//#endif

/** Zugriff auf Minecrafts Nachbearbeitungs-Shader (für den weichgezeichneten Menü-Hintergrund). */
//#if MC >= 1.13
//$$ @Mixin(class_4218.class) // GameRenderer
//#else
@Mixin(GameRenderer.class)
//#endif
public interface GameRendererAccessor {
	//#if MC >= 1.13
	//$$ @Invoker("method_19071") // loadShader
	//#else
	@Invoker("loadShader")
	//#endif
	void moonvibe$loadShader(Identifier id);

	//#if MC >= 1.13
	//$$ @Invoker("method_19072") // disableShader
	//#else
	@Invoker("disableShader")
	//#endif
	void moonvibe$disableShader();
}
