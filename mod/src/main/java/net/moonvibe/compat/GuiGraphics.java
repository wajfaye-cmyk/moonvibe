//#if MC < 1.20
//$$ package net.moonvibe.compat;
//$$
//$$ import com.mojang.blaze3d.platform.Window;
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import com.mojang.blaze3d.vertex.BufferBuilder;
//$$ import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.blaze3d.vertex.Tesselator;
//$$ import java.util.ArrayDeque;
//$$ import java.util.Deque;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.gui.Font;
//$$ import net.minecraft.client.gui.GuiComponent;
//$$ import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//$$ import net.minecraft.network.chat.Component;
//$$ import net.minecraft.resources.ResourceLocation;
//$$ import net.minecraft.world.item.ItemStack;
	//#if MC >= 1.19.3
//$$ import org.joml.Vector4f;
	//#elseif MC >= 1.15
//$$ import com.mojang.math.Vector4f;
	//#endif
	//#if MC >= 1.17
//$$ import com.mojang.blaze3d.vertex.VertexFormat;
//$$ import net.minecraft.client.renderer.GameRenderer;
	//#else
//$$ import org.lwjgl.opengl.GL11;
	//#endif
	//#if MC < 1.16
//$$ import net.moonvibe.mixin.MinecraftAccessor;
	//#endif
	//#if MC < 1.15
//$$ import com.mojang.blaze3d.platform.Lighting;
//$$ import net.minecraft.client.renderer.texture.TextureAtlas;
	//#endif
//$$
//$$ /**
//$$  * Nachbau von Minecrafts GuiGraphics (gibt es erst ab 1.20) für ältere Versionen – mit denselben
//$$  * Methodennamen, damit Menüs und HUD für alle Versionen denselben Code verwenden.
//$$  * Zeichnet sofort (ältere Versionen sammeln nichts ein).
//$$  */
//$$ public class GuiGraphics {
//$$ 	private static final Deque<int[]> SCISSOR = new ArrayDeque<>();
//$$ 	private final Minecraft minecraft = Minecraft.getInstance();
//$$ 	private final PoseStack pose;
//$$
//$$ 	public GuiGraphics(PoseStack pose) {
//$$ 		this.pose = pose;
//$$ 	}
//$$
//$$ 	public PoseStack pose() {
//$$ 		return pose;
//$$ 	}
//$$
//$$ 	public int guiWidth() {
//$$ 		return minecraft.getWindow().getGuiScaledWidth();
//$$ 	}
//$$
//$$ 	public int guiHeight() {
//$$ 		return minecraft.getWindow().getGuiScaledHeight();
//$$ 	}
//$$
//$$ 	public void flush() {
//$$ 	}
//$$
//$$ 	public void setColor(float r, float g, float b, float a) {
	//#if MC >= 1.17
//$$ 		RenderSystem.setShaderColor(r, g, b, a);
	//#else
//$$ 		RenderSystem.color4f(r, g, b, a);
	//#endif
//$$ 	}
//$$
//$$ 	public void fill(int x0, int y0, int x1, int y1, int color) {
	//#if MC >= 1.16
//$$ 		GuiComponent.fill(pose, x0, y0, x1, y1, color);
	//#else
//$$ 		withPose(() -> GuiComponent.fill(x0, y0, x1, y1, color));
	//#endif
//$$ 	}
//$$
//$$ 	/** Senkrechter Farbverlauf (oben → unten). */
//$$ 	public void fillGradient(int x0, int y0, int x1, int y1, int top, int bottom) {
	//#if MC >= 1.15
//$$ 		var matrix = pose.last().pose();
	//#endif
//$$ 		RenderSystem.enableBlend();
//$$ 		RenderSystem.defaultBlendFunc();
//$$ 		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
	//#if MC >= 1.17
//$$ 		RenderSystem.setShader(GameRenderer::getPositionColorShader);
//$$ 		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
	//#else
//$$ 		RenderSystem.disableTexture();
//$$ 		RenderSystem.disableAlphaTest();
//$$ 		RenderSystem.shadeModel(GL11.GL_SMOOTH);
//$$ 		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR);
	//#endif
	//#if MC >= 1.15
//$$ 		buffer.vertex(matrix, x1, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
//$$ 		buffer.vertex(matrix, x0, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
//$$ 		buffer.vertex(matrix, x0, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
//$$ 		buffer.vertex(matrix, x1, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
	//#else
