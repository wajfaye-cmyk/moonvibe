// Übersetzungen des Launchers. Englisch ist Standard; fehlende Schlüssel in anderen Sprachen
// führen zu einem TypeScript-Fehler beim Bauen.
import de from './de';
import en from './en';
import es from './es';
import fr from './fr';
import ru from './ru';
import tr from './tr';

export type Lang = 'en' | 'de' | 'fr' | 'es' | 'tr' | 'ru';
export type I18nKey = keyof typeof en;
export type Dictionary = Record<I18nKey, string>;
export type I18nParams = Record<string, string | number>;

export const LANGUAGES: { id: Lang; name: string }[] = [
  { id: 'en', name: 'English' },
  { id: 'de', name: 'Deutsch' },
  { id: 'fr', name: 'Français' },
  { id: 'es', name: 'Español' },
  { id: 'tr', name: 'Türkçe' },
  { id: 'ru', name: 'Русский' },
];

/** Sprachcode für Minecraft (options.txt "lang:"). */
export const MINECRAFT_LANG: Record<Lang, string> = {
  en: 'en_us',
  de: 'de_de',
  fr: 'fr_fr',
  es: 'es_es',
  tr: 'tr_tr',
  ru: 'ru_ru',
};

/**
 * Sprachcode für die options.txt einer bestimmten Version: Vor 1.11 (November 2016) hießen die Codes
 * de_DE statt de_de – mit dem falschen Code fällt Minecraft still auf Englisch zurück.
 */
export function minecraftLang(lang: Lang, releaseTime: string): string {
  const code = MINECRAFT_LANG[lang];
  return releaseTime < '2016-11-14' ? code.replace(/_([a-z]+)$/, (_m, region: string) => `_${region.toUpperCase()}`) : code;
}

const DICTIONARIES: Record<Lang, Dictionary> = { en, de, fr, es, tr, ru };

export function isLang(value: unknown): value is Lang {
  return typeof value === 'string' && value in DICTIONARIES;
}

export function translate(lang: Lang, key: I18nKey, params: I18nParams = {}): string {
  const text: string = DICTIONARIES[lang]?.[key] ?? en[key] ?? key;
  return text.replace(/\{(\w+)\}/g, (match, name: string) => (name in params ? String(params[name]) : match));
}

/**
 * Fehler mit Übersetzungsschlüssel – der Kern wirft diese, der Hauptprozess übersetzt sie
 * in die eingestellte Sprache, bevor sie in der Oberfläche landen.
 */
export class LocalizedError extends Error {
  constructor(
    public readonly key: I18nKey,
    public readonly params: I18nParams = {},
  ) {
    super(translate('en', key, params));
  }
}
