package net.moonvibe;

import com.mojang.blaze3d.platform.NativeImage;
import net.moonvibe.compat.Mc;
import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.mixin.CreateWorldScreenAccessor;
import net.moonvibe.module.Modules;
import net.moonvibe.ui.screen.CosmeticsScreen;
import net.moonvibe.ui.screen.MoonMenuScreen;
import net.moonvibe.ui.screen.MoonTitleScreen;
import net.moonvibe.ui.screen.ModsScreen;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * Nur für Entwickler: macht automatisch Bildschirmfotos der moonvibe-Oberfläche, um Änderungen
 * ohne manuelles Spielen zu prüfen. Aktiv nur mit -Dmoonvibe.dev.screenshots=<Ordner>.
 *   -Dmoonvibe.dev.world      → in einer Welt testen (per Quick Play gestartet)
 *   -Dmoonvibe.dev.newworld   → vorher selbst eine neue Welt anlegen (wenn es noch keine gibt)
 *   -Dmoonvibe.dev.cape=<Bild> → eigenes Cape hochladen
 */
final class DevScreenshots {
	/** Schritt: erst delayTicks warten, dann bis ready() stimmt, dann action ausführen. */
	private record Step(int delayTicks, BooleanSupplier ready, Runnable action) {
		Step(int delayTicks, Runnable action) {
			this(delayTicks, () -> true, action);
		}
	}

	private static final List<Step> STEPS = new ArrayList<>();
	private static int wait;
	private static boolean loadingShot;

	private DevScreenshots() {}

