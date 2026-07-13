import type { Game, HeroDraft, PrivateView, Seat } from '../types/game';
const base = import.meta.env.VITE_API_URL ?? '';
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(base + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(problem.detail ?? problem.title ?? 'Request failed');
  }
  return res.json();
}
export const api = {
  create: (body: { name: string; seed: number; maxPlayers: number; debugMode: boolean }) =>
    request<Game>('/api/v1/games', { method: 'POST', body: JSON.stringify(body) }),
  get: (id: string) => request<Game>(`/api/v1/games/${id}`),
  join: async (id: string, body: { displayName: string; playerColor: string }): Promise<Seat> => ({
    ...(await request<{ playerId: string; accessToken: string; version: number }>(
      `/api/v1/games/${id}/players`,
      { method: 'POST', body: JSON.stringify(body) },
    )),
    displayName: body.displayName,
  }),
  start: (id: string) => request<Game>(`/api/v1/games/${id}/start`, { method: 'POST' }),
  heroDraft: (id: string) => request<HeroDraft>(`/api/v1/games/${id}/hero-draft`),
  private: (id: string, seat: Seat) =>
    request<PrivateView>(`/api/v1/games/${id}/players/${seat.playerId}`, {
      headers: { 'X-Player-Token': seat.accessToken },
    }),
  legal: (id: string, playerId: string) =>
    request<{ actions: string[]; movementTargets: string[]; buildTargets: string[]; attackTargets: string[] }>(
      `/api/v1/games/${id}/legal-actions?playerId=${playerId}`,
    ),
  command: (game: Game, seat: Seat, type: string, payload: unknown = {}) =>
    request<Game>(`/api/v1/games/${game.id}/commands`, {
      method: 'POST',
      body: JSON.stringify({
        commandId: crypto.randomUUID(),
        playerId: seat.playerId,
        expectedVersion: game.version,
        type,
        payload,
      }),
    }),
};
