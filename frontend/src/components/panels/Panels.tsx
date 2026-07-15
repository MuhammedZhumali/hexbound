import { useState } from 'react';
import type { Game, PrivateView } from '../../types/game';
import {
  ActionChecklist,
  BeginnerRecommendation,
  PhaseGuide,
  phaseLabel,
  type GuidanceLevel,
} from '../guidance/Guidance';
import { heroCardArt, resourceArt } from '../../assets/gameAssets';

const heroNames: Record<string, string> = {
  KNIGHT: 'Рыцарь',
  MERCHANT: 'Торговец',
  PRIEST: 'Жрец',
  RANGER: 'Следопыт',
  MAGE: 'Маг',
  ENGINEER: 'Инженер',
};

const phaseNames: Record<string, string> = {
  SETUP: 'Подготовка',
  HERO_SELECTION: 'Hero Selection',
  HERO_REVEAL: 'Hero Reveal',
  HERO_DRAFT: 'Драфт героев',
  STARTING_PLACEMENT: 'Стартовое размещение',
  WORLD_ROLL: 'World Roll',
  WORLD: 'Фаза мира',
  PRODUCTION: 'Production',
  NEGOTIATION: 'Negotiation',
  MONSTER_EVENT: 'Нашествие',
  MARKET: 'Рынок',
  ACTION_CARD_SELECTION: 'Action Card Selection',
  ACTION_CARD_REVEAL: 'Action Card Reveal',
  PLAYER_TURNS: 'Player Turns',
  PLANNING: 'Тайное планирование',
  REVEAL: 'Раскрытие действий',
  RESOLUTION: 'Разыгрывание действий',
  END_ROUND: 'Конец раунда',
  FINAL_ROUND: 'Финальный раунд',
  GAME_OVER: 'Игра окончена',
};

export function RealmPanel({
  game,
  guidanceLevel,
  view,
  invalidSelectionReason,
}: {
  game: Game;
  guidanceLevel: GuidanceLevel;
  view?: PrivateView;
  invalidSelectionReason?: string;
}) {
  return (
    <aside className="panel realm">
      <p className="eyebrow">Состояние мира</p>
      <h2>{game.name}</h2>
      <div className="phase">{phaseLabel(game.phase) || phaseNames[game.phase] || game.phase}</div>

      <div className="round-summary">
        <span>
          <small>Раунд</small>
          <b>{game.roundNumber}</b>
        </span>
        <span>
          <small>Ходит первым</small>
          <b>{game.players.find((p) => p.id === game.firstPlayerId)?.displayName ?? '—'}</b>
        </span>
      </div>

      <h3>Игроки</h3>
      <PhaseGuide
        game={game}
        guidanceLevel={guidanceLevel}
        invalidSelectionReason={invalidSelectionReason}
      />
      <ActionChecklist game={game} view={view} />
      <BeginnerRecommendation game={game} guidanceLevel={guidanceLevel} />

      <div className="player-roster">
        {game.players.map((player) => (
          <div className="roster-player" key={player.id}>
            <i className={`player-dot ${player.color.toLowerCase()}`} />
            <span>
              <b>{player.displayName}</b>
              <small>
                {player.heroClass
                  ? (heroNames[player.heroClass] ?? player.heroClass)
                  : 'герой не выбран'}
              </small>
            </span>
            {player.id === game.firstPlayerId && <strong title="Первый игрок">Ⅰ</strong>}
            {player.hasSelectedAction && <em title="Действие выбрано">✓</em>}
          </div>
        ))}
      </div>

      <h3>Журнал партии</h3>
      <EventLog events={game.eventLog} />
    </aside>
  );
}

function EventLog({ events }: { events: string[] }) {
  const [filter, setFilter] = useState('ALL');
  const filters = ['ALL', 'ECONOMY', 'BUILD', 'EXPLORE', 'COMBAT', 'TURN'];
  const keywords: Record<string, string[]> = {
    ECONOMY: ['gains', 'resource', 'Market Deal', 'transmuted', 'Roll'],
    BUILD: ['built', 'recruited', 'repaired'],
    EXPLORE: ['explored', 'scouted', 'revealed'],
    COMBAT: ['attacked', 'Raid', 'Arcane Bolt', 'Damage', 'monster'],
    TURN: ['Turn:', 'Round', 'finished'],
  };
  const filtered =
    filter === 'ALL'
      ? events
      : events.filter((event) => keywords[filter]?.some((word) => event.includes(word)));
  return (
    <>
      <div className="log-filters" aria-label="Event log filters">
        {filters.map((item) => (
          <button
            key={item}
            className={filter === item ? 'active' : ''}
            onClick={() => setFilter(item)}
          >
            {item === 'ALL' ? 'All' : item[0] + item.slice(1).toLowerCase()}
          </button>
        ))}
      </div>
      <ol className="log">
        {filtered.length === 0 ? (
          <li className="muted">No public events yet.</li>
        ) : (
          filtered
            .slice(-18)
            .reverse()
            .map((event, index) => <li key={`${event}-${index}`}>{event}</li>)
        )}
      </ol>
    </>
  );
}

