package net.moonvibe.module.hud;

import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;

/** Aktive Trank-Effekte mit Symbol, Stufe und Restzeit. */
public final class PotionsModule extends HudModule {
	private static final int ROW = 22;
	private static final int WIDTH = 104;

	public final BoolSetting background = add(new BoolSetting("background", true));

	public PotionsModule() {
		super("potions", Items.POTION, true, -4, 44);
	}

	private List<MobEffectInstance> effects(Minecraft mc) {
		List<MobEffectInstance> list = new ArrayList<>();
		if (mc.player != null) list.addAll(mc.player.getActiveEffects());
		if (list.isEmpty() && editing()) {
			list.add(new MobEffectInstance(MobEffects.SPEED, 20 * 95, 1));
			list.add(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 300, 0));
		}
		return list;
	}

	@Override
	public int width(Minecraft mc) {
		return WIDTH;
	}

	@Override
	public int height(Minecraft mc) {
		return Math.max(1, effects(mc).size()) * ROW - 2;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Minecraft mc) {
		//#if MC >= 1.20.3
		float tickRate = mc.level != null ? mc.level.tickRateManager().tickrate() : 20f;
		//#endif
		int y = 0;
		for (MobEffectInstance effect : effects(mc)) {
			if (background.on()) Draw.rect(g, 0, y, WIDTH, ROW - 2, 3, 0x8C0B0F17);
			Draw.effectIcon(g, effect.getEffect(), 3, y + 2, 16);
			Component name = effect.getEffect().value().getDisplayName();
			if (effect.getAmplifier() > 0) {
				name = name.copy().append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
			}
			Draw.mcText(g, name, 23, y + 2, 0xFFFFFFFF, true);
			//#if MC >= 1.20.3
			Component time = MobEffectUtil.formatDuration(effect, 1.0f, tickRate);
			//#elseif MC >= 1.19
			//$$ Component time = MobEffectUtil.formatDuration(effect, 1.0f);
			//#else
			//$$ String time = MobEffectUtil.formatDuration(effect, 1.0f);
			//#endif
			//#if MC >= 1.19.4
			boolean ending = effect.endsWithin(200);
			//#else
			//$$ boolean ending = effect.getDuration() <= 200;
			//#endif
			Draw.mcText(g, time, 23, y + 11, ending ? 0xFFFF6B6B : 0xFFAEB8CC, true);
			y += ROW;
		}
	}
}
