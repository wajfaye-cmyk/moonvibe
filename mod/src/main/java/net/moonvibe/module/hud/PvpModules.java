package net.moonvibe.module.hud;

import net.moonvibe.compat.Mc;
import net.moonvibe.feature.CombatTracker;
import net.moonvibe.module.setting.BoolSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Items;

/** PvP-Anzeigen: Toggle Sprint/Sneak, Combo, Reichweite, Geschwindigkeit. */
public final class PvpModules {
	private PvpModules() {}

	/** Schaltet das Umschalt-Sprinten/-Schleichen von Minecraft ein und zeigt den Zustand an. */
	public static final class ToggleSprint extends TextHudModule {
		public final BoolSetting toggleSprint = add(new BoolSetting("toggle_sprint", true));
		public final BoolSetting toggleSneak = add(new BoolSetting("toggle_sneak", false));

		public ToggleSprint() {
			super("togglesprint", Items.LEATHER_BOOTS, false, 4, -40);
		}

		/** Jeden Tick die Minecraft-Optionen an die Einstellungen anpassen. */
		public void apply(Minecraft mc) {
			if (!enabled()) return;
			Mc.setToggleSprint(toggleSprint.on(), toggleSneak.on());
		}

		@Override
		protected void onDisable() {
			Mc.setToggleSprint(false, false);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			LocalPlayer p = mc.player;
			boolean sneaking = p != null && p.isShiftKeyDown();
			boolean sprinting = p != null && p.isSprinting();
			if (sneaking) return List.of(I18n.get(toggleSneak.on() && mc.options.keyShift.isDown() ? "moonvibe.hud.sneaking_toggled" : "moonvibe.hud.sneaking"));
			if (sprinting || editing()) return List.of(I18n.get(toggleSprint.on() ? "moonvibe.hud.sprinting_toggled" : "moonvibe.hud.sprinting"));
			return List.of(I18n.get("moonvibe.hud.walking"));
		}
	}

	public static final class Combo extends TextHudModule {
		public Combo() {
			super("combo", Items.IRON_SWORD, false, 4, 180);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			return List.of(I18n.get("moonvibe.hud.combo", editing() && CombatTracker.combo() == 0 ? 3 : CombatTracker.combo()));
		}
	}

	public static final class Reach extends TextHudModule {
		public Reach() {
			super("reach", Items.STICK, false, 4, 198);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			double reach = CombatTracker.reach();
			if (reach < 0) return List.of(editing() ? "2.87 " + I18n.get("moonvibe.hud.blocks") : I18n.get("moonvibe.hud.reach_none"));
			return List.of(String.format("%.2f %s", reach, I18n.get("moonvibe.hud.blocks")));
		}
	}

	public static final class Speed extends TextHudModule {
		private double lastX;
		private double lastZ;
		private long lastTick;
		private double speed;

		public Speed() {
			super("speed", Items.FEATHER, false, 4, 216);
		}

		@Override
		protected List<String> lines(Minecraft mc) {
			LocalPlayer p = mc.player;
			if (p != null && p.tickCount != lastTick) {
				// Blöcke pro Sekunde aus der Bewegung seit dem letzten Tick (20 Ticks = 1 s)
				speed = speed * 0.7 + Math.hypot(p.getX() - lastX, p.getZ() - lastZ) * 20 * 0.3;
				lastX = p.getX();
				lastZ = p.getZ();
				lastTick = p.tickCount;
			}
			return List.of(String.format("%.1f %s", editing() && speed < 0.05 ? 5.6 : speed, I18n.get("moonvibe.hud.bps")));
		}
	}
}
