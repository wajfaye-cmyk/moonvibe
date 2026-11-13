//#if MC < 1.13
package net.moonvibe.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.module.Modules;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerInventory;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fenstertitel, Startbild und Mausrad-Zoom bis 1.12 (1.13 hat dafür eine neue Fenster- und Maus-Technik, siehe MouseMixin). */
@Mixin(MinecraftClient.class)
public abstract class LegacyClientMixin {
	@Shadow
	public int width;

	@Shadow
	public int height;

	@Shadow
	public abstract void updateDisplay();

	/** Fenstertitel: "moonvibe | Minecraft 1.8.9" */
	@ModifyArg(method = "setPixelFormat", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;setTitle(Ljava/lang/String;)V", remap = false))
	private String moonvibe$title(String title) {
		return "Moonvibe | " + title;
	}

	/** Beim Zoomen ändert das Mausrad die Zoomstärke statt den Hotbar-Slot. */
	//#if MC >= 1.9
	//$$ @Redirect(method = "tickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
	//#else
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;scrollInHotbar(I)V"))
	//#endif
	private void moonvibe$zoomScroll(PlayerInventory inventory, int amount) {
		if (!Modules.ZOOM.onScroll(amount)) inventory.scrollInHotbar(amount);
	}

	/** Startbild: dunkler Hintergrund mit moonvibe-Logo statt des weißen Mojang-Bildschirms. */
	@Inject(method = "loadLogo", at = @At("HEAD"), cancellable = true)
	private void moonvibe$splash(TextureManager textureManager, CallbackInfo ci) {
		BufferedImage logo;
		try (InputStream in = MinecraftClientMixin.class.getResourceAsStream("/assets/moonvibe/textures/gui/splash.png")) {
			logo = in == null ? null : ImageIO.read(in);
		} catch (Exception e) {
			return; // dann eben das normale Startbild
		}
		if (logo == null) return;
		ci.cancel();
		Window window = new Window((MinecraftClient) (Object) this);
		int scale = window.getScaleFactor();
		Framebuffer framebuffer = new Framebuffer(window.getWidth() * scale, window.getHeight() * scale, true);
		framebuffer.bind(false);
		GlStateManager.matrixMode(GL11.GL_PROJECTION);
		GlStateManager.loadIdentity();
		GlStateManager.ortho(0.0, window.getWidth(), window.getHeight(), 0.0, 1000.0, 3000.0);
		GlStateManager.matrixMode(GL11.GL_MODELVIEW);
		GlStateManager.loadIdentity();
		GlStateManager.translate(0.0F, 0.0F, -2000.0F);
		GlStateManager.disableLighting();
		GlStateManager.disableFog();
		GlStateManager.disableDepthTest();
		GlStateManager.disableTexture();

		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
		buffer.vertex(0, height, 0).color(8, 11, 18, 255).next();
		buffer.vertex(width, height, 0).color(8, 11, 18, 255).next();
		buffer.vertex(width, 0, 0).color(8, 11, 18, 255).next();
		buffer.vertex(0, 0, 0).color(8, 11, 18, 255).next();
		tessellator.draw();

		GlStateManager.enableTexture();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		NativeImageBackedTexture texture = new NativeImageBackedTexture(logo);
		textureManager.bindTexture(textureManager.registerDynamicTexture("moonvibe_splash", texture));
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		float w = Math.min(window.getWidth() * 0.6f, 380);
		float h = w * logo.getHeight() / logo.getWidth();
		float x = (window.getWidth() - w) / 2;
		float y = (window.getHeight() - h) / 2;
		buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
		buffer.vertex(x, y + h, 0).texture(0, 1).next();
		buffer.vertex(x + w, y + h, 0).texture(1, 1).next();
		buffer.vertex(x + w, y, 0).texture(1, 0).next();
		buffer.vertex(x, y, 0).texture(0, 0).next();
		tessellator.draw();
		GlStateManager.disableBlend();

		framebuffer.unbind();
		framebuffer.draw(window.getWidth() * scale, window.getHeight() * scale);
		GlStateManager.enableAlphaTest();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
		updateDisplay();
	}
}
//#endif
