package net.moonvibe.module.hud;

import net.moonvibe.Mv;
import net.moonvibe.feature.CpsTracker;
import net.moonvibe.module.setting.BoolSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

/** Die Text-Module von moonvibe. */
public final class TextModules {
	private TextModules() {}

	private static List<String> one(String line) {
		return Collections.singletonList(line);
	}

	public static final class Fps extends TextHudModule {
		public Fps() {
			super("fps", new ItemStack(Items.EXPERIENCE_BOTTLE), true, 4, 4);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			return one(MinecraftClient.getCurrentFps() + " FPS");
		}
	}

	public static final class Cps extends TextHudModule {
		public final BoolSetting rightClick = add(new BoolSetting("show_right_click", true));

		public Cps() {
			super("cps", new ItemStack(Items.REPEATER), true, 4, 23);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			return one(rightClick.on() ? CpsTracker.left() + " | " + CpsTracker.right() + " CPS" : CpsTracker.left() + " CPS");
		}
	}

	public static final class Ping extends TextHudModule {
		public Ping() {
			super("ping", new ItemStack(Items.ENDER_PEARL), true, 4, 42);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			ClientPlayerEntity player = mc.player;
			if (player == null || mc.getNetworkHandler() == null || mc.isIntegratedServerRunning()) {
				return one(editing() ? "24 ms" : "0 ms");
			}
			PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(player.getUuid());
			return one((info == null ? "?" : String.valueOf(info.getLatency())) + " ms");
		}
	}

	public static final class Coords extends TextHudModule {
		public final BoolSetting showDirection = add(new BoolSetting("show_direction", true));

		public Coords() {
			super("coords", new ItemStack(Items.COMPASS), false, 4, 142);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			ClientPlayerEntity p = mc.player;
			int x = p == null ? 0 : MathHelper.floor(p.x);
			int y = p == null ? 0 : MathHelper.floor(p.y);
			int z = p == null ? 0 : MathHelper.floor(p.z);
			String xyz = "X " + x + "  Y " + y + "  Z " + z;
			if (!showDirection.on()) return one(xyz);
			String dir = p == null ? "north" : p.getHorizontalDirection().getName();
			return Arrays.asList(xyz, Mv.tr("moonvibe.direction." + dir));
		}
	}

	public static final class Clock extends TextHudModule {
		public final BoolSetting twelveHour = add(new BoolSetting("twelve_hour", false));
		private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
		private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");

		public Clock() {
			super("clock", new ItemStack(Items.CLOCK), false, -4, 4);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			return one(LocalTime.now().format(twelveHour.on() ? H12 : H24));
		}
	}

	public static final class Server extends TextHudModule {
		public Server() {
			super("server", new ItemStack(Blocks.GRASS), false, CENTER, 4);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			ServerInfo server = mc.getCurrentServerEntry();
			if (server != null && !mc.isIntegratedServerRunning()) return one(server.address);
			return one(editing() ? "play.moonvibe.eu" : Mv.tr("moonvibe.hud.singleplayer"));
		}
	}

	public static final class Memory extends TextHudModule {
		public Memory() {
			super("memory", new ItemStack(Items.REDSTONE), false, -4, 23);
		}

		@Override
		protected List<String> lines(MinecraftClient mc) {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			long max = rt.maxMemory();
			return one(String.format("RAM %d%%  %.1f/%.1f GB", used * 100 / max, used / 1073741824.0, max / 1073741824.0));
		}
	}
}
