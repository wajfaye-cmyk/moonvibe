// Microsoft-Login → Xbox Live → XSTS → Minecraft-Token → Minecraft-Profil.
// Ablauf: https://minecraft.wiki/w/Microsoft_authentication
import { createHash, randomBytes } from 'node:crypto';
import { LocalizedError, type I18nKey } from '../shared/i18n';
import { fetchJson, HttpError, postJson } from './http';

export const REDIRECT_URI = 'https://login.microsoftonline.com/common/oauth2/nativeclient';
const AUTHORIZE_URL = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize';
const TOKEN_URL = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/token';
const SCOPE = 'XboxLive.signin offline_access';

export interface MicrosoftTokens {
  accessToken: string;
  refreshToken: string;
}

export interface MinecraftSession {
  accessToken: string;
  /** Millisekunden seit 1970 */
  expiresAt: number;
  uuid: string;
  name: string;
  xuid: string;
}

const base64url = (buf: Buffer) => buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

export function createPkce(): { verifier: string; challenge: string; state: string } {
  const verifier = base64url(randomBytes(32));
  const challenge = base64url(createHash('sha256').update(verifier).digest());
  return { verifier, challenge, state: base64url(randomBytes(16)) };
}

export function authorizeUrl(clientId: string, challenge: string, state: string): string {
  const params = new URLSearchParams({
    client_id: clientId,
    response_type: 'code',
    redirect_uri: REDIRECT_URI,
    scope: SCOPE,
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
    prompt: 'select_account',
  });
  return `${AUTHORIZE_URL}?${params}`;
}

async function tokenRequest(params: Record<string, string>): Promise<MicrosoftTokens> {
  const res = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params),
  });
  const json = (await res.json()) as { access_token?: string; refresh_token?: string; error_description?: string };
  if (!res.ok || !json.access_token || !json.refresh_token) {
    throw new LocalizedError('error.msLogin', { details: json.error_description ?? res.status });
  }
  return { accessToken: json.access_token, refreshToken: json.refresh_token };
}

export function exchangeCode(clientId: string, code: string, verifier: string): Promise<MicrosoftTokens> {
  return tokenRequest({
    client_id: clientId,
    grant_type: 'authorization_code',
    code,
    redirect_uri: REDIRECT_URI,
    code_verifier: verifier,
    scope: SCOPE,
  });
}

export function refreshMicrosoft(clientId: string, refreshToken: string): Promise<MicrosoftTokens> {
  return tokenRequest({ client_id: clientId, grant_type: 'refresh_token', refresh_token: refreshToken, scope: SCOPE });
}

/** Bekannte Xbox-Fehlercodes (XErr) mit eigener Erklärung. */
const XSTS_ERRORS = ['2148916233', '2148916235', '2148916236', '2148916237', '2148916238'];

/** Macht aus einem Microsoft-Token eine fertige Minecraft-Sitzung. */
export async function loginMinecraft(msAccessToken: string): Promise<MinecraftSession> {
  const xbl = await postJson<{ Token: string; DisplayClaims: { xui: { uhs: string }[] } }>(
    'https://user.auth.xboxlive.com/user/authenticate',
    {
      Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${msAccessToken}` },
      RelyingParty: 'http://auth.xboxlive.com',
      TokenType: 'JWT',
    },
  );

  let xsts: { Token: string; DisplayClaims: { xui: { uhs: string }[] } };
  try {
    xsts = await postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT',
    });
  } catch (e) {
    if (e instanceof HttpError && e.status === 401) {
      const code = String((JSON.parse(e.body || '{}') as { XErr?: number }).XErr ?? '');
      if (XSTS_ERRORS.includes(code)) throw new LocalizedError(`error.xsts.${code}` as I18nKey);
      throw new LocalizedError('error.xstsGeneric', { code });
    }
    throw e;
  }

  const uhs = xsts.DisplayClaims.xui[0].uhs;
  let mc: { access_token: string; expires_in: number };
  try {
    mc = await postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
      identityToken: `XBL3.0 x=${uhs};${xsts.Token}`,
    });
  } catch (e) {
    if (e instanceof HttpError && e.status === 403) {
      throw new LocalizedError('error.appNotApproved');
    }
    throw e;
  }

  let profile: { id: string; name: string };
  try {
    profile = await fetchJson('https://api.minecraftservices.com/minecraft/profile', {
      headers: { Authorization: `Bearer ${mc.access_token}` },
    });
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) {
      throw new LocalizedError('error.noMinecraft');
    }
    throw e;
  }

  // Die Xbox-User-ID steht im Minecraft-Token (JWT) selbst.
  let xuid = '0';
  try {
    const payload = JSON.parse(Buffer.from(mc.access_token.split('.')[1], 'base64url').toString('utf8')) as { xuid?: string };
    xuid = payload.xuid ?? '0';
  } catch {
    // ohne xuid geht es auch
  }

  return {
    accessToken: mc.access_token,
    expiresAt: Date.now() + mc.expires_in * 1000,
    uuid: profile.id,
    name: profile.name,
    xuid,
  };
}

/**
 * Meldet den Spieler mit einer Kennung bei Mojangs Session-Server an – genau wie beim Betreten eines Servers.
 * Der Server (hier: der moonvibe-Cosmetics-Server) kann danach bei Mojang nachfragen, wer das war,
 * ohne den Minecraft-Token selbst zu bekommen.
 */
export async function joinSession(accessToken: string, uuid: string, serverId: string): Promise<void> {
  const res = await fetch('https://sessionserver.mojang.com/session/minecraft/join', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accessToken, selectedProfile: uuid, serverId }),
  });
  if (!res.ok) throw new HttpError(res.status, 'sessionserver.mojang.com', await res.text().catch(() => ''));
}

/** Offline-UUID wie bei Minecraft selbst: UUID v3 aus "OfflinePlayer:<name>". */
export function offlineUuid(name: string): string {
  const hash = createHash('md5').update(`OfflinePlayer:${name}`).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  return hash.toString('hex');
}
