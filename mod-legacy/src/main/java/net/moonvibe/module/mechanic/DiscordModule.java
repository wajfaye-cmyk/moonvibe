package net.moonvibe.module.mechanic;

import net.moonvibe.discord.DiscordPresence;
import net.moonvibe.module.Module;
import net.moonvibe.module.setting.BoolSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Discord-Status ("Spielt moonvibe – Auf server.de"). */
public final class DiscordModule extends Module {
	public final BoolSetting showServer = add(new BoolSetting("show_server", true));

	public DiscordModule() {
		super("discord", Category.CLIENT, new ItemStack(Items.NAME_TAG), true);
	}

	@Override
	protected void onEnable() {
		DiscordPresence.start();
	}

	@Override
	protected void onDisable() {
		DiscordPresence.stop();
	}
}
