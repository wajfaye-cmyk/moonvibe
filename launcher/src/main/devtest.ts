// Nur für Entwickler: automatischer Oberflächen-Test mit Bildschirmfotos.
//   MOONVIBE_SCREENSHOTS=<Ordner>        → Bild jeder Seite speichern, dann beenden
//   zusätzlich MOONVIBE_TEST_CAPE=<Bild>    → Cape-Editor mit diesem Bild testen und speichern
//   zusätzlich MOONVIBE_TEST_PLAY=<Version> → Test-Account anlegen, "Spielen" drücken,
//                                            Minecraft kurz laufen lassen und wieder beenden
import { app, type BrowserWindow } from 'electron';
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';

const wait = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function runDevTest(win: BrowserWindow, dir: string): Promise<void> {
  const js = (code: string) => win.webContents.executeJavaScript(code);
  const shot = async (name: string) => writeFileSync(join(dir, `${name}.png`), (await win.webContents.capturePage()).toPNG());

  await new Promise<void>((r) => win.webContents.once('did-finish-load', () => r()));
  await wait(4000);

  const playVersion = process.env.MOONVIBE_TEST_PLAY;
  if (playVersion) {
    // Launcher offen lassen, sonst kann er keine Bildschirmfotos machen (am Ende wiederherstellen)
    const behavior = (await js('mv.getSettings()')).launcherBehavior;
    // die Versionsauswahl übernimmt nach dem Neuladen die zuletzt gespielte Version
    await js(`mv.saveSettings({ launcherBehavior: 'keep', lastVersion: ${JSON.stringify(playVersion)} })`);
    await js(`(async () => { const s = await mv.accounts(); if (!s.accounts.some(a => a.name === 'MoonTester')) await mv.addOffline('MoonTester'); })()`);
    win.webContents.reload();
    await new Promise<void>((r) => win.webContents.once('did-finish-load', () => r()));
    await wait(4000);
    await js(`document.getElementById('play-button').click()`);
    await wait(1500);
    await shot('play-starting');
    for (let i = 0; i < 120; i++) {
      if ((await js('mv.gameState()')).running) break;
      await wait(1000);
    }
    console.log('[devtest] Spiel läuft, Launcher sichtbar:', win.isVisible());
    await wait(30000);
    await js('mv.killGame()');
    await wait(4000);
    console.log('[devtest] Spiel beendet, Launcher sichtbar:', win.isVisible());
    await shot('play-after');
    await js(`mv.saveSettings({ launcherBehavior: ${JSON.stringify(behavior)} })`);
    app.quit();
    return;
  }

  // optional andere Sprache testen (MOONVIBE_TEST_LANG=de …), danach zurücksetzen
  const testLang = process.env.MOONVIBE_TEST_LANG;
  const previousLang = testLang ? (await js('mv.getSettings()')).language : null;
  if (testLang) {
    await js(`mv.saveSettings({ language: ${JSON.stringify(testLang)} })`);
    win.webContents.reload();
    await new Promise<void>((r) => win.webContents.once('did-finish-load', () => r()));
    await wait(4000);
  }
  const prefix = testLang ? `${testLang}-` : '';

  for (const page of ['home', 'servers', 'mods', 'cosmetics', 'settings']) {
    await js(`document.querySelector('[data-page="${page}"]').click()`);
    await wait(page === 'home' ? 500 : page === 'cosmetics' ? 6000 : 3500);
    await shot(prefix + page);
    if (page === 'home') {
      // Versionsliste aufklappen
      await js(`document.querySelector('#version-picker .dropdown-button').click()`);
      await wait(600);
      await shot(`${prefix}home-versions`);
      await js(`document.body.click()`);
    }
    if (page === 'mods') {
      // Kategorie "Optimierung" wählen
      await js(`document.querySelectorAll('#mods-categories .cat-chip')[1].click()`);
      await wait(3500);
      await shot(`${prefix}mods-category`);
      // Shader: auf der gewählten Version (Iris installieren) und auf 1.8.9 (gibt es dort nicht)
      await js(`document.querySelector('#mods-tabs [data-type="shader"]').click()`);
      await wait(3500);
      await shot(`${prefix}mods-shaders`);
      await js(`document.querySelector('#mods-version-picker .dropdown-button').click()`);
      await wait(400);
      await js(`[...document.querySelectorAll('#mods-version-picker .dropdown-item')].find((i) => i.textContent.startsWith('1.8.9')).click()`);
      await wait(3500);
      await shot(`${prefix}mods-shaders-old`);
      await js(`document.querySelector('#mods-tabs [data-type="mod"]').click()`);
    }
    if (page === 'cosmetics') {
      for (const slot of ['hat', 'wings']) {
        await js(`document.querySelector('[data-slot="${slot}"]').click()`);
        await wait(3500);
        await shot(`${prefix}cosmetics-${slot}`);
      }
      if (process.env.MOONVIBE_TEST_CAPE) {
        await js(`document.querySelector('[data-slot="cape"]').click()`);
        await wait(1500);
        await js(`document.querySelector('.cosmetic-card.upload').click()`);
        await wait(3500);
        await shot(`${prefix}cape-editor`);
        await js(`document.getElementById('cape-save').click()`);
        await wait(4000);
        await shot(`${prefix}cosmetics-custom`);
      }
    }
  }
  if (previousLang) await js(`mv.saveSettings({ language: ${JSON.stringify(previousLang)} })`);
  app.quit();
}
