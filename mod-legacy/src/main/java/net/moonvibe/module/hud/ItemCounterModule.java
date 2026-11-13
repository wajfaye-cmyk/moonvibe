package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;

/** Zählt wichtige PvP-Gegenstände im Inventar (Pfeile, Perlen, Goldäpfel, Wurftränke). */
public final class ItemCounterModule extends HudModule {
	private static final int ROW = 18;

	public final BoolSetting arrows = add(new BoolSetting("count_arrows", true));
	public final BoolSetting pearls = add(new BoolSetting("count_pearls", true));
	public final BoolSetting apples = add(new BoolSetting("count_gapples", true));
	public final BoolSetting potions = add(new BoolSetting("count_potions", true));
	public final BoolSetting background = add(new BoolSetting("background", true));

	private static final class Entry {
		final ItemStack icon;
		final int count;

		Entry(ItemStack icon, int count) {
			this.icon = icon;
			this.count = count;
		}
	}

	public ItemCounterModule() {
		super("items", new ItemStack(Items.ARROW), false, -4, 160);
	}

	private static int count(Item item) {
		int n = 0;
		for (ItemStack s : Mv.mainInventory()) if (!Mv.isEmpty(s) && s.getItem() == item) n += Mv.count(s);
		return n;
	}

	private static int splashPotions() {
		//#if MC >= 1.9
		//$$ return count(Items.SPLASH_POTION);
		//#else
		int n = 0;
		for (ItemStack s : Mv.mainInventory()) if (!Mv.isEmpty(s) && s.getItem() == Items.POTION && PotionItem.isThrowable(s.getDamage())) n += Mv.count(s);
		return n;
		//#endif
	}

	private static ItemStack splashPotionIcon() {
		//#if MC >= 1.9
		//$$ return new ItemStack(Items.SPLASH_POTION);
		//#else
		return new ItemStack(Items.POTION, 1, 16421);
		//#endif
	}

	private List<Entry> entries(MinecraftClient mc) {
		List<Entry> list = new ArrayList<Entry>();
		if (mc.player != null) {
			if (arrows.on()) add(list, new ItemStack(Items.ARROW), count(Items.ARROW));
			if (pearls.on()) add(list, new ItemStack(Items.ENDER_PEARL), count(Items.ENDER_PEARL));
			if (apples.on()) add(list, new ItemStack(Items.GOLDEN_APPLE), count(Items.GOLDEN_APPLE));
			if (potions.on()) add(list, splashPotionIcon(), splashPotions());
		}
		if (list.isEmpty() && editing()) {
			list.add(new Entry(new ItemStack(Items.ARROW), 64));
			list.add(new Entry(new ItemStack(Items.ENDER_PEARL), 16));
			list.add(new Entry(new ItemStack(Items.GOLDEN_APPLE), 8));
		}
		return list;
	}

	private static void add(List<Entry> list, ItemStack icon, int count) {
		if (count > 0) list.add(new Entry(icon, count));
	}

	@Override
	public int width(MinecraftClient mc) {
		return 46;
	}

	@Override
	public int height(MinecraftClient mc) {
		return Math.max(1, entries(mc).size()) * ROW;
	}

	@Override
	public void render(MinecraftClient mc) {
		List<Entry> list = entries(mc);
		if (background.on() && !list.isEmpty()) Draw.rect(0, 0, width(mc), list.size() * ROW, 4, 0x8C0B0F17);
		int y = 1;
		for (Entry e : list) {
			Draw.item(e.icon, 2, y);
			Draw.mcText(String.valueOf(e.count), 21, y + 4, 0xFFFFFFFF, true);
			y += ROW;
		}
	}
}
