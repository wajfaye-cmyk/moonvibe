package net.moonvibe.cosmetics;

import com.mojang.blaze3d.platform.GlStateManager;
import net.moonvibe.Mv;
import net.moonvibe.module.Modules;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.model.ModelPart;

/** Zeichnet Hüte, Brillen, Rücken-Items, Flügel und Begleiter auf Spieler (Minecraft 1.8.9). */
public class CosmeticsFeature implements FeatureRenderer<AbstractClientPlayerEntity> {
	private final PlayerEntityRenderer renderer;

	public CosmeticsFeature(PlayerEntityRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch, float scale) {
		if (player.isInvisible() || !CosmeticsManager.available()) return;
		boolean animate = Modules.COSMETICS.animations.on();
		float seconds = (player.ticksAlive + tickDelta) / 20f;
		float movement = Math.min(1f, limbDistance * 1.5f);
		for (String slot : CosmeticsManager.MODEL_SLOTS) {
			String id = CosmeticsManager.equipped(player.getUuid(), slot);
			if (id == null || hidden(slot, player)) continue;
			CosmeticsManager.Loaded loaded = CosmeticsManager.model(slot, id);
			if (loaded == null) continue;
			CosmeticModel model = loaded.model;

			for (ModelPart part : anchors(model)) {
				GlStateManager.pushMatrix();
				GlStateManager.color(1, 1, 1, 1);
				if (player.isSneaking()) GlStateManager.translate(0, 0.2f, 0);
				part.preRender(0.0625f);
				renderer.bindTexture(loaded.texture);
				GlStateManager.enableCull();
				model.render(seconds, movement, animate, false);
				if (model.hasEmissive) model.render(seconds, movement, animate, true);
				GlStateManager.popMatrix();
			}
		}
	}

	/** Model parts a cosmetic is attached to: head, body, or both legs (shoes). */
	private ModelPart[] anchors(CosmeticModel model) {
		if (model.attach == CosmeticModel.Attach.HEAD) return new ModelPart[] {renderer.getModel().head};
		if (model.attach == CosmeticModel.Attach.LEGS) return new ModelPart[] {renderer.getModel().rightLeg, renderer.getModel().leftLeg};
		return new ModelPart[] {renderer.getModel().body};
	}

	/** Hüte nicht über Helmen. */
	private static boolean hidden(String slot, AbstractClientPlayerEntity player) {
		return slot.equals("hat") && !Mv.isEmpty(Mv.armor(3));
	}

	@Override
	public boolean combineTextures() {
		return false;
	}
}
