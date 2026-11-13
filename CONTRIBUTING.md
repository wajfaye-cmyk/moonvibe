# Contributing

Thanks for helping! Bug reports, translations and pull requests are welcome.

## Before you open a pull request

- **Every listed version must keep working.** A change to `mod/` or `mod-legacy/` has to build and run on all
  17 versions (1.8.9 – 26.3), not just the one you play. Build all of them with `build-mod.bat` and start the ones
  your change touches with `node scripts/test-launch.cjs <version>` in `launcher` (see "For developers" in the README).
- Version differences go into `//#if MC >= …` blocks or `versions/<version>.renames`, not into copies of a class.
- New texts need all six languages: `launcher/src/shared/i18n/*.ts` for the launcher (the build fails if one is
  missing) and `mod/lang/translations.mjs` for the mod.
- No cheats or features that give an unfair advantage on servers (see "Fair play and legal" in the README).
- Never commit Minecraft files, other people's mods or your own tokens, IDs and server addresses.

## Reporting bugs

Open an issue and include the Minecraft version, what you did, what happened and, if the game crashed, the end of
the game console (launcher → Settings → Game console). Security problems: see [SECURITY.md](SECURITY.md).
