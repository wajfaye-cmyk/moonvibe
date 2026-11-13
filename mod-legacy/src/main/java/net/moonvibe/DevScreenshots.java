package net.moonvibe;

import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.module.Modules;
import net.moonvibe.ui.screen.CosmeticsScreen;
import net.moonvibe.ui.screen.MoonMenuScreen;
import net.moonvibe.ui.screen.MoonTitleScreen;
import net.moonvibe.ui.screen.ModsScreen;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;
//#if MC >= 1.10
//$$ import net.minecraft.world.GameMode;
//#endif

/**
 * Nur für Entwickler: macht automatisch Bildschirmfotos der moonvibe-Oberfläche (Minecraft 1.8.9).
 * Aktiv nur mit -Dmoonvibe.dev.screenshots=<Ordner> (Bilder landen in <Ordner>/screenshots).
 *   -Dmoonvibe.dev.world       → in einer Welt testen (wird angelegt bzw. geladen: "moonworld")
 *   -Dmoonvibe.dev.cape=<Bild> → eigenes Cape hochladen
 */
final class DevScreenshots {
	/** Schritt: erst delayTicks warten, dann bis ready() stimmt, dann action ausführen. */
	private static final class Step {
		final int delayTicks;
		final BooleanSupplier ready;
		final Runnable action;

		Step(int delayTicks, BooleanSupplier ready, Runnable action) {
			this.delayTicks = delayTicks;
			this.ready = ready;
			this.action = action;
		}

		Step(int delayTicks, Runnable action) {
			this(delayTicks, () -> true, action);
		}
	}

	private static final List<Step> STEPS = new ArrayList<Step>();
	private static int wait;

	private DevScreenshots() {}

	static void startIfRequested() {
		String dir = System.getProperty("moonvibe.dev.screenshots");
		if (dir == null) return;
		File out = new File(dir);
		out.mkdirs();
		MinecraftClient mc = Mv.mc();
		// Test läuft im Hintergrund weiter, auch wenn das Fenster nicht im Vordergrund ist
		//#if MC >= 1.13
		//$$ mc.options.field_19973 = false; // pauseOnLostFocus
		//#else
		mc.options.pauseOnLostFocus = false;
		//#endif

		Events.onTick(client -> {
			if (STEPS.isEmpty()) return;
			if (wait-- > 0) return;
			Step step = STEPS.get(0);
			if (!step.ready.getAsBoolean()) return;
			STEPS.remove(0);
			step.action.run();
			wait = STEPS.isEmpty() ? 0 : STEPS.get(0).delayTicks;
		});

		// warten, bis das Hauptmenü wirklich zu sehen ist (neuere Versionen laden länger)
		STEPS.add(new Step(60, () -> mc.currentScreen instanceof MoonTitleScreen, () -> {}));
		STEPS.add(new Step(20, () -> shot(out, "title")));
		if (System.getProperty("moonvibe.dev.world") == null) {
			STEPS.add(new Step(10, mc::scheduleStop));
			return;
		}
		STEPS.add(new Step(5, () -> mc.startIntegratedServer("moonworld", "moonworld",
			//#if MC >= 1.10
			//$$ new LevelInfo(12345L, GameMode.CREATIVE, true, false, LevelGeneratorType.DEFAULT))));
			//#else
			new LevelInfo(12345L, LevelInfo.GameMode.CREATIVE, true, false, LevelGeneratorType.DEFAULT))));
			//#endif
		// warten, bis man wirklich in der Welt steht
		STEPS.add(new Step(20, () -> mc.player != null && mc.currentScreen == null, () -> {}));
		STEPS.add(new Step(120, () -> shot(out, "hud")));
		STEPS.add(new Step(5, () -> mc.setScreen(new MoonMenuScreen())));
		STEPS.add(new Step(30, () -> shot(out, "menu")));
		STEPS.add(new Step(5, () -> mc.setScreen(new ModsScreen(new MoonMenuScreen(), null))));
		STEPS.add(new Step(30, () -> shot(out, "mods")));
		STEPS.add(new Step(5, () -> mc.setScreen(new ModsScreen(new MoonMenuScreen(), Modules.HUD.get(7)))));
		STEPS.add(new Step(30, () -> shot(out, "settings")));
		STEPS.add(new Step(5, () -> mc.setScreen(new CosmeticsScreen(new MoonMenuScreen()))));
		STEPS.add(new Step(100, () -> shot(out, "wardrobe")));
		String cape = System.getProperty("moonvibe.dev.cape");
		if (cape != null) {
			STEPS.add(new Step(5, () -> {
				if (mc.currentScreen instanceof CosmeticsScreen) ((CosmeticsScreen) mc.currentScreen).uploadCape(new File(cape));
			}));
			STEPS.add(new Step(120, () -> shot(out, "wardrobe_custom")));
		}
		STEPS.add(new Step(5, () -> {
			if (mc.currentScreen instanceof CosmeticsScreen) ((CosmeticsScreen) mc.currentScreen).showSlot("hat");
			CosmeticsManager.equip("hat", "crown");
		}));
		STEPS.add(new Step(80, () -> shot(out, "wardrobe_hats")));
		STEPS.add(new Step(5, () -> {
			mc.setScreen(null);
			mc.options.perspective = 2;
		}));
		STEPS.add(new Step(60, () -> shot(out, "cosmetics_front")));
		STEPS.add(new Step(5, () -> mc.options.perspective = 1));
		STEPS.add(new Step(40, () -> shot(out, "cosmetics_back")));
		STEPS.add(new Step(5, () -> mc.options.perspective = 0));
		STEPS.add(new Step(10, mc::scheduleStop));
	}

	private static void shot(File dir, String name) {
		MinecraftClient mc = Mv.mc();
		//#if MC >= 1.13
		//$$ ScreenshotUtils.method_18273(dir, name + ".png", Mv.displayWidth(), Mv.displayHeight(), mc.getFramebuffer(), text -> {}); // saveScreenshot
		//#elseif MC >= 1.9
		//$$ ScreenshotUtils.method_12154(dir, name + ".png", mc.width, mc.height, mc.getFramebuffer()); // saveScreenshot mit Dateiname
		//#else
		ScreenshotUtils.saveScreenshot(dir, name + ".png", mc.width, mc.height, mc.getFramebuffer());
		//#endif
		Moonvibe.LOGGER.info("[dev] Bildschirmfoto gespeichert: {}", name);
	}
}
