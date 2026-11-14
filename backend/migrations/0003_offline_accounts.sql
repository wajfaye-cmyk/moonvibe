-- Username accounts (no Microsoft login): which installation reserved which name.
-- uuid = Minecraft's offline UUID of the name, key_hash = sha256 of "<uuid>:<secret of the installation>"
CREATE TABLE offline_accounts (
  uuid TEXT PRIMARY KEY,
  key_hash TEXT NOT NULL,
  created INTEGER NOT NULL
);