//$$ 		// 1.14: die Verschiebung steckt schon in der OpenGL-Matrix
//$$ 		buffer.vertex(x1, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
//$$ 		buffer.vertex(x0, y0, 0).color(top >> 16 & 255, top >> 8 & 255, top & 255, top >>> 24).endVertex();
//$$ 		buffer.vertex(x0, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
//$$ 		buffer.vertex(x1, y1, 0).color(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, bottom >>> 24).endVertex();
	//#endif
//$$ 		Tesselator.getInstance().end();
	//#if MC < 1.17
//$$ 		RenderSystem.shadeModel(GL11.GL_FLAT);
//$$ 		RenderSystem.enableAlphaTest();
//$$ 		RenderSystem.enableTexture();
	//#endif
//$$ 		RenderSystem.disableBlend();
//$$ 	}
//$$
//$$ 	public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
	//#if MC >= 1.16
//$$ 		return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
	//#else
//$$ 		int[] end = new int[1];
//$$ 		withPose(() -> end[0] = shadow ? font.drawShadow(text, x, y, color) : font.draw(text, x, y, color));
//$$ 		return end[0];
	//#endif
//$$ 	}
//$$
//$$ 	public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
	//#if MC >= 1.16
//$$ 		return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
	//#else
//$$ 		return drawString(font(text, font), text.getString(), x, y, color, shadow);
	//#endif
//$$ 	}
//$$
	//#if MC < 1.16
//$$ 	/**
//$$ 	 * Vor 1.16 kann ein Text seine Schrift nicht selbst tragen – Draw.ui()/bold() merken sie sich deshalb im
//$$ 	 * "insertion"-Feld des Stils (z. B. "moonvibe:ui"), hier wird sie wieder aufgelöst.
//$$ 	 */
//$$ 	public static Font font(Component text, Font fallback) {
//$$ 		String id = text.getStyle().getInsertion();
//$$ 		if (id == null || !id.startsWith("moonvibe:")) return fallback;
//$$ 		return ((MinecraftAccessor) Minecraft.getInstance()).moonvibe$fontManager().get(new ResourceLocation(id));
//$$ 	}
//$$
//$$ 	public static int width(Component text) {
//$$ 		return font(text, Minecraft.getInstance().font).width(text.getString());
//$$ 	}
//$$
//$$ 	/** Ältere Versionen zeichnen mit der globalen Matrix – unsere Verschiebung/Skalierung dort anwenden. */
//$$ 	private void withPose(Runnable draw) {
		//#if MC >= 1.15
//$$ 		RenderSystem.pushMatrix();
//$$ 		RenderSystem.multMatrix(pose.last().pose());
//$$ 		draw.run();
//$$ 		RenderSystem.popMatrix();
		//#else
//$$ 		// 1.14: der PoseStack-Ersatz arbeitet direkt mit der OpenGL-Matrix. Text und Flächen hinterlassen
//$$ 		// dort ihre Farbe – zurücksetzen, sonst werden folgende Bilder eingefärbt.
//$$ 		draw.run();
//$$ 		RenderSystem.color4f(1, 1, 1, 1);
		//#endif
//$$ 	}
	//#endif
//$$
//$$ 	/** Textur-Ausschnitt (u, v, uw, vh) einer texW×texH großen Textur, gezeichnet bei (x, y) mit Größe w×h. */
//$$ 	public void blit(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int texW, int texH) {
//$$ 		bind(texture);
	//#if MC >= 1.16
//$$ 		GuiComponent.blit(pose, x, y, w, h, u, v, uw, vh, texW, texH);
	//#else
//$$ 		withPose(() -> GuiComponent.blit(x, y, w, h, u, v, uw, vh, texW, texH));
	//#endif
//$$ 	}
//$$
//$$ 	/** Bild aus einem Textur-Atlas (z. B. Trank-Symbole). */
//$$ 	public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
	//#if MC >= 1.19.3
//$$ 		bind(sprite.atlasLocation());
	//#elseif MC >= 1.15
//$$ 		bind(sprite.atlas().location());
	//#else
//$$ 		bind(TextureAtlas.LOCATION_MOB_EFFECTS); // 1.14: Bilder aus Atlanten gibt es hier nur für Trank-Effekte
	//#endif
	//#if MC >= 1.16
//$$ 		GuiComponent.blit(pose, x, y, z, w, h, sprite);
	//#else
//$$ 		withPose(() -> GuiComponent.blit(x, y, z, w, h, sprite));
	//#endif
//$$ 	}
//$$
//$$ 	private void bind(ResourceLocation texture) {
	//#if MC >= 1.17
//$$ 		RenderSystem.setShaderTexture(0, texture);
	//#else
