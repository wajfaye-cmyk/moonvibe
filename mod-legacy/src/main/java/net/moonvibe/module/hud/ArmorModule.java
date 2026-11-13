package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Rüstung + Gegenstand in der Hand mit Haltbarkeit (farbig je nach Zustand). */
public final class ArmorModule extends HudModule {
	private static final int ROW = 17;

	public final BoolSetting showHand = add(new BoolSetting("show_hand", true));
	public final BoolSetting percent = add(new BoolSetting("durability_percent", false));

	public ArmorModule() {
		super("armor", new ItemStack(Items.DIAMOND_CHESTPLATE), true, -4, -4);
	}

	private List<ItemStack> items(MinecraftClient mc) {
		List<ItemStack> result = new ArrayList<ItemStack>();
		if (mc.player != null) {
			// armor[3] = Helm … armor[0] = Schuhe
			for (int i = 3; i >= 0; i--) {
				ItemStack stack = Mv.armor(i);
				if (!Mv.isEmpty(stack)) result.add(stack);
			}
			ItemStack hand = mc.player.inventory.getMainHandStack();
			if (showHand.on() && !Mv.isEmpty(hand)) result.add(hand);
		}
		if (result.isEmpty() && editing()) {
			result.add(new ItemStack(Items.DIAMOND_HELMET));
			result.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
			result.add(new ItemStack(Items.DIAMOND_LEGGINGS));
			result.add(new ItemStack(Items.DIAMOND_BOOTS));
		}
		return result;
	}

	@Override
	public int width(MinecraftClient mc) {
		return 54;
	}

	@Override
	public int height(MinecraftClient mc) {
		return Math.max(1, items(mc).size()) * ROW;
	}

	@Override
	public void render(MinecraftClient mc) {
		int y = 0;
		for (ItemStack stack : items(mc)) {
			Draw.item(stack, 0, y);
			String text = null;
			int color = 0xFFFFFFFF;
			if (stack.isDamageable()) {
				int left = stack.getMaxDamage() - stack.getDamage();
				float ratio = (float) left / stack.getMaxDamage();
				color = ratio > 0.5f ? 0xFF6EE7A0 : ratio > 0.2f ? 0xFFFFD166 : 0xFFFF6B6B;
				text = percent.on() ? Math.round(ratio * 100) + "%" : String.valueOf(left);
			} else if (Mv.count(stack) > 1) {
				text = String.valueOf(Mv.count(stack));
			}
			if (text != null) Draw.mcText(text, 20, y + 4, color, true);
			y += ROW;
		}
	}
}
