package net.moonvibe.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.moonvibe.Moonvibe;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.Theme;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#if MC >= 26.3
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
//#elseif MC >= 1.21.11
//$$ import com.mojang.blaze3d.pipeline.RenderPipeline;
//#endif
//#if MC >= 1.21.11
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.resources.ResourceManager;
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.texture.DynamicTexture;
//#endif
//#if MC < 1.20
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
//#if MC < 1.17
//$$ import net.minecraft.client.gui.GuiComponent;
//$$ import org.lwjgl.opengl.GL11;
//#endif

/** Ladebildschirm im moonvibe-Stil: dunkler Hintergrund, moonvibe-Logo und runder Ladebalken. */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
	private static final Identifier MV_SPLASH = Moonvibe.id("textures/gui/splash_boot.png");
	private static final int MV_BACKGROUND = 0xFF080B12;
	/** Zeichenbefehl des Mojang-Logos – mit vollständiger Signatur, damit er sich für ältere Versionen umbenennen lässt. */
	//#if MC >= 26.3
	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V";
	//#elseif MC >= 1.21.11
	//$$ private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V";
	//#elseif MC >= 1.20
	//$$ private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lnet/minecraft/resources/Identifier;IIIIFFIIII)V";
	//#elseif MC >= 1.16
	//$$ private static final String BLIT = "Lnet/minecraft/client/gui/screens/LoadingOverlay;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIFFIIII)V";
	//#else
	//$$ private static final String BLIT = "Lnet/minecraft/client/gui/screens/LoadingOverlay;blit(IIIIII)V";
	//#endif

	@Shadow
	private float currentProgress;

	//#if MC >= 1.21.11
	@Inject(method = "registerTextures", at = @At("TAIL"))
	private static void moonvibe$registerSplash(TextureManager textureManager, CallbackInfo ci) {
		textureManager.registerAndLoad(MV_SPLASH, new ReloadableTexture(MV_SPLASH) {
			@Override
			public TextureContents loadContents(ResourceManager resourceManager) throws IOException {
				return new TextureContents(readSplash(), new TextureMetadataSection(true, true, MipmapStrategy.MEAN, 0.0F));
			}
		});
	}
	//#else
	//$$ @Inject(method = "registerTextures", at = @At("TAIL"))
	//$$ private static void moonvibe$registerSplash(Minecraft minecraft, CallbackInfo ci) {
	//$$ 	try {
	//$$ 		DynamicTexture texture = new DynamicTexture(readSplash());
	//$$ 		texture.setFilter(true, false);
	//$$ 		minecraft.getTextureManager().register(MV_SPLASH, texture);
	//$$ 	} catch (IOException e) {
	//$$ 		Moonvibe.LOGGER.warn("Ladebild fehlt", e);
	//$$ 	}
	//$$ }
	//#endif

	/** Direkt aus der Mod-Jar laden – beim ersten Laden sind Mod-Ressourcen noch nicht aktiv. */
	private static NativeImage readSplash() throws IOException {
		try (InputStream in = Moonvibe.class.getResourceAsStream("/assets/moonvibe/textures/gui/splash.png")) {
			if (in == null) throw new IOException("splash.png fehlt");
			return NativeImage.read(in);
		}
	}

	//#if MC >= 1.17
	/** Hintergrundfarbe: Minecraft fragt sie über BRAND_BACKGROUND.getAsInt() ab – wir geben unsere zurück. */
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int moonvibe$background(IntSupplier supplier) {
		return MV_BACKGROUND;
	}
	//#elseif MC >= 1.16
	//$$ /** Hintergrundfarbe: vor 1.17 eine feste Farbe – jede Hintergrundfläche bekommt unsere (Deckkraft bleibt). */
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;fill(Lcom/mojang/blaze3d/vertex/PoseStack;IIIII)V"))
	//$$ private void moonvibe$background(PoseStack pose, int x0, int y0, int x1, int y1, int color) {
	//$$ 	GuiComponent.fill(pose, x0, y0, x1, y1, (color & 0xFF000000) | (MV_BACKGROUND & 0xFFFFFF));
	//$$ }
	//#else
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;fill(IIIII)V"))
	//$$ private void moonvibe$background(int x0, int y0, int x1, int y1, int color) {
	//$$ 	GuiComponent.fill(x0, y0, x1, y1, (color & 0xFF000000) | (MV_BACKGROUND & 0xFFFFFF));
	//$$ }
	//#endif

	//#if MC >= 1.21.11
	/** Erste Hälfte des Mojang-Logos → stattdessen unser Logo zeichnen. */
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	private void moonvibe$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int sw, int sh, int tw, int th, int color) {
		drawLogo(g, y, h, (color >>> 24) / 255f);
	}

	/** Zweite Hälfte des Mojang-Logos weglassen. */
	@Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	private void moonvibe$skipSecondHalf(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v, int w, int h, int sw, int sh, int tw, int th, int color) {
	}
	//#elseif MC >= 1.20
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	//$$ private void moonvibe$logo(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
	//$$ 	// Minecraft zeichnet das Logo hier additiv – für unser Logo normale Überblendung
	//$$ 	float alpha = RenderSystem.getShaderColor()[3];
	//$$ 	RenderSystem.defaultBlendFunc();
	//$$ 	drawLogo(g, y, h, alpha);
	//$$ }
	//$$
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	//$$ private void moonvibe$skipSecondHalf(GuiGraphicsExtractor g, Identifier texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
	//$$ }
	//#elseif MC >= 1.16
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	//$$ private void moonvibe$logo(PoseStack pose, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
	//#if MC >= 1.17
	//$$ 	float alpha = RenderSystem.getShaderColor()[3];
	//#else
	//$$ 	float[] color = new float[4];
	//$$ 	GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color);
	//$$ 	float alpha = color[3];
	//#endif
	//$$ 	RenderSystem.defaultBlendFunc();
	//$$ 	drawLogo(new GuiGraphicsExtractor(pose), y, h, alpha);
	//$$ }
	//$$
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	//$$ private void moonvibe$skipSecondHalf(PoseStack pose, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
	//$$ }
	//#else
	//$$ /** Vor 1.16 ein einziges großes Mojang-Bild (weißer Ladebildschirm) → unser Logo in passender Größe. */
	//$$ @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = BLIT))
	//$$ private void moonvibe$logo(LoadingOverlay self, int x, int y, int u, int v, int w, int h) {
	//$$ 	float[] color = new float[4];
	//$$ 	GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color);
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//$$ 	int size = (int) (Math.min(Draw.guiWidth(g) * 0.75, Draw.guiHeight(g)) * 0.25);
	//$$ 	drawLogo(g, (Draw.guiHeight(g) - size) / 2, size, color[3]);
	//$$ }
	//#endif

	private static void drawLogo(GuiGraphicsExtractor g, int y, int h, float alpha) {
		int logoH = Math.round(h * 1.25f);
		int logoW = Math.round(logoH * 1024f / 341f);
		int cx = Draw.guiWidth(g) / 2;
		int cy = y + h / 2;
		Draw.image(g, MV_SPLASH, cx - logoW / 2, cy - logoH / 2, logoW, logoH, 1024, 341, alpha);
	}

	@Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
	//#if MC >= 1.20
	private void moonvibe$progressBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
	//#elseif MC >= 1.16
	//$$ private void moonvibe$progressBar(PoseStack pose, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(pose);
	//#elseif MC >= 1.15
	//$$ private void moonvibe$progressBar(int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//#else
	//$$ private void moonvibe$progressBar(int x0, int y0, int x1, int y1, float progress, float fade, CallbackInfo ci) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//#endif
		ci.cancel();
		float w = (x1 - x0) * 0.6f;
		float x = (x0 + x1) / 2f - w / 2;
		float y = (y0 + y1) / 2f - 2;
		Draw.rect(g, x, y, w, 4, 2, Draw.alpha(0x33FFFFFF, fade));
		Draw.gradient(g, x, y, Math.max(4, w * currentProgress), 4, 2, Draw.alpha(Theme.ACCENT_LIGHT, fade), Draw.alpha(Theme.ACCENT, fade));
	}
}