//$$ 		minecraft.getTextureManager().bind(texture);
	//#endif
//$$ 	}
//$$
//$$ 	public void renderItem(ItemStack stack, int x, int y) {
	//#if MC >= 1.19.4
//$$ 		minecraft.getItemRenderer().renderAndDecorateItem(pose, stack, x, y);
	//#elseif MC >= 1.17
//$$ 		// Gegenstände nutzen hier noch die globale Matrix – unsere Verschiebung/Skalierung dort anwenden
//$$ 		PoseStack global = RenderSystem.getModelViewStack();
//$$ 		global.pushPose();
//$$ 		global.mulPoseMatrix(pose.last().pose());
//$$ 		RenderSystem.applyModelViewMatrix();
//$$ 		minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y);
//$$ 		global.popPose();
//$$ 		RenderSystem.applyModelViewMatrix();
	//#elseif MC >= 1.16
//$$ 		RenderSystem.pushMatrix();
//$$ 		RenderSystem.multMatrix(pose.last().pose());
//$$ 		minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y);
//$$ 		RenderSystem.popMatrix();
	//#elseif MC >= 1.15
//$$ 		withPose(() -> minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y));
	//#else
//$$ 		// 1.14: Licht und Tiefentest wie in Minecrafts Menüs (sonst wird der Verzauberungs-Glanz eckig)
//$$ 		Lighting.turnOnGui();
//$$ 		RenderSystem.enableDepthTest();
//$$ 		withPose(() -> minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y));
//$$ 		RenderSystem.disableDepthTest();
//$$ 		Lighting.turnOff();
	//#endif
//$$ 	}
//$$
//$$ 	/** Zeichenbereich begrenzen (in GUI-Pixeln; Verschiebung und Skalierung werden mit eingerechnet). */
//$$ 	public void enableScissor(int x0, int y0, int x1, int y1) {
//$$ 		float[] a = transform(x0, y0);
//$$ 		float[] b = transform(x1, y1);
//$$ 		int[] rect = {(int) Math.floor(a[0]), (int) Math.floor(a[1]), (int) Math.ceil(b[0]), (int) Math.ceil(b[1])};
//$$ 		int[] outer = SCISSOR.peek();
//$$ 		if (outer != null) {
//$$ 			rect = new int[] {Math.max(rect[0], outer[0]), Math.max(rect[1], outer[1]), Math.min(rect[2], outer[2]), Math.min(rect[3], outer[3])};
//$$ 		}
//$$ 		SCISSOR.push(rect);
//$$ 		applyScissor();
//$$ 	}
//$$
//$$ 	public void disableScissor() {
//$$ 		SCISSOR.poll();
//$$ 		applyScissor();
//$$ 	}
//$$
//$$ 	private static void applyScissor() {
//$$ 		int[] r = SCISSOR.peek();
//$$ 		if (r == null) {
	//#if MC >= 1.16
//$$ 			RenderSystem.disableScissor();
	//#else
//$$ 			GL11.glDisable(GL11.GL_SCISSOR_TEST);
	//#endif
//$$ 			return;
//$$ 		}
//$$ 		Window window = Minecraft.getInstance().getWindow();
//$$ 		double s = window.getGuiScale();
//$$ 		int x = (int) (r[0] * s);
//$$ 		int y = (int) (window.getHeight() - r[3] * s);
//$$ 		int w = Math.max(0, (int) ((r[2] - r[0]) * s));
//$$ 		int h = Math.max(0, (int) ((r[3] - r[1]) * s));
	//#if MC >= 1.16
//$$ 		RenderSystem.enableScissor(x, y, w, h);
	//#else
//$$ 		GL11.glEnable(GL11.GL_SCISSOR_TEST);
//$$ 		GL11.glScissor(x, y, w, h);
	//#endif
//$$ 	}
//$$
//$$ 	/** Aktuelle Vergrößerung der Matrix. */
//$$ 	public float scale() {
//$$ 		return transform(1, 0)[0] - transform(0, 0)[0];
//$$ 	}
//$$
//$$ 	private float[] transform(float x, float y) {
	//#if MC >= 1.15
//$$ 		Vector4f v = new Vector4f(x, y, 0, 1);
		//#if MC >= 1.19.3
//$$ 		v.mul(pose.last().pose());
		//#else
//$$ 		v.transform(pose.last().pose());
		//#endif
//$$ 		return new float[] {v.x(), v.y()};
	//#else
//$$ 		return pose.transform(x, y);
	//#endif
//$$ 	}
//$$ }
//#endif
