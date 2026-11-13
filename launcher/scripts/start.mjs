// Startet den Launcher im Entwicklermodus.
// VS Code setzt ELECTRON_RUN_AS_NODE=1 in seinen Terminals – dann würde Electron nur als
// normales Node laufen. Darum starten wir Electron hier ohne diese Variable.
import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';

const electron = createRequire(import.meta.url)('electron');
const env = { ...process.env };
delete env.ELECTRON_RUN_AS_NODE;

// optional anderes Einstiegsskript (z. B. scripts/thumbnails.cjs)
const child = spawn(electron, [process.argv[2] ?? '.'], { stdio: 'inherit', env });
child.on('exit', (code) => process.exit(code ?? 0));
