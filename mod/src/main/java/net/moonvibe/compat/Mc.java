package net.moonvibe.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.SharedConstants;
import java.util.UUID;
//#if MC >= 1.16
import net.minecraft.client.CameraType;
//#endif
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
//#if MC >= 1.19.4
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
//#else
//$$ import net.minecraft.client.gui.screens.WinScreen;
//#endif
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
//#if MC >= 1.15
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
//#endif
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
//#if MC < 1.19
//$$ import net.moonvibe.mixin.MinecraftAccessor;
//#endif

/**
 * Kleine Brücke zu Minecraft-Funktionen, die je nach Version anders heißen.
 * Der übrige Code ruft nur diese Methoden auf und muss die Versionen nicht kennen.
 */
public final class Mc {
	private Mc() {}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	/** Gerade offenes Menü (null = im Spiel). */
	public static @Nullable Screen screen() {
		//#if MC >= 26.2
		return mc().gui.screen();
		//#else
		//$$ return mc().screen;
		//#endif
	}

	public static void setScreen(@Nullable Screen screen) {
		//#if MC >= 26.2
		mc().gui.setScreen(screen);
		//#else
		//$$ mc().setScreen(screen);
		//#endif
	}

	/** Ladebildschirm o. Ä. über allem (null = keiner). */
	public static @Nullable Overlay overlay() {
		//#if MC >= 26.2
		return mc().gui.overlay();
		//#else
		//$$ return mc().getOverlay();
		//#endif
	}

	/** HUD mit F1 ausgeblendet? */
	public static boolean hudHidden() {
		//#if MC >= 26.2
		return mc().gui.hud.isHidden();
		//#else
		//$$ return mc().options.hideGui;
		//#endif
	}

	/** Bild, in das das Spiel gezeichnet wird (für Bildschirmfotos). */
	public static RenderTarget mainTarget() {
		//#if MC >= 26.2
		return mc().gameRenderer.mainRenderTarget();
		//#else
		//$$ return mc().getMainRenderTarget();
		//#endif
	}

	// Kameraperspektive (F5): in Minecrafts Reihenfolge
	public static final int CAMERA_FIRST_PERSON = 0;
	public static final int CAMERA_BACK = 1;
	public static final int CAMERA_FRONT = 2;

	public static boolean firstPerson() {
		//#if MC >= 1.16
		return mc().options.getCameraType().isFirstPerson();
		//#else
		//$$ return mc().options.thirdPersonView == CAMERA_FIRST_PERSON;
		//#endif
	}

	public static void setCamera(int camera) {
		//#if MC >= 1.16
		mc().options.setCameraType(CameraType.values()[camera]);
		//#else
		//$$ mc().options.thirdPersonView = camera;
		//#endif
	}

	/** Bilder pro Sekunde */
	public static int fps() {
		//#if MC >= 1.19
		return mc().getFps();
		//#else
		//$$ return MinecraftAccessor.moonvibe$fps();
		//#endif
	}

	/** UUID des angemeldeten Accounts */
	public static UUID userId() {
		//#if MC >= 1.19
		return mc().getUser().getProfileId();
		//#else
		//$$ return mc().getUser().getGameProfile().getId();
		//#endif
	}

	/** Umschalt-Sprinten und -Schleichen von Minecraft (statt gedrückt halten). */
	public static void setToggleSprint(boolean sprint, boolean sneak) {
		Options o = mc().options;
		//#if MC >= 1.19
		if (o.toggleSprint().get() != sprint) o.toggleSprint().set(sprint);
		if (o.toggleCrouch().get() != sneak) o.toggleCrouch().set(sneak);
		//#elseif MC >= 1.15
		//$$ o.toggleSprint = sprint;
		//$$ o.toggleCrouch = sneak;
		//#else
		//$$ // 1.14 kennt das Umschalten noch nicht – dann einfach beim Vorwärtslaufen sprinten
		//$$ net.minecraft.client.player.LocalPlayer p = mc().player;
		//$$ if (sprint && p != null && p.input.forwardImpulse > 0 && !p.isSneaking() && !p.isUsingItem()) p.setSprinting(true);
		//#endif
	}

	/** Minecraft-Version, z. B. "1.21.11". */
	public static String versionName() {
		//#if MC >= 1.21.11
		return SharedConstants.getCurrentVersion().name();
		//#else
		//$$ return SharedConstants.getCurrentVersion().getName();
		//#endif
	}

	/** Copyright-Hinweis unten rechts im Hauptmenü (führt zu den Credits). */
	public static Component copyright() {
		//#if MC >= 1.20
		return Component.translatable("title.credits");
		//#else
		//$$ return Component.literal("Copyright Mojang AB. Do not distribute!");
		//#endif
	}

	public static void playClickSound() {
		mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	public static boolean allowsMultiplayer() {
		//#if MC >= 1.16
		return mc().allowsMultiplayer();
		//#else
		//$$ return true;
		//#endif
	}

	// ---------- Menüs von Minecraft ----------

	public static Screen singleplayerScreen(Screen parent) {
		return new SelectWorldScreen(parent);
	}

	public static Screen multiplayerScreen(Screen parent) {
		//#if MC >= 1.15
		return mc().options.skipMultiplayerWarning ? new JoinMultiplayerScreen(parent) : new SafetyScreen(parent);
		//#else
		//$$ return new JoinMultiplayerScreen(parent);
		//#endif
	}

	public static Screen optionsScreen(Screen parent) {
		//#if MC >= 26.3
		return new OptionsScreen(parent, mc().options);
		//#elseif MC >= 26.1
		//$$ return new OptionsScreen(parent, mc().options, false);
		//#else
		//$$ return new OptionsScreen(parent, mc().options);
		//#endif
	}

	public static Screen languageScreen(Screen parent) {
		return new LanguageSelectScreen(parent, mc().options, mc().getLanguageManager());
	}

	public static Screen creditsScreen(Screen parent) {
		//#if MC >= 1.19.4
		return new CreditsAndAttributionScreen(parent);
		//#else
		//$$ return new WinScreen(false, () -> setScreen(parent));
		//#endif
	}
}
