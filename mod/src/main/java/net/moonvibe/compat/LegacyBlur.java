//#if MC < 1.20.5
//$$ package net.moonvibe.compat;
//$$
//$$ import com.mojang.blaze3d.pipeline.RenderTarget;
//$$ import net.moonvibe.Moonvibe;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.PostChain;
//$$ import net.minecraft.resources.ResourceLocation;
//$$
//$$ /** Weichzeichner hinter Menüs – Minecraft bringt erst ab 1.20.5 einen eigenen mit. */
//$$ public final class LegacyBlur {
//$$ 	private static final ResourceLocation SHADER = new ResourceLocation(Moonvibe.MOD_ID, "shaders/post/menu_blur.json");
//$$ 	private static PostChain chain;
//$$ 	private static boolean failed;
//$$ 	private static int width;
//$$ 	private static int height;
//$$
//$$ 	private LegacyBlur() {}
//$$
//$$ 	/** Alles bisher Gezeichnete weichzeichnen. */
//$$ 	public static void apply() {
//$$ 		if (failed) return;
//$$ 		Minecraft mc = Minecraft.getInstance();
//$$ 		RenderTarget main = mc.getMainRenderTarget();
//$$ 		try {
//$$ 			if (chain == null) {
//$$ 				chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, SHADER);
//$$ 				width = -1;
//$$ 			}
//$$ 			if (width != main.width || height != main.height) {
//$$ 				chain.resize(main.width, main.height);
//$$ 				width = main.width;
//$$ 				height = main.height;
//$$ 			}
//$$ 		} catch (Exception e) {
//$$ 			failed = true;
//$$ 			Moonvibe.LOGGER.warn("Weichzeichner nicht verfügbar", e);
//$$ 			return;
//$$ 		}
//$$ 		chain.process(mc.getFrameTime());
//$$ 		main.bindWrite(false);
//$$ 	}
//$$ }
//#endif
