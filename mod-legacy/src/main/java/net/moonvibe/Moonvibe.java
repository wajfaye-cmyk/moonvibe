package net.moonvibe;

import net.moonvibe.cosmetics.CosmeticsFeature;
import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.discord.DiscordPresence;
import net.moonvibe.feature.CombatTracker;
import net.moonvibe.module.Modules;
import net.moonvibe.module.hud.HudModule;
import net.moonvibe.ui.screen.MoonMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//#if MC < 1.13
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.legacyfabric.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
//#endif

/** Einstiegspunkt der moonvibe-Mod für die alten Minecraft-Versionen – gleiche Funktionen wie die neuen Versionen. */
public class Moonvibe implements ClientModInitializer {
	public static final String MOD_ID = "moonvibe";
	public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
	public static final String VERSION = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.map(c -> c.getMetadata().getVersion().getFriendlyString())
		.orElse("dev");

	public static final String CATEGORY = "key.category.moonvibe.main";
	public static KeyBinding zoomKey;
	public static KeyBinding menuKey;

	/** Eigene Tastenbelegungen (werden beim ersten Aufruf angelegt – 1.13 fragt schon beim Laden der Optionen danach). */
	public static KeyBinding[] keyBindings() {
		if (zoomKey == null) {
			zoomKey = new KeyBinding("key.moonvibe.zoom", Mv.KEY_C, CATEGORY);
			menuKey = new KeyBinding("key.moonvibe.menu", Mv.KEY_RSHIFT, CATEGORY);
		}
		return new KeyBinding[] {zoomKey, menuKey};
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("moonvibe {} (Minecraft {}) wird geladen", VERSION, Mv.minecraftVersion());
		Modules.load();

		//#if MC >= 1.13
		//$$ // 1.13 hat keine Legacy-Fabric-API: Tasten, HUD und Cosmetics hängen eigene Mixins ein
		//$$ // (GameOptionsMixin, HudMixin, PlayerRendererMixin)
		//$$ keyBindings();
		//#else
		for (KeyBinding key : keyBindings()) KeyBindingHelper.registerKeyBinding(key);
		HudRenderCallback.EVENT.register((client, tickDelta) -> renderHud(client));
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityClass, renderer, helper) -> {
			if (renderer instanceof PlayerEntityRenderer) helper.register(new CosmeticsFeature((PlayerEntityRenderer) renderer));
		});
		//#endif

		Events.onTick(Moonvibe::onTick);
		Events.onStarted(() -> {
			if (Modules.DISCORD.enabled()) DiscordPresence.start();
			CosmeticsManager.start();
			DevScreenshots.startIfRequested();
		});
		Events.onStopping(() -> {
			DiscordPresence.stop();
			Modules.save();
		});
	}

	private static void onTick(MinecraftClient mc) {
		while (menuKey.wasPressed()) {
			if (mc.currentScreen == null) mc.setScreen(new MoonMenuScreen());
		}
		Modules.ZOOM.tick(mc);
		Modules.TOGGLE_SPRINT.apply(mc);
		CombatTracker.tick(mc);
	}

	public static void renderHud(MinecraftClient mc) {
		// Im HUD-Editor zeichnet der Editor die Module selbst
		if (Mv.hudHidden() || mc.currentScreen instanceof MoonMenuScreen) return;
		Modules.CROSSHAIR.render(mc);
		int w = Mv.width();
		int h = Mv.height();
		for (HudModule module : Modules.HUD) {
			if (module.enabled()) module.renderAt(mc, w, h);
		}
	}
}