export function PlayerPanel({ view }: { view?: PrivateView }) {
  if (!view) {
    return (
      <aside className="panel player empty-player-panel">
        <span className="empty-icon">◉</span>
        <h3>Личные данные скрыты</h3>
        <p>Выберите игрока и подтвердите передачу устройства.</p>
      </aside>
    );
  }

  const hero = view.hero;
  if (!hero) {
    return (
      <aside className="panel player hero-draft-private">
        <p className="eyebrow">Тайный выбор</p>
        <h2>{view.temporaryHeroClass ? heroNames[view.temporaryHeroClass] : 'Выберите героя'}</h2>
        <p>
          Временный выбор видите только вы. После подтверждения герой станет публичным и драфт
          перейдёт к следующему игроку.
        </p>
      </aside>
    );
  }
  return (
    <aside className="panel player">
      <p className="eyebrow">Ваш совет</p>
      <h2>
        {view.game?.players.find((player) => player.id === view.playerId)?.displayName ??
          'Current Player'}
      </h2>
      <section className="hero-summary-card">
        <p className="eyebrow">Hero</p>
        {heroCardArt[hero.heroClass] && (
          <img className="hero-summary-art" src={heroCardArt[hero.heroClass]} alt="" />
        )}
        <b>{heroNames[hero.heroClass] ?? hero.heroClass}</b>
        <span>
          Location: {hero.location ? `${hero.location.q}, ${hero.location.r}` : 'not placed yet'}
        </span>
        <span>Status: {hero.defeated ? 'Defeated' : 'Ready'}</span>
      </section>
      <div className="hero-stats">
        <b>♥ {hero.hp}/3</b>
        {hero.heroClass === 'MAGE' && <b>✦ {hero.mana}/3 маны</b>}
        {hero.heroClass === 'PRIEST' && <b>☼ {hero.grace}/4 благодати</b>}
      </div>

      <h3>Ресурсы</h3>
      <div className="resources">
        {Object.entries(view.resources).map(([name, amount]) => {
          const needed = actionCost(view.selectedAction)?.[name as keyof PrivateView['resources']];
          return (
            <span className={needed ? 'needed-resource' : ''} key={name}>
              <i>{resourceArt[name] ? <img src={resourceArt[name]} alt="" /> : resourceIcon(name)}</i>
              <b>{amount}</b>
              <small>{resourceName(name)}</small>
              {needed ? (
                <em>
                  {amount} / {needed}
                </em>
              ) : null}
            </span>
          );
        })}
      </div>

      <dl>
        <dt>Репутация</dt>
        <dd>{view.reputation}</dd>
        <dt>Слава</dt>
        <dd>{Object.values(view.glory).reduce((sum, amount) => sum + amount, 0)} / 10</dd>
        <dt>Укрепления</dt>
        <dd>{view.fortificationTokens}</dd>
      </dl>

      <h3>Печати пути</h3>
      <div className="chips">
        {view.activeSeals.length ? (
          view.activeSeals.map((seal) => <span key={seal}>{seal}</span>)
        ) : (
          <em>Пока нет активных печатей</em>
        )}
      </div>

      <h3>Карты руки</h3>
      <h3>Private discoveries</h3>
      <div className="private-discoveries">
        {view.privateExplorationResults?.length ? (
          view.privateExplorationResults
            .slice(-5)
            .reverse()
            .map((result) => (
              <article key={`${result.target.q},${result.target.r}-${result.type}`}>
                <small>
                  {result.target.q}, {result.target.r} · {result.type}
                </small>
                <span>{result.description}</span>
              </article>
            ))
        ) : (
          <em>No private discoveries yet</em>
        )}
      </div>

      <h3>Cards in hand</h3>
      <div className="hand-list">
        {view.hand.length ? (
          view.hand.map((card) => (
            <article key={card.id}>
              <small>{card.category}</small>
              <b>{card.name}</b>
              <span>{card.effect}</span>
            </article>
          ))
        ) : (
          <em>Карт пока нет</em>
        )}
      </div>

      <h3>Войска</h3>
      {view.units.map((unit) => (
        <p className="unit" key={unit.id}>
          {unit.type}
          <span>
            {unit.fatigue}
            {unit.wounded ? ' · ранен' : ''}
          </span>
        </p>
      ))}
    </aside>
  );
}

function resourceName(name: string) {
  const names: Record<string, string> = {
    wood: 'дерево',
    food: 'еда',
    ore: 'руда',
    stone: 'камень',
    gold: 'золото',
  };
  return names[name];
}

function resourceIcon(name: string) {
  const icons: Record<string, string> = {
    wood: '♣',
    food: '●',
    ore: '◆',
    stone: '⬟',
    gold: '◈',
  };
  return icons[name];
}

function actionCost(action?: string) {
  const none = { wood: 0, food: 0, ore: 0, stone: 0, gold: 0 };
  const costs: Record<string, PrivateView['resources']> = {
    BUILD: { ...none, wood: 1, stone: 1 },
    RECRUIT: { ...none, food: 1 },
    FORTIFY: { ...none, wood: 1, food: 1, ore: 1 },
    TRADE: none,
    EXPLORE: none,
    ATTACK: none,
  };
  return action ? costs[action] : undefined;
}
