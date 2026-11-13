package net.moonvibe.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.moonvibe.Mv;
import java.util.List;

/** Auswahl aus festen Möglichkeiten (z. B. Fadenkreuz-Form). */
public class ChoiceSetting extends Setting<String> {
	public final List<String> choices;

	public ChoiceSetting(String id, String defaultValue, List<String> choices) {
		super(id, defaultValue);
		this.choices = choices;
	}

	public boolean is(String choice) {
		return value.equals(choice);
	}

	/** Übersetzter Name einer Möglichkeit: moonvibe.choice.<id> */
	public static String choiceLabel(String choice) {
		return Mv.tr("moonvibe.choice." + choice);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(value);
	}

	@Override
	public void read(JsonElement json) {
		if (json.isJsonPrimitive() && choices.contains(json.getAsString())) value = json.getAsString();
	}
}
