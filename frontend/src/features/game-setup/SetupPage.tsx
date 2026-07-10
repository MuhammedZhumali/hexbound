import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import type { Seat } from '../../types/game';

const players = [
  { displayName: 'Игрок 1', playerColor: 'BLUE' },
  { displayName: 'Игрок 2', playerColor: 'RED' },
  { displayName: 'Игрок 3', playerColor: 'GREEN' },
  { displayName: 'Игрок 4', playerColor: 'GOLD' },
];

export function SetupPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('Долина четырёх корон');
  const [seed, setSeed] = useState(123456);
  const [debug, setDebug] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function create() {
    setBusy(true);
    setError('');
    try {
      const game = await api.create({ name, seed, maxPlayers: 4, debugMode: debug });
      const seats: Seat[] = [];
      for (const player of players) {
        seats.push(await api.join(game.id, player));
      }
      localStorage.setItem(`seats:${game.id}`, JSON.stringify(seats));
      await api.start(game.id);
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
        <p className="eyebrow">Фэнтезийная стратегия для четырёх игроков</p>
        <h1>
          Hexbound <i>Realms</i>
        </h1>
        <p className="setup-lead">
          Развивайте владения, договаривайтесь с деревнями и сражайтесь с чудовищами. Все результаты
          рассчитывает сервер.
        </p>

        <div className="setup-fields">
          <label>
            Название мира
            <input value={name} onChange={(event) => setName(event.target.value)} />
          </label>
          <label>
            Seed карты
            <input
              type="number"
              value={seed}
              onChange={(event) => setSeed(Number(event.target.value))}
            />
          </label>
        </div>

        <div className="setup-players">
          {players.map((player) => (
            <span key={player.displayName}>
              <i className={`player-dot ${player.playerColor.toLowerCase()}`} />
              {player.displayName}
              <small>герой выбирается в драфте</small>
            </span>
          ))}
        </div>

        <label className="check">
          <input
            type="checkbox"
            checked={debug}
            onChange={(event) => setDebug(event.target.checked)}
          />
          Разрешить отладочные броски
        </label>

        {error && <p className="error">{error}</p>}
        <button className="primary large-button" disabled={busy} onClick={create}>
          {busy ? 'Создаём мир…' : 'Начать игру на 4 человека'}
        </button>
      </section>
      <div className="setup-mark" aria-hidden="true">
        <b>37</b>
        <span>земель</span>
        <small>одна долина · четыре героя</small>
      </div>
    </main>
  );
}
