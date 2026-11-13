package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
//#if MC >= 1.9
//$$ import net.minecraft.entity.effect.StatusEffects;
//#endif
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/** Aktive Trank-Effekte mit Symbol, Stufe und Restzeit. */
public final class PotionsModule extends HudModule {
	private static final Identifier ICONS = new Identifier("textures/gui/container/inventory.png");
	private static final int ROW = 22;
	private static final int WIDTH = 104;

	public final BoolSetting background = add(new BoolSetting("background", true));

	public PotionsModule() {
		super("potions", new ItemStack(Items.POTION), true, -4, 44);
	}

	private List<StatusEffectInstance> effects(MinecraftClient mc) {
		List<StatusEffectInstance> list = new ArrayList<StatusEffectInstance>();
		if (mc.player != null) list.addAll(mc.player.getStatusEffectInstances());
		if (list.isEmpty() && editing()) {
			//#if MC >= 1.9
			//$$ list.add(new StatusEffectInstance(StatusEffects.SPEED, 20 * 95, 1));
			//$$ list.add(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 300, 0));
			//#else
			list.add(new StatusEffectInstance(StatusEffect.SPEED.id, 20 * 95, 1));
			list.add(new StatusEffectInstance(StatusEffect.FIRE_RESISTANCE.id, 20 * 300, 0));
			//#endif
		}
		return list;
	}

	@Override
	public int width(MinecraftClient mc) {
		return WIDTH;
	}

	@Override
	public int height(MinecraftClient mc) {
		return Math.max(1, effects(mc).size()) * ROW - 2;
	}

	private static String duration(StatusEffectInstance effect) {
		if (effect.isPermanent()) return "**:**";
		int seconds = effect.getDuration() / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	@Override
	public void render(MinecraftClient mc) {
		int y = 0;
		for (StatusEffectInstance effect : effects(mc)) {
			//#if MC >= 1.9
			//$$ StatusEffect type = effect.getStatusEffect();
			//#else
			StatusEffect type = StatusEffect.STATUS_EFFECTS[effect.getEffectId()];
			//#endif
			if (type == null) continue;
			if (background.on()) Draw.rect(0, y, WIDTH, ROW - 2, 3, 0x8C0B0F17);
			if (type.hasIcon()) {
				int icon = type.getIconLevel();
				float u = (icon % 8) * 18 / 256f;
				float v = (198 + icon / 8 * 18) / 256f;
				Draw.region(ICONS, 2, y + 1, 18, 18, u, v, u + 18 / 256f, v + 18 / 256f, 0xFFFFFFFF);
			}
			String name = Mv.tr(effect.getTranslationKey());
			if (effect.getAmplifier() > 0) name += " " + Mv.tr("enchantment.level." + (effect.getAmplifier() + 1));
			Draw.mcText(name, 23, y + 2, 0xFFFFFFFF, true);
			Draw.mcText(duration(effect), 23, y + 11, effect.getDuration() < 200 ? 0xFFFF6B6B : 0xFFAEB8CC, true);
			y += ROW;
		}
	}
}
