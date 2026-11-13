//#if MC < 1.21.11
//$$ package net.moonvibe.compat;
//$$
//$$ import net.minecraft.client.gui.GuiGraphicsExtractor;
//$$ import net.minecraft.world.entity.LivingEntity;
//#if MC >= 1.19.3
//$$ import net.minecraft.client.gui.screens.inventory.InventoryScreen;
//$$ import org.joml.Quaternionf;
//$$ import org.joml.Vector3f;
//#elseif MC >= 1.15
//$$ import com.mojang.blaze3d.platform.Lighting;
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import com.mojang.math.Quaternion;
//$$ import com.mojang.math.Vector3f;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//#else
//$$ import com.mojang.blaze3d.platform.GLX;
//$$ import com.mojang.blaze3d.platform.GlStateManager;
//$$ import com.mojang.blaze3d.platform.Lighting;
//$$ import net.minecraft.client.Minecraft;
//$$ import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//#endif
//$$
//$$ /**
//$$  * Spielerfigur wie im Inventar zeichnen – für Versionen vor 1.21.11. Neuere Versionen zeichnen über einen
//$$  * Render-Zustand (siehe Draw.entity); hier wird die Blickrichtung kurz am Wesen selbst umgestellt.
//$$  */
//$$ public final class LegacyEntity {
//$$ 	private static final float TILT = -0.12f;
//$$
//$$ 	private LegacyEntity() {}
//$$
//$$ 	/** Figur mittig im Rechteck (x0, y0)–(x1, y1), Blickrichtung yaw in Grad. */
//$$ 	public static void render(GuiGraphicsExtractor g, LivingEntity entity, float yaw, int x0, int y0, int x1, int y1, int size) {
//$$ 		float bodyRot = entity.yBodyRot, bodyRotO = entity.yBodyRotO, headRot = entity.yHeadRot, headRotO = entity.yHeadRotO;
//$$ 		float yRot = yRot(entity), xRot = xRot(entity), xRotO = entity.xRotO;
//$$ 		entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw;
//$$ 		setRot(entity, yaw, 0);
//$$ 		entity.xRotO = 0;
//$$
//$$ 		int cx = (x0 + x1) / 2;
//$$ 		int cy = (y0 + y1) / 2;
//$$ 		// ältere Versionen haben den Bezugspunkt an den Füßen statt in der Mitte
//$$ 		int feet = Math.round(cy + size * (entity.getBbHeight() / 2.0F + 0.0625F));
//$$ 		g.enableScissor(x0, y0, x1, y1);
	//#if MC >= 1.19.3
//$$ 		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
//$$ 		Quaternionf tilt = new Quaternionf().rotateX(TILT);
//$$ 		rotation.mul(tilt);
		//#if MC >= 1.20.5
//$$ 		Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F + 0.0625F, 0.0F);
//$$ 		InventoryScreen.renderEntityInInventory(g, cx, cy, size, translation, rotation, tilt, entity);
		//#elseif MC >= 1.20
//$$ 		InventoryScreen.renderEntityInInventory(g, cx, feet, size, rotation, tilt, entity);
		//#else
//$$ 		InventoryScreen.renderEntityInInventory(g.pose(), cx, feet, size, rotation, tilt, entity);
		//#endif
	//#elseif MC >= 1.15
//$$ 		draw(g.pose(), cx, feet, size, entity);
	//#else
//$$ 		drawGl(cx, feet, size, yaw, entity);
	//#endif
//$$ 		g.disableScissor();
//$$
//$$ 		entity.yBodyRot = bodyRot;
//$$ 		entity.yBodyRotO = bodyRotO;
//$$ 		entity.yHeadRot = headRot;
//$$ 		entity.yHeadRotO = headRotO;
//$$ 		setRot(entity, yRot, xRot);
//$$ 		entity.xRotO = xRotO;
//$$ 	}
//$$
//$$ 	private static float yRot(LivingEntity entity) {
	//#if MC >= 1.17
//$$ 		return entity.getYRot();
	//#else
//$$ 		return entity.yRot;
	//#endif
//$$ 	}
//$$
//$$ 	private static float xRot(LivingEntity entity) {
	//#if MC >= 1.17
//$$ 		return entity.getXRot();
	//#else
//$$ 		return entity.xRot;
	//#endif
//$$ 	}
//$$
//$$ 	private static void setRot(LivingEntity entity, float yRot, float xRot) {
	//#if MC >= 1.17
//$$ 		entity.setYRot(yRot);
//$$ 		entity.setXRot(xRot);
	//#else
