package net.moonvibe.ui;

import net.moonvibe.Moonvibe;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
//#if MC >= 26.3
import java.nio.ByteBuffer;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;
//#elseif MC >= 1.15
//$$ import org.lwjgl.PointerBuffer;
//$$ import org.lwjgl.system.MemoryStack;
//$$ import org.lwjgl.util.tinyfd.TinyFileDialogs;
//#else
//$$ import java.awt.FileDialog;
//$$ import java.awt.Frame;
//$$ import java.io.File;
//$$ import java.util.Locale;
//#endif

/** Dateiauswahl des Betriebssystems (ab 26.3 über SDL, davor über "tiny file dialogs" – beides bringt Minecraft mit). */
public final class FilePicker {
	//#if MC >= 26.3
	private static @Nullable SDL_DialogFileCallback pending;
	//#else
	//$$ private static volatile boolean open;
	//#endif

	private FilePicker() {}

	/**
	 * Öffnet "Datei öffnen" für Bilder. Das Ergebnis kommt auf dem Spiel-Thread an (null = abgebrochen).
	 * Muss auf dem Spiel-Thread aufgerufen werden.
	 */
	public static void openImage(String title, Consumer<@Nullable Path> result) {
		Minecraft mc = Minecraft.getInstance();
		//#if MC >= 26.3
		if (pending != null) return; // schon offen
		ByteBuffer name = MemoryUtil.memUTF8(title);
		ByteBuffer pattern = MemoryUtil.memUTF8("png;jpg;jpeg");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.get(0).set(name, pattern);
		SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, files, filter) -> {
			// files: Liste von C-Strings mit NULL am Ende (leer = abgebrochen, NULL = Fehler)
			String path = null;
			if (files != 0L) {
				long first = MemoryUtil.memGetAddress(files);
				if (first != 0L) path = MemoryUtil.memUTF8(first);
			}
			Path picked = path == null ? null : Path.of(path);
			mc.execute(() -> {
				free(filters, name, pattern);
				result.accept(picked);
			});
		});
		pending = callback;
		try {
			SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, mc.getWindow().handle(), filters, (CharSequence) null, false);
		} catch (Throwable e) {
			Moonvibe.LOGGER.warn("Dateiauswahl nicht verfügbar", e);
			free(filters, name, pattern);
			result.accept(null);
		}
		//#else
		//$$ if (open) return; // schon offen
		//$$ open = true;
		//$$ // der Dialog blockiert, bis man etwas gewählt hat – daher nicht auf dem Spiel-Thread
		//$$ Thread thread = new Thread(() -> {
		//$$ 	String path = null;
		//#if MC >= 1.15
		//$$ 	try (MemoryStack stack = MemoryStack.stackPush()) {
		//$$ 		PointerBuffer patterns = stack.mallocPointer(3);
		//$$ 		patterns.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).flip();
		//$$ 		path = TinyFileDialogs.tinyfd_openFileDialog(title, null, patterns, title, false);
		//$$ 	} catch (Throwable e) {
		//#else
		//$$ 	try {
		//$$ 		// 1.14 bringt "tiny file dialogs" noch nicht mit – dann den Dialog von Java selbst
		//$$ 		System.setProperty("java.awt.headless", "false");
		//$$ 		FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
		//$$ 		dialog.setFilenameFilter((dir, name) -> name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)$"));
		//$$ 		dialog.setFile("*.png;*.jpg;*.jpeg");
		//$$ 		dialog.setVisible(true);
		//$$ 		if (dialog.getFile() != null) path = new File(dialog.getDirectory(), dialog.getFile()).getPath();
		//$$ 		dialog.dispose();
		//$$ 	} catch (Throwable e) {
		//#endif
		//$$ 		Moonvibe.LOGGER.warn("Dateiauswahl nicht verfügbar", e);
		//$$ 	}
		//$$ 	Path picked = path == null ? null : Path.of(path);
		//$$ 	mc.execute(() -> {
		//$$ 		open = false;
		//$$ 		result.accept(picked);
		//$$ 	});
		//$$ }, "moonvibe Dateiauswahl");
		//$$ thread.setDaemon(true);
		//$$ thread.start();
		//#endif
	}

	//#if MC >= 26.3
	private static void free(SDL_DialogFileFilter.Buffer filters, ByteBuffer name, ByteBuffer pattern) {
		if (pending != null) pending.free();
		pending = null;
		filters.free();
		MemoryUtil.memFree(name);
		MemoryUtil.memFree(pattern);
	}
	//#endif
}
