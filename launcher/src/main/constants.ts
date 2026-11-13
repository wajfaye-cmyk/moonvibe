/**
 * Azure-App (Client-ID) für den Microsoft-Login.
 * Anleitung zum Erstellen: README → "Microsoft-Login einrichten".
 * Zum Testen kann man sie auch per Umgebungsvariable MOONVIBE_MS_CLIENT_ID setzen.
 */
export const MS_CLIENT_ID = process.env.MOONVIBE_MS_CLIENT_ID ?? '';

/**
 * Standard-Cosmetics-Server des fertigen Launchers (eigener Cloudflare Worker, README → Tutorial 2).
 * Leer = keiner; Spieler können ihn auch in den Einstellungen eintragen.
 */
export const API_URL = '';

/** Cosmetics-Server auf dem eigenen PC (start-backend.bat) – nimmt der Launcher beim Entwickeln. */
export const LOCAL_API_URL = 'http://127.0.0.1:8777';