//$$ 		entity.yRot = yRot;
//$$ 		entity.xRot = xRot;
	//#endif
//$$ 	}
//$$
	//#if MC < 1.15
//$$ 	/** 1.14: Nachbau von InventoryScreen.renderPlayerModel mit OpenGL (die GUI-Verschiebung steckt schon in der Matrix). */
//$$ 	private static void drawGl(int x, int y, int scale, float yaw, LivingEntity entity) {
//$$ 		// 1.14 zählt die Drehung von der Kamera aus (0 = zur Kamera), neuere Versionen ab 180
//$$ 		entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = yaw - 180;
//$$ 		entity.yRot = yaw - 180;
//$$ 		GlStateManager.color4f(1, 1, 1, 1);
//$$ 		GlStateManager.enableColorMaterial();
//$$ 		GlStateManager.pushMatrix();
//$$ 		GlStateManager.translatef(x, y, 50.0F);
//$$ 		GlStateManager.scalef(-scale, scale, scale);
//$$ 		GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
//$$ 		GlStateManager.rotatef(135.0F, 0.0F, 1.0F, 0.0F);
//$$ 		Lighting.turnOn();
//$$ 		GlStateManager.rotatef(-135.0F, 0.0F, 1.0F, 0.0F);
//$$ 		GlStateManager.rotatef((float) Math.toDegrees(TILT), 1.0F, 0.0F, 0.0F);
//$$ 		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
//$$ 		dispatcher.setPlayerRotY(180.0F);
//$$ 		dispatcher.setRenderShadow(false);
//$$ 		dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, false);
//$$ 		dispatcher.setRenderShadow(true);
//$$ 		GlStateManager.popMatrix();
//$$ 		Lighting.turnOff();
//$$ 		GlStateManager.disableRescaleNormal();
//$$ 		GlStateManager.activeTexture(GLX.GL_TEXTURE1);
//$$ 		GlStateManager.disableTexture();
//$$ 		GlStateManager.activeTexture(GLX.GL_TEXTURE0);
//$$ 	}
	//#endif
//$$
	//#if MC < 1.19.3 && MC >= 1.15
//$$ 	/** Nachbau von InventoryScreen.renderEntityInInventory ohne Blick zur Maus. (x, y) = Füße */
//$$ 	private static void draw(PoseStack gui, int x, int y, int scale, LivingEntity entity) {
		//#if MC >= 1.17
//$$ 		PoseStack global = RenderSystem.getModelViewStack();
//$$ 		global.pushPose();
//$$ 		global.mulPoseMatrix(gui.last().pose());
//$$ 		global.translate(x, y, 1050.0);
//$$ 		global.scale(1.0F, 1.0F, -1.0F);
//$$ 		RenderSystem.applyModelViewMatrix();
		//#else
//$$ 		RenderSystem.pushMatrix();
//$$ 		RenderSystem.multMatrix(gui.last().pose());
//$$ 		RenderSystem.translatef(x, y, 1050.0F);
//$$ 		RenderSystem.scalef(1.0F, 1.0F, -1.0F);
		//#endif
//$$
//$$ 		PoseStack pose = new PoseStack();
//$$ 		pose.translate(0.0, 0.0, 1000.0);
//$$ 		pose.scale(scale, scale, scale);
//$$ 		Quaternion rotation = Vector3f.ZP.rotationDegrees(180.0F);
//$$ 		Quaternion camera = Vector3f.XP.rotation(TILT);
//$$ 		rotation.mul(camera);
//$$ 		pose.mulPose(rotation);
//$$
		//#if MC >= 1.17
//$$ 		Lighting.setupForEntityInInventory();
		//#endif
//$$ 		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
//$$ 		camera.conj();
//$$ 		dispatcher.overrideCameraOrientation(camera);
//$$ 		dispatcher.setRenderShadow(false);
//$$ 		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		//#if MC >= 1.16
//$$ 		RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, buffers, 0xF000F0));
		//#else
//$$ 		dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, buffers, 0xF000F0);
		//#endif
//$$ 		buffers.endBatch();
//$$ 		dispatcher.setRenderShadow(true);
//$$
		//#if MC >= 1.17
//$$ 		global.popPose();
//$$ 		RenderSystem.applyModelViewMatrix();
//$$ 		Lighting.setupFor3DItems();
		//#else
//$$ 		RenderSystem.popMatrix();
		//#endif
//$$ 	}
	//#endif
//$$ }
//#endif
