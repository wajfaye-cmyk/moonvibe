package net.moonvibe.ui;

import net.moonvibe.Mv;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;

/** Einfaches Suchfeld in der moonvibe-Schrift (Minecraft 1.8.9 hat keins im passenden Stil). */
public final class SearchField {
	private final String hint;
	private final Consumer<String> responder;
	private String value = "";
	private boolean focused = true;
	public float x;
	public float y;
	public float w;

	public SearchField(String hint, Consumer<String> responder) {
		this.hint = hint;
		this.responder = responder;
	}

	public String value() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void place(float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public void render() {
		boolean empty = value.isEmpty();
		Draw.Txt text = empty ? Draw.ui(hint) : Draw.ellipsizeStart(Draw.ui(value), (int) w - 6);
		Draw.text(text, x, y, empty ? Theme.MUTED : Theme.TEXT);
		// blinkender Cursor
		if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
			float cx = x + (empty ? 0 : Draw.width(text)) + 1;
			Draw.fill(cx, y - 1, cx + 1, y + 9, Theme.TEXT);
		}
	}

	public boolean clicked(int mouseX, int mouseY) {
		focused = mouseX >= x - 6 && mouseX < x + w && mouseY >= y - 6 && mouseY < y + 14;
		return focused;
	}

	/** Tastatur: true, wenn die Taste verwendet wurde. */
	public boolean key(char character, int code) {
		if (!focused) return false;
		if (code == Mv.KEY_BACKSPACE) {
			if (!value.isEmpty()) {
				value = Screen.hasControlDown() ? "" : value.substring(0, value.length() - 1);
				responder.accept(value);
			}
			return true;
		}
		if (Screen.isPaste(code)) {
			value += Mv.clipboard().replaceAll("[\\r\\n]", "");
			responder.accept(value);
			return true;
		}
		if (character >= 32 && character != 127 && value.length() < 40) {
			value += character;
			responder.accept(value);
			return true;
		}
		return false;
	}
}
