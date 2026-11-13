package net.moonvibe;

import com.mojang.blaze3d.platform.InputConstants;
import net.moonvibe.compat.Mc;
import net.moonvibe.cosmetics.CosmeticsLayer;
import net.moonvibe.cosmetics.CosmeticsManager;
import net.moonvibe.discord.DiscordPresence;
import net.moonvibe.feature.CombatTracker;
import net.moonvibe.module.Modules;
import net.moonvibe.module.hud.HudModule;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.screen.MoonMenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//#if MC >= 1.17
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
//#endif
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
//#if MC >= 1.21.11
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
//#else
	//#if MC >= 1.15
//$$ import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
	//#endif
//$$ import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//#endif
//#if MC < 1.20
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//#endif
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Moonvibe implements ClientModInitializer {
	public static final String MOD_ID = "moonvibe";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String VERSION = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.map(c -> c.getMetadata().getVersion().getFriendlyString())
		.orElse("dev");

	//#if MC >= 1.21.11
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(id("main"));
	//#else
	//$$ private static final String CATEGORY = "key.category.moonvibe.main";
	//#endif
	public static KeyMapping zoomKey;
	public static KeyMapping menuKey;

	public static Identifier id(String path) {
		//#if MC >= 1.21.11
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
		//#else
		//$$ return new Identifier(MOD_ID, path);
		//#endif
	}

	@Override
	public void onInitializeClient() {
		LOGGER.info("Moonvibe {} loading", VERSION);
		Modules.load();

		zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moonvibe.zoom", InputConstants.KEY_C, CATEGORY));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.moonvibe.menu", InputConstants.KEY_RSHIFT, CATEGORY));

		//#if MC >= 1.21.11
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("hud"), Moonvibe::renderHud);
		// Eigenes Fadenkreuz ersetzt das normale, wenn das Modul an ist (ältere Versionen: CrosshairMixin)
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (g, delta) -> {
			if (!Modules.CROSSHAIR.render(g, Minecraft.getInstance())) original.extractRenderState(g, delta);
		});
		//#elseif MC >= 1.15
		//$$ HudRenderCallback.EVENT.register(Moonvibe::renderHud);
		//#endif
		// (1.14: HudMixin ruft renderHud auf)
		CombatTracker.register();
		// Cosmetics an Spieler hängen (vor 1.17: PlayerRendererMixin)
		//#if MC >= 1.17
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			//#if MC >= 1.21.11
			if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new CosmeticsLayer(avatar));
			//#else
			//$$ if (renderer instanceof PlayerRenderer player) helper.register(new CosmeticsLayer(player));
			//#endif
		});
		//#endif

		ClientTickEvents.END_CLIENT_TICK.register(Moonvibe::onTick);
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			if (Modules.DISCORD.enabled()) DiscordPresence.start();
			CosmeticsManager.start();
			DevScreenshots.startIfRequested();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			DiscordPresence.stop();
			Modules.save();
		});
	}

	private static void onTick(Minecraft mc) {
		while (menuKey.consumeClick()) {
			if (Mc.screen() == null) Mc.setScreen(new MoonMenuScreen());
		}
		Modules.ZOOM.tick(mc);
		Modules.TOGGLE_SPRINT.apply(mc);
		CombatTracker.tick(mc);
	}

	//#if MC >= 1.21.11
	public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
	//#elseif MC >= 1.20
	//$$ public static void renderHud(GuiGraphicsExtractor g, float delta) {
	//#elseif MC >= 1.16
	//$$ public static void renderHud(PoseStack pose, float delta) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(pose);
	//#else
	//$$ public static void renderHud(float delta) {
	//$$ 	GuiGraphicsExtractor g = new GuiGraphicsExtractor(new PoseStack());
	//#endif
		Minecraft mc = Minecraft.getInstance();
		// Im HUD-Editor zeichnet der Editor die Module selbst
		if (Mc.hudHidden() || Mc.screen() instanceof MoonMenuScreen) return;
		for (HudModule module : Modules.HUD) {
			if (module.enabled()) module.renderAt(g, mc, Draw.guiWidth(g), Draw.guiHeight(g));
		}
	}
}
