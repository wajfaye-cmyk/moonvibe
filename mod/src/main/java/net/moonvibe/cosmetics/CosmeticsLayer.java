package net.moonvibe.cosmetics;

import net.moonvibe.module.Modules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC >= 1.15
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
//#else
//$$ import com.mojang.blaze3d.platform.GLX;
//$$ import com.mojang.blaze3d.platform.GlStateManager;
//#endif
//#if MC >= 1.21.11
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
//#else
//$$ import net.minecraft.world.entity.EquipmentSlot;
//#endif
//#if MC < 1.21.11 && MC >= 1.15
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//#endif

/**
 * Zeichnet Hüte, Brillen, Rücken-Items, Flügel und Begleiter auf Spieler.
 * Neuere Versionen übergeben einen Render-Zustand und sammeln Modelle ein, ältere zeichnen den Spieler direkt.
 */
//#if MC >= 1.21.11
public class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible || !CosmeticsManager.available()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity entity = mc.level.getEntity(state.id);
		if (!(entity instanceof AbstractClientPlayer player)) return;

		float seconds = state.ageInTicks / 20f;
		float movement = Math.min(1f, state.walkAnimationSpeed * 1.5f + (state.isFallFlying ? 1f : 0f));
		for (String slot : CosmeticsManager.MODEL_SLOTS) {
			String id = CosmeticsManager.equipped(player.getUUID(), slot);
			if (id == null || hidden(slot, state.headEquipment, state.chestEquipment)) continue;
			CosmeticsManager.Loaded loaded = CosmeticsManager.model(slot, id);
			if (loaded == null) continue;
			for (ModelPart anchor : anchors(loaded.model())) {
				pose.pushPose();
				anchor.translateAndRotate(pose);
				for (CosmeticModel.State pass : passes(loaded.model(), seconds, movement)) {
					submit(collector, loaded, pass, pose, pass.emissivePass() ? FULL_BRIGHT : light, state.outlineColor);
				}
				pose.popPose();
			}
		}
	}

	private static void submit(SubmitNodeCollector collector, CosmeticsManager.Loaded loaded, CosmeticModel.State state, PoseStack pose, int light, int outline) {
		//#if MC >= 26.3
		collector.submitModel(loaded.model(), state, pose, RenderTypes.entityCutout(loaded.texture()), light, OverlayTexture.NO_OVERLAY, outline);
		//#else
		//$$ collector.submitModel(loaded.model(), state, pose, RenderTypes.entityCutout(loaded.texture()), light, OverlayTexture.NO_OVERLAY, outline, null);
		//#endif
	}
//#elseif MC >= 1.15
//$$ public class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
//$$ 	public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
//$$ 		super(renderer);
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
//$$ 		float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
//$$ 		if (player.isInvisible() || !CosmeticsManager.available()) return;
//$$ 		float seconds = ageInTicks / 20f;
//$$ 		float movement = Math.min(1f, limbSwingAmount * 1.5f + (player.isFallFlying() ? 1f : 0f));
//$$ 		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
//$$ 		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
//$$ 		for (String slot : CosmeticsManager.MODEL_SLOTS) {
//$$ 			String id = CosmeticsManager.equipped(player.getUUID(), slot);
//$$ 			if (id == null || hidden(slot, head, chest)) continue;
//$$ 			CosmeticsManager.Loaded loaded = CosmeticsManager.model(slot, id);
//$$ 			if (loaded == null) continue;
//$$ 			for (ModelPart anchor : anchors(loaded.model())) {
//$$ 				pose.pushPose();
//$$ 				anchor.translateAndRotate(pose);
//$$ 				for (CosmeticModel.State pass : passes(loaded.model(), seconds, movement)) {
//$$ 					loaded.model().setupAnim(pass);
//$$ 					loaded.model().renderToBuffer(pose, buffers.getBuffer(RenderTypes.entityCutout(loaded.texture())),
//$$ 						pass.emissivePass() ? FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
//$$ 				}
//$$ 				pose.popPose();
//$$ 			}
//$$ 		}
//$$ 	}
//#else
//$$ public class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
//$$ 	public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
//$$ 		super(renderer);
//$$ 	}
//$$
//$$ 	/** 1.14: Zeichnen direkt mit OpenGL, scale = Größe eines Modell-Pixels. */
//$$ 	@Override
//$$ 	public void render(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
//$$ 		float netHeadYaw, float headPitch, float scale) {
//$$ 		if (player.isInvisible() || !CosmeticsManager.available()) return;
//$$ 		float seconds = ageInTicks / 20f;
//$$ 		float movement = Math.min(1f, limbSwingAmount * 1.5f + (player.isFallFlying() ? 1f : 0f));
//$$ 		ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
//$$ 		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
//$$ 		for (String slot : CosmeticsManager.MODEL_SLOTS) {
//$$ 			String id = CosmeticsManager.equipped(player.getUUID(), slot);
//$$ 			if (id == null || hidden(slot, head, chest)) continue;
//$$ 			CosmeticsManager.Loaded loaded = CosmeticsManager.model(slot, id);
//$$ 			if (loaded == null) continue;
//$$ 			bindTexture(loaded.texture());
//$$ 			for (ModelPart anchor : anchors(loaded.model())) {
//$$ 				GlStateManager.pushMatrix();
//$$ 				anchor.translateTo(scale);
//$$ 				for (CosmeticModel.State pass : passes(loaded.model(), seconds, movement)) {
//$$ 					loaded.model().setupAnim(pass);
//$$ 					if (pass.emissivePass()) GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, 240.0F);
//$$ 					loaded.model().render(scale);
//$$ 				}
//$$ 				if (loaded.model().hasEmissive) {
//$$ 					// restore normal light for the following layers
//$$ 					int light = player.getLightColor();
//$$ 					GLX.glMultiTexCoord2f(GLX.GL_TEXTURE1, light % 65536, light / 65536);
//$$ 				}
//$$ 				GlStateManager.popMatrix();
//$$ 			}
//$$ 		}
//$$ 	}
//$$
//$$ 	@Override
//$$ 	public boolean colorsOnDamage() {
//$$ 		return false;
//$$ 	}
//#endif

	private static final int FULL_BRIGHT = 0xF000F0;

	/** Hüte nicht über Helmen, Rücken-Items und Flügel nicht zusammen mit einer Elytra. */
	private static boolean hidden(String slot, ItemStack head, ItemStack chest) {
		return switch (slot) {
			case "hat" -> !head.isEmpty();
			case "back", "wings" -> chest.getItem() == Items.ELYTRA;
			default -> false;
		};
	}

	/** Model parts a cosmetic is attached to: head, body, or both legs (shoes). */
	private ModelPart[] anchors(CosmeticModel model) {
		return switch (model.attach) {
			case HEAD -> new ModelPart[] {getParentModel().head};
			case LEGS -> new ModelPart[] {getParentModel().rightLeg, getParentModel().leftLeg};
			default -> new ModelPart[] {getParentModel().body};
		};
	}

	/** Normaler Durchgang, dazu einer mit voller Helligkeit für leuchtende Teile. */
	private static CosmeticModel.State[] passes(CosmeticModel model, float seconds, float movement) {
		boolean animate = Modules.COSMETICS.animations.on();
		CosmeticModel.State normal = new CosmeticModel.State(seconds, movement, false, animate);
		if (!model.hasEmissive) return new CosmeticModel.State[] {normal};
		return new CosmeticModel.State[] {normal, new CosmeticModel.State(seconds, movement, true, animate)};
	}
}
