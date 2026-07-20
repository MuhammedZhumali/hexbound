import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';

export function SetupPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('Hexbound Realms Lobby');
  const [seed, setSeed] = useState(123456);
  const [debug, setDebug] = useState(true);
  const [gameMode, setGameMode] = useState<'BEGINNER' | 'STANDARD'>('BEGINNER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function createLobby() {
    setBusy(true);
    setError('');
    try {
      const game = await api.create({ name, seed, maxPlayers: 4, debugMode: debug, gameMode });
      localStorage.setItem(`host:${game.id}`, 'true');
      navigate(`/games/${game.id}`);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="setup">
      <section className="setup-card">
        <p className="eyebrow">Lobby-based local multiplayer</p>
        <h1>
          Hexbound <i>Realms</i>
        </h1>
        <p className="setup-lead">
          Create a lobby, open the same game URL in up to four browser tabs, then each player joins
          with their own color and private profile.
        </p>

        <div className="setup-fields">
          <label>
            Lobby name
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label>
            Map seed
            <input
              type="number"
              value={seed}
              onChange={(event) => setSeed(Number(event.target.value))}
            />
          </label>
        </div>

        <div className="mode-picker" role="radiogroup" aria-label="Game mode">
          <button
            type="button"
            className={gameMode === 'BEGINNER' ? 'active' : ''}
            onClick={() => setGameMode('BEGINNER')}
          >
            <b>Beginner / Fast</b>
            <span>4 AP turns. Production triggers on rolled number and adjacent numbers.</span>
          </button>
          <button
            type="button"
            className={gameMode === 'STANDARD' ? 'active' : ''}
            onClick={() => setGameMode('STANDARD')}
          >
            <b>Standard</b>
            <span>3 AP turns. Normal production pace.</span>
          </button>
        </div>

        <label className="check">
          <input
            type="checkbox"
            checked={debug}
            onChange={(event) => setDebug(event.target.checked)}
          />
          Allow debug dice controls
        </label>

        {error && <p className="error">{error}</p>}
        <button className="primary large-button" disabled={busy} onClick={createLobby}>
          {busy ? 'Creating lobby…' : 'Create lobby'}
        </button>
      </section>
      <div className="setup-mark" aria-hidden="true">
        <b>4</b>
        <span>players</span>
        <small>one lobby · one private tab per player</small>
      </div>
    </main>
  );
}
