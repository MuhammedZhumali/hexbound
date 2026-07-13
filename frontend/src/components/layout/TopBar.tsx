import type { Game } from '../../types/game';
import { phaseLabel, type GuidanceLevel } from '../guidance/Guidance';

export function TopBar({
  game,
  activePlayerName,
  connection,
  guidanceLevel,
  onGuidanceChange,
  onOpenRules,
  onResetMap,
}: {
  game: Game;
  activePlayerName?: string;
  connection: string;
  guidanceLevel: GuidanceLevel;
  onGuidanceChange: (level: GuidanceLevel) => void;
  onOpenRules: () => void;
  onResetMap: () => void;
}) {
  const bestGlory = Math.max(0, ...game.players.map((player) => player.glory ?? 0));
  return (
    <header className="game-header top-bar">
      <button className="help-button" onClick={onOpenRules} aria-label="Open rulebook">
        ?
      </button>
      <div className="brand">
        HEXBOUND <i>REALMS</i>
      </div>
      <span className={`phase-badge phase-${game.phase.toLowerCase()}`}>
        {phaseLabel(game.phase)}
      </span>
      <span className="header-meta">Round {game.roundNumber}</span>
      <span className="header-meta">
        {activePlayerName ? `Waiting for ${activePlayerName}` : 'No active player'}
      </span>
      {game.lastRoll && <span className="header-meta">Dice {game.lastRoll}</span>}
      <button className="victory-pill" onClick={onOpenRules}>
        Glory {bestGlory}/10
      </button>
      <label className="guidance-select">
        Guidance
        <select
          value={guidanceLevel}
          onChange={(event) => onGuidanceChange(event.target.value as GuidanceLevel)}
        >
          <option value="BEGINNER">Beginner</option>
          <option value="NORMAL">Normal</option>
          <option value="EXPERT">Expert</option>
        </select>
      </label>
      <button className="quiet-button" onClick={onResetMap}>
        Reset Map
      </button>
      <span className={`status ${connection}`}>{connectionLabel(connection)}</span>
    </header>
  );
}

function connectionLabel(connection: string) {
  return (
    {
      connected: 'Connected',
      reconnecting: 'Reconnecting',
      offline: 'Offline',
      connecting: 'Connecting',
    }[connection] ?? connection
  );
}