	static void startIfRequested() {
		String dir = System.getProperty("moonvibe.dev.screenshots");
		if (dir == null) return;
		File out = new File(dir);
		out.mkdirs();
		Minecraft mc = Minecraft.getInstance();
		// Test läuft im Hintergrund weiter, auch wenn das Fenster nicht im Vordergrund ist
		mc.options.pauseOnLostFocus = false;

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!loadingShot && Mc.overlay() instanceof LoadingOverlay) {
				loadingShot = true;
				STEPS.add(0, new Step(30, () -> shot(out, "loading")));
			}
			if (Mc.overlay() != null && !loadingShot) return;
			if (STEPS.isEmpty()) return;
			if (wait-- > 0) return;
			Step step = STEPS.get(0);
			if (!step.ready().getAsBoolean()) return;
			STEPS.remove(0);
			step.action().run();
			wait = STEPS.isEmpty() ? 0 : STEPS.get(0).delayTicks();
		});

		boolean world = System.getProperty("moonvibe.dev.world") != null;
		// Hauptmenü erst fotografieren, wenn der Ladebildschirm ganz ausgeblendet ist
		// (die einmalige Barrierefreiheits-Abfrage frischer Installationen dabei überspringen)
		BooleanSupplier loaded = () -> {
			if (Mc.overlay() != null) return false;
			if (mc.level == null && !(Mc.screen() instanceof MoonTitleScreen)) {
				Mc.setScreen(null);
				return false;
			}
			return true;
		};
		if (!world) {
			STEPS.add(new Step(40, loaded, () -> {}));
			STEPS.add(new Step(30, () -> shot(out, "title")));
			STEPS.add(new Step(10, mc::stop));
			return;
		}
		if (System.getProperty("moonvibe.dev.newworld") != null) {
			// nach dem Wechsel ins Hauptmenü die Einblend-Animation abwarten
			STEPS.add(new Step(40, loaded, () -> {}));
			STEPS.add(new Step(30, () -> {
				shot(out, "title");
				Moonvibe.LOGGER.info("[dev] Hauptmenü: {} (Demo: {})", Mc.screen() == null ? null : Mc.screen().getClass().getName(), mc.isDemo());
			}));
			//#if MC >= 1.21.11
			STEPS.add(new Step(5, () -> CreateWorldScreen.openFresh(mc, () -> Mc.setScreen(null))));
			//#elseif MC >= 1.19
			//$$ STEPS.add(new Step(5, () -> CreateWorldScreen.openFresh(mc, null)));
			//#elseif MC >= 1.18
			//$$ STEPS.add(new Step(5, () -> Mc.setScreen(CreateWorldScreen.createFresh(null))));
			//#elseif MC >= 1.16
			//$$ STEPS.add(new Step(5, () -> Mc.setScreen(CreateWorldScreen.create(null))));
			//#else
			//$$ STEPS.add(new Step(5, () -> Mc.setScreen(new CreateWorldScreen(null))));
			//#endif
			STEPS.add(new Step(40, () -> Mc.screen() instanceof CreateWorldScreen, () -> ((CreateWorldScreenAccessor) Mc.screen()).moonvibe$create()));
		}
		// warten, bis man wirklich in der Welt steht
		STEPS.add(new Step(20, () -> mc.player != null && Mc.screen() == null && Mc.overlay() == null, () -> {}));
		STEPS.add(new Step(160, () -> shot(out, "hud")));
		STEPS.add(new Step(5, () -> Mc.setScreen(new MoonMenuScreen())));
		STEPS.add(new Step(30, () -> shot(out, "menu")));
		STEPS.add(new Step(5, () -> Mc.setScreen(new ModsScreen(new MoonMenuScreen(), null))));
		STEPS.add(new Step(30, () -> shot(out, "mods")));
		STEPS.add(new Step(5, () -> Mc.setScreen(new ModsScreen(new MoonMenuScreen(), Modules.HUD.get(7)))));
		STEPS.add(new Step(30, () -> shot(out, "settings")));
		STEPS.add(new Step(5, () -> Mc.setScreen(new CosmeticsScreen(new MoonMenuScreen()))));
		STEPS.add(new Step(100, () -> shot(out, "wardrobe")));
		String cape = System.getProperty("moonvibe.dev.cape");
		if (cape != null) {
			STEPS.add(new Step(5, () -> {
				if (Mc.screen() instanceof CosmeticsScreen screen) screen.uploadCape(Path.of(cape));
			}));
			STEPS.add(new Step(120, () -> shot(out, "wardrobe_custom")));
		}
		STEPS.add(new Step(5, () -> {
			if (Mc.screen() instanceof CosmeticsScreen screen) screen.showSlot("hat");
			CosmeticsManager.equip("hat", "crown");
		}));
		STEPS.add(new Step(80, () -> shot(out, "wardrobe_hats")));
		STEPS.add(new Step(5, () -> {
			Mc.setScreen(null);
			Mc.setCamera(Mc.CAMERA_FRONT);
		}));
		STEPS.add(new Step(60, () -> {
			shot(out, "cosmetics_front");
			if (mc.player == null) return;
			for (String slot : CosmeticsManager.MODEL_SLOTS) {
				String id = CosmeticsManager.equipped(mc.player.getUUID(), slot);
				Moonvibe.LOGGER.info("[dev] {} = {} geladen={}", slot, id, id != null && CosmeticsManager.model(slot, id) != null);
			}
			Moonvibe.LOGGER.info("[dev] Kopf-Ausrüstung: {}", mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
		}));
		STEPS.add(new Step(5, () -> Mc.setCamera(Mc.CAMERA_BACK)));
		STEPS.add(new Step(40, () -> shot(out, "cosmetics_back")));
		STEPS.add(new Step(5, () -> Mc.setCamera(Mc.CAMERA_FIRST_PERSON)));
		STEPS.add(new Step(10, mc::stop));
	}

	private static void shot(File dir, String name) {
		//#if MC >= 1.21.11
		Screenshot.takeScreenshot(Mc.mainTarget(), image -> save(image, dir, name));
		//#elseif MC >= 1.17
		//$$ save(Screenshot.takeScreenshot(Mc.mainTarget()), dir, name);
		//#else
		//$$ save(Screenshot.takeScreenshot(Mc.mainTarget().width, Mc.mainTarget().height, Mc.mainTarget()), dir, name);
		//#endif
	}

	private static void save(NativeImage image, File dir, String name) {
		try (NativeImage img = image) {
			img.writeToFile(new File(dir, name + ".png"));
			Moonvibe.LOGGER.info("[dev] Bildschirmfoto gespeichert: {}", name);
		} catch (Exception e) {
			Moonvibe.LOGGER.warn("[dev] Bildschirmfoto fehlgeschlagen", e);
		}
	}
}
