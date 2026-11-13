package net.moonvibe.ui;

import net.moonvibe.Mv;
import net.moonvibe.Moonvibe;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.function.Consumer;

/** Dateiauswahl des Betriebssystems über Java (AWT) – Minecraft 1.8.9 bringt dafür nichts mit. */
public final class FilePicker {
	private static volatile boolean open;

	private FilePicker() {}

	/** Öffnet "Datei öffnen" für Bilder. Das Ergebnis kommt auf dem Spiel-Thread an (null = abgebrochen). */
	public static void openImage(String title, Consumer<File> result) {
		if (open) return;
		open = true;
		// der Dialog blockiert, bis man etwas gewählt hat – daher nicht auf dem Spiel-Thread
		Thread thread = new Thread(() -> {
			File picked = null;
			try {
				Frame frame = new Frame();
				frame.setAlwaysOnTop(true);
				FileDialog dialog = new FileDialog(frame, title, FileDialog.LOAD);
				dialog.setFilenameFilter((dir, name) -> name.toLowerCase().matches(".*\\.(png|jpe?g|gif|bmp)$"));
				dialog.setFile("*.png;*.jpg;*.jpeg;*.gif;*.bmp");
				dialog.setVisible(true);
				if (dialog.getFile() != null) picked = new File(dialog.getDirectory(), dialog.getFile());
				dialog.dispose();
				frame.dispose();
			} catch (Throwable e) {
				Moonvibe.LOGGER.warn("Dateiauswahl nicht verfügbar", e);
			}
			File file = picked;
			Mv.mc().submit(() -> {
				open = false;
				result.accept(file);
			});
		}, "moonvibe Dateiauswahl");
		thread.setDaemon(true);
		thread.start();
	}
}
