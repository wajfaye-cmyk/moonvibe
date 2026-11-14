/**
 * Azure-App (Client-ID) für den Microsoft-Login.
 * Anleitung zum Erstellen: README → "Microsoft-Login einrichten".
 * Zum Testen kann man sie auch per Umgebungsvariable MOONVIBE_MS_CLIENT_ID setzen.
 */
export const MS_CLIENT_ID = process.env.MOONVIBE_MS_CLIENT_ID ?? '';

/**
 * Shared cosmetics server of the finished launcher (your own Cloudflare Worker, README → Tutorial 2).
 * Empty = none: the launcher then uses its built-in cosmetics server on this PC, so cosmetics work
 * right after installing (other players only see them once a shared server is entered here).
 * Players can also enter a server address in the settings.
 */
export const API_URL = '';
