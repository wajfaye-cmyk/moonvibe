package net.moonvibe.module.hud;

import com.google.gson.JsonObject;
import net.moonvibe.Mv;
import net.moonvibe.module.Module;
import net.moonvibe.module.setting.SliderSetting;
import net.moonvibe.ui.Draw;
import net.moonvibe.ui.screen.MoonMenuScreen;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * Ein HUD-Element, das man im HUD-Editor verschieben kann.
 *
 * Standardposition: Abstand in GUI-Pixeln vom linken/oberen Rand (positive Werte),
 * vom rechten/unteren Rand (negative Werte) oder mittig ({@link #CENTER}).
 * Nach dem Verschieben wird die Position relativ zum freien Platz gespeichert (0 = links/oben, 1 = rechts/unten).
 */
public abstract class HudModule extends Module {
	public static final float CENTER = Float.POSITIVE_INFINITY;

	private final float defaultX;
	private final float defaultY;
	private Float relX;
	private Float relY;
	public final SliderSetting scale = add(new SliderSetting("scale", 1.0f, 0.5f, 2.0f, 0.05f, new Function<Float, String>() {
		@Override
		public String apply(Float v) {
			return Math.round(v * 100) + "%";
		}
	}));

	protected HudModule(String id, ItemStack icon, boolean enabledByDefault, float defaultX, float defaultY) {
		super(id, Category.HUD, icon, enabledByDefault);
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	/** Größe ohne Skalierung */
	public abstract int width(MinecraftClient mc);

	public abstract int height(MinecraftClient mc);

	/** Zeichnet das Modul bei (0, 0). */
	public abstract void render(MinecraftClient mc);

	/** true, solange der HUD-Editor offen ist – Module zeigen dann Beispieldaten, wenn echte fehlen. */
	public static boolean editing() {
		return Mv.mc().currentScreen instanceof MoonMenuScreen;
	}

	public float scale() {
		return scale.value();
	}

	public float scaledWidth(MinecraftClient mc) {
		return width(mc) * scale();
	}

	public float scaledHeight(MinecraftClient mc) {
		return height(mc) * scale();
	}

	private static float place(Float rel, float def, float free) {
		if (rel != null) return rel * free;
		if (def == CENTER) return free / 2f;
		return Math.max(0, Math.min(free, def >= 0 ? def : free + def));
	}

	public float x(MinecraftClient mc, int screenWidth) {
		return place(relX, defaultX, Math.max(0, screenWidth - scaledWidth(mc)));
	}

	public float y(MinecraftClient mc, int screenHeight) {
		return place(relY, defaultY, Math.max(0, screenHeight - scaledHeight(mc)));
	}

	/** Neue Position in GUI-Pixeln setzen (wird in relative Werte umgerechnet). */
	public void setPosition(MinecraftClient mc, float x, float y, int screenWidth, int screenHeight) {
		float freeX = Math.max(1, screenWidth - scaledWidth(mc));
		float freeY = Math.max(1, screenHeight - scaledHeight(mc));
		relX = Math.max(0, Math.min(1, x / freeX));
		relY = Math.max(0, Math.min(1, y / freeY));
	}

	public void resetPosition() {
		relX = null;
		relY = null;
		scale.reset();
	}

	/** Mit Position und Skalierung zeichnen. */
	public void renderAt(MinecraftClient mc, int screenWidth, int screenHeight) {
		Draw.push();
		Draw.translate(x(mc, screenWidth), y(mc, screenHeight));
		Draw.scale(scale());
		render(mc);
		Draw.pop();
	}

	@Override
	public JsonObject write() {
		JsonObject json = super.write();
		if (relX != null) json.addProperty("x", relX);
		if (relY != null) json.addProperty("y", relY);
		return json;
	}

	@Override
	public void read(JsonObject json) {
		super.read(json);
		if (json.has("x")) relX = Math.max(0, Math.min(1, json.get("x").getAsFloat()));
		if (json.has("y")) relY = Math.max(0, Math.min(1, json.get("y").getAsFloat()));
	}
}
