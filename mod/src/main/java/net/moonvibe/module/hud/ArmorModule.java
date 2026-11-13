package net.moonvibe.module.hud;

import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Rüstung + Gegenstand in der Hand mit Haltbarkeit (farbig je nach Zustand). */
public final class ArmorModule extends HudModule {
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int ROW = 17;

	public final BoolSetting showHand = add(new BoolSetting("show_hand", true));
	public final BoolSetting percent = add(new BoolSetting("durability_percent", false));

	public ArmorModule() {
		super("armor", Items.DIAMOND_CHESTPLATE, true, -4, -4);
	}

	private List<ItemStack> items(Minecraft mc) {
		List<ItemStack> result = new ArrayList<>();
		if (mc.player != null) {
			for (EquipmentSlot slot : ARMOR) {
				ItemStack stack = mc.player.getItemBySlot(slot);
				if (!stack.isEmpty()) result.add(stack);
			}
			if (showHand.on() && !mc.player.getMainHandItem().isEmpty()) result.add(mc.player.getMainHandItem());
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
	public int width(Minecraft mc) {
		return 54;
	}

	@Override
	public int height(Minecraft mc) {
		return Math.max(1, items(mc).size()) * ROW;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Minecraft mc) {
		int y = 0;
		for (ItemStack stack : items(mc)) {
			Draw.item(g, stack, 0, y);
			String text = null;
			int color = 0xFFFFFFFF;
			if (stack.isDamageableItem()) {
				int left = stack.getMaxDamage() - stack.getDamageValue();
				float ratio = (float) left / stack.getMaxDamage();
				color = ratio > 0.5f ? 0xFF6EE7A0 : ratio > 0.2f ? 0xFFFFD166 : 0xFFFF6B6B;
				text = percent.on() ? Math.round(ratio * 100) + "%" : String.valueOf(left);
			} else if (stack.getCount() > 1) {
				text = String.valueOf(stack.getCount());
			}
			if (text != null) Draw.mcText(g, text, 20, y + 4, color, true);
			y += ROW;
		}
	}
}
