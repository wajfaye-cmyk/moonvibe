-- Was jeder Spieler trägt (uuid ohne Bindestriche, je Platz eine Cosmetic-ID oder NULL)
CREATE TABLE players (
  uuid TEXT PRIMARY KEY,
  cape TEXT,
  hat TEXT,
  face TEXT,
  back TEXT,
  wings TEXT,
  pet TEXT,
  -- Prüfsumme des eigenen Capes (Bild liegt im KV-Speicher CAPES)
  custom_cape TEXT
);
CREATE INDEX players_custom_cape ON players (custom_cape);

-- moonvibe-Sitzungen, nur als sha256 gespeichert
CREATE TABLE sessions (
  hash TEXT PRIMARY KEY,
  uuid TEXT NOT NULL,
  until INTEGER NOT NULL
);
CREATE INDEX sessions_until ON sessions (until);

-- Zufallskennungen für Mojangs "join" (2 Minuten gültig, nur einmal nutzbar)
CREATE TABLE challenges (
  id TEXT PRIMARY KEY,
  until INTEGER NOT NULL
);
