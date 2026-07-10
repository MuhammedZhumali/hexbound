import type { Game, PrivateView } from '../../types/game';

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
  HERO_DRAFT: 'Драфт героев',
  STARTING_PLACEMENT: 'Стартовое размещение',
  WORLD: 'Фаза мира',
  MONSTER_EVENT: 'Нашествие',
  MARKET: 'Рынок',
  PLANNING: 'Тайное планирование',
  REVEAL: 'Раскрытие действий',
  RESOLUTION: 'Разыгрывание действий',
  END_ROUND: 'Конец раунда',
  FINAL_ROUND: 'Финальный раунд',
  GAME_OVER: 'Игра окончена',
};

export function RealmPanel({ game, rolling }: { game: Game; rolling: boolean }) {
  return (
    <aside className="panel realm">
      <p className="eyebrow">Состояние мира</p>
      <h2>{game.name}</h2>
      <div className="phase">{phaseNames[game.phase] ?? game.phase}</div>

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

      <DiceRoll value={game.lastRoll} rolling={rolling} />

      <h3>Последние события</h3>
      <ol className="log">
        {game.eventLog
          .slice(-6)
          .reverse()
          .map((event, index) => (
            <li key={`${event}-${index}`}>{event.replaceAll('_', ' ').toLowerCase()}</li>
          ))}
      </ol>
    </aside>
  );
}

function DiceRoll({ value, rolling }: { value?: number; rolling: boolean }) {
  const first = value ? Math.max(1, value - 6) : 1;
  const second = value ? value - first : 1;
  return (
    <section className={`dice-tray ${rolling ? 'rolling' : ''}`} aria-live="polite">
      <div className="dice-copy">
        <small>Бросок мира</small>
        <b>{rolling ? 'Кубики летят…' : value ? `Результат: ${value}` : 'Ещё не бросали'}</b>
      </div>
      <div className="dice-pair" aria-label={value ? `Выпало ${value}` : 'Кубики не брошены'}>
        <Die value={first} />
        <Die value={second} />
      </div>
    </section>
  );
}

function Die({ value }: { value: number }) {
  return (
    <div className={`die face-${value}`}>
      {Array.from({ length: value }, (_, index) => (
        <i key={index} />
      ))}
    </div>
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
      <h2>{heroNames[hero.heroClass] ?? hero.heroClass}</h2>
      <div className="hero-stats">
        <b>♥ {hero.hp}/3</b>
        {hero.heroClass === 'MAGE' && <b>✦ {hero.mana}/3 маны</b>}
        {hero.heroClass === 'PRIEST' && <b>☼ {hero.grace}/4 благодати</b>}
      </div>

      <h3>Ресурсы</h3>
      <div className="resources">
        {Object.entries(view.resources).map(([name, amount]) => (
          <span key={name}>
            <i>{resourceIcon(name)}</i>
            <b>{amount}</b>
            <small>{resourceName(name)}</small>
          </span>
        ))}
      </div>

      <dl>
        <dt>Репутация</dt>
        <dd>{view.reputation}</dd>
        <dt>Слава</dt>
        <dd>{Object.values(view.glory).reduce((sum, amount) => sum + amount, 0)} / 12</dd>
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
