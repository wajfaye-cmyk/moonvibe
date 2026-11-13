package net.moonvibe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
//#if MC < 1.13
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//#endif

/**
 * Spiel-Ereignisse (Tick, Start, Beenden). Bis 1.12 liefert sie die Legacy-Fabric-API; für 1.13 gibt es
 * keine – dort ruft ClientEventsMixin die fire-Methoden auf.
 */
public final class Events {
	private static final List<Consumer<MinecraftClient>> TICK = new ArrayList<Consumer<MinecraftClient>>();
	private static final List<Runnable> STARTED = new ArrayList<Runnable>();
	private static final List<Runnable> STOPPING = new ArrayList<Runnable>();

	private Events() {}

	/** Am Ende jedes Spiel-Ticks (20× pro Sekunde) */
	public static void onTick(Consumer<MinecraftClient> listener) {
		//#if MC >= 1.13
		//$$ TICK.add(listener);
		//#else
		ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
		//#endif
	}

	/** Wenn das Spiel fertig gestartet ist */
	public static void onStarted(Runnable listener) {
		//#if MC >= 1.13
		//$$ STARTED.add(listener);
		//#else
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> listener.run());
		//#endif
	}

	/** Beim Beenden des Spiels */
	public static void onStopping(Runnable listener) {
		//#if MC >= 1.13
		//$$ STOPPING.add(listener);
		//#else
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> listener.run());
		//#endif
	}

	public static void fireTick(MinecraftClient client) {
		for (Consumer<MinecraftClient> l : TICK) l.accept(client);
	}

	public static void fireStarted() {
		for (Runnable l : STARTED) l.run();
	}

	public static void fireStopping() {
		for (Runnable l : STOPPING) l.run();
	}

}
