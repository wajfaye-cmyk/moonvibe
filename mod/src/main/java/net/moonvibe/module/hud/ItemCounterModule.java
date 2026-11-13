package net.moonvibe.module.hud;

import net.moonvibe.module.setting.BoolSetting;
import net.moonvibe.ui.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Zählt wichtige PvP-Gegenstände im Inventar (Pfeile, Perlen, Goldäpfel, Totems, Tränke). */
public final class ItemCounterModule extends HudModule {
	private static final int ROW = 18;

	public final BoolSetting arrows = add(new BoolSetting("count_arrows", true));
	public final BoolSetting pearls = add(new BoolSetting("count_pearls", true));
	public final BoolSetting apples = add(new BoolSetting("count_gapples", true));
	public final BoolSetting totems = add(new BoolSetting("count_totems", true));
	public final BoolSetting potions = add(new BoolSetting("count_potions", true));
	public final BoolSetting background = add(new BoolSetting("background", true));

	private record Entry(ItemStack icon, int count) {}

	public ItemCounterModule() {
		super("items", Items.ARROW, false, -4, 160);
	}

	private static int count(Inventory inv, Item... items) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			for (Item item : items) if (s.getItem() == item) n += s.getCount();
		}
		return n;
	}

	private List<Entry> entries(Minecraft mc) {
		List<Entry> list = new ArrayList<>();
		if (mc.player != null) {
			Inventory inv = mc.player.getInventory();
			if (arrows.on()) add(list, Items.ARROW, count(inv, Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW));
			if (pearls.on()) add(list, Items.ENDER_PEARL, count(inv, Items.ENDER_PEARL));
			if (apples.on()) add(list, Items.GOLDEN_APPLE, count(inv, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE));
			if (totems.on()) add(list, Items.TOTEM_OF_UNDYING, count(inv, Items.TOTEM_OF_UNDYING));
			if (potions.on()) add(list, Items.SPLASH_POTION, count(inv, Items.SPLASH_POTION));
		}
		if (list.isEmpty() && editing()) {
			list.add(new Entry(new ItemStack(Items.ARROW), 64));
			list.add(new Entry(new ItemStack(Items.ENDER_PEARL), 16));
			list.add(new Entry(new ItemStack(Items.GOLDEN_APPLE), 8));
		}
		return list;
	}

	private static void add(List<Entry> list, Item item, int count) {
		if (count > 0) list.add(new Entry(new ItemStack(item), count));
	}

	@Override
	public int width(Minecraft mc) {
		return 46;
	}

	@Override
	public int height(Minecraft mc) {
		return Math.max(1, entries(mc).size()) * ROW;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Minecraft mc) {
		List<Entry> list = entries(mc);
		if (background.on() && !list.isEmpty()) Draw.rect(g, 0, 0, width(mc), list.size() * ROW, 4, 0x8C0B0F17);
		int y = 1;
		for (Entry e : list) {
			Draw.item(g, e.icon, 2, y);
			Draw.mcText(g, String.valueOf(e.count), 21, y + 4, 0xFFFFFFFF, true);
			y += ROW;
		}
	}
}
