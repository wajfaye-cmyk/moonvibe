package net.moonvibe.module.hud;

import net.moonvibe.compat.Mc;
import net.moonvibe.feature.CpsTracker;
import net.moonvibe.module.setting.BoolSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;

/** Die Text-Module von moonvibe. */
public final class TextModules {
	private TextModules() {}

	public static final class Fps extends TextHudModule {
		public Fps() {
			super("fps", Items.EXPERIENCE_BOTTLE, true, 4, 4);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			return List.of(Mc.fps() + " FPS");
		}
	}

	public static final class Cps extends TextHudModule {
		public final BoolSetting rightClick = add(new BoolSetting("show_right_click", true));

		public Cps() {
			super("cps", Items.REPEATER, true, 4, 23);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			return List.of(rightClick.on() ? CpsTracker.left() + " | " + CpsTracker.right() + " CPS" : CpsTracker.left() + " CPS");
		}
	}

	public static final class Ping extends TextHudModule {
		public Ping() {
			super("ping", Items.ENDER_PEARL, true, 4, 42);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			LocalPlayer player = mc.player;
			if (player == null || mc.getConnection() == null || mc.hasSingleplayerServer()) {
				return List.of(editing() ? "24 ms" : "0 ms");
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
			return List.of((info == null ? "?" : info.getLatency()) + " ms");
		}
	}

	public static final class Coords extends TextHudModule {
		public final BoolSetting showDirection = add(new BoolSetting("show_direction", true));

		public Coords() {
			super("coords", Items.COMPASS, false, 4, 142);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			LocalPlayer p = mc.player;
			BlockPos pos = p == null ? BlockPos.ZERO : p.blockPosition();
			String xyz = "X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ();
			if (!showDirection.on()) return List.of(xyz);
			String dir = p == null ? "north" : p.getDirection().getSerializedName();
			return List.of(xyz, I18n.get("moonvibe.direction." + dir));
		}
	}

	public static final class Clock extends TextHudModule {
		public final BoolSetting twelveHour = add(new BoolSetting("twelve_hour", false));
		private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
		private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a");

		public Clock() {
			super("clock", Items.CLOCK, false, -4, 4);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			return List.of(LocalTime.now().format(twelveHour.on() ? H12 : H24));
		}
	}

	public static final class Server extends TextHudModule {
		public Server() {
			super("server", Items.GRASS_BLOCK, false, CENTER, 4);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			ServerData server = mc.getCurrentServer();
			if (server != null && !mc.hasSingleplayerServer()) return List.of(server.ip);
			return List.of(editing() ? "play.moonvibe.eu" : I18n.get("moonvibe.hud.singleplayer"));
		}
	}

	public static final class Memory extends TextHudModule {
		public Memory() {
			super("memory", Items.REDSTONE, false, -4, 23);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			long max = rt.maxMemory();
			return List.of(String.format("RAM %d%%  %.1f/%.1f GB", used * 100 / max, used / 1073741824.0, max / 1073741824.0));
		}
	}
}
