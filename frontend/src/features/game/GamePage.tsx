import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { subscribeGame } from '../../api/websocket';
import { Board } from '../../components/board/Board';
import { RulesModal } from '../../components/dialogs/RulesModal';
import { PlayerPanel, RealmPanel } from '../../components/panels/Panels';
import { useUi } from '../../store/ui';
import type { Coord, Game, HeroDraft, PrivateView, Resources, Seat } from '../../types/game';

const ACTIONS = [
  {
    id: 'FORTIFY',
    icon: '⬟',
    title: 'Укрепиться',
    text: 'Получить жетоны защиты и подготовить гарнизон.',
  },
  {
    id: 'TRADE',
    icon: '◇',
    title: 'Торговать',
    text: 'Обменять один доступный ресурс на другой.',
  },
  {
    id: 'RECRUIT',
    icon: '♟',
    title: 'Нанять',
    text: 'Добавить новый отряд в свою армию.',
  },
  {
    id: 'BUILD',
    icon: '⌂',
    title: 'Строить',
    text: 'Проложить дорогу или основать аванпост.',
  },
  {
    id: 'EXPLORE',
    icon: '⌁',
    title: 'Исследовать',
    text: 'Открыть древние руины рядом с героем.',
  },
  {
    id: 'ATTACK',
    icon: '⚔',
    title: 'Атаковать',
    text: 'Провести серверный бросок d20 против цели.',
  },
] as const;

const ACTION_ORDER = ACTIONS.map((action) => action.id);

const HEROES = [
  {
    id: 'KNIGHT',
    name: 'Рыцарь',
    role: 'Командир',
    strength: '+2 к атаке',
    weakness: 'Высокое содержание армий',
    resource: 'Нет',
  },
  {
    id: 'MERCHANT',
    name: 'Торговец',
    role: 'Экономика',
    strength: 'Дополнительное производство',
    weakness: '-1 в личном бою',
    resource: 'Нет',
  },
  {
    id: 'PRIEST',
    name: 'Жрец',
    role: 'Поддержка',
    strength: 'Лечение и благословения',
    weakness: 'Зависит от репутации',
    resource: 'Благодать 0–4',
  },
  {
    id: 'RANGER',
    name: 'Следопыт',
    role: 'Разведка',
    strength: 'Движение 2 и руины',
    weakness: 'Ограниченный бонус армии',
    resource: 'Нет',
  },
  {
    id: 'MAGE',
    name: 'Маг',
    role: 'Контроль',
    strength: 'Заклинания атаки и защиты',
    weakness: 'Слаб без маны',
    resource: 'Мана 0–3',
  },
  {
    id: 'ENGINEER',
    name: 'Инженер',
    role: 'Строительство',
    strength: 'Дешёвые дороги и стены',
    weakness: 'Нет бонуса атаки',
    resource: 'Нет',
  },
] as const;

export function GamePage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const gameQuery = useQuery({
    queryKey: ['game', id],
    queryFn: () => api.get(id),
    refetchInterval: 15000,
  });
  const game = gameQuery.data;
  const seats = useMemo<Seat[]>(
    () => JSON.parse(localStorage.getItem(`seats:${id}`) ?? '[]'),
    [id],
  );

  const [seat, setSeat] = useState<Seat>();
  const [view, setView] = useState<PrivateView>();
  const [handover, setHandover] = useState<Seat>();
  const [rulesOpen, setRulesOpen] = useState(false);
  const [rolling, setRolling] = useState(false);
  const [error, setError] = useState('');
  const [reaction, setReaction] = useState('SHIELD');
  const { selected, reset, connection, setConnection } = useUi();

  const legalQuery = useQuery({
    queryKey: ['legal', id, seat?.playerId, game?.version],
    queryFn: () => api.legal(id, seat!.playerId),
    enabled: Boolean(seat && game),
  });
  const heroDraftQuery = useQuery({
    queryKey: ['hero-draft', id, game?.version],
    queryFn: () => api.heroDraft(id),
    enabled: game?.phase === 'HERO_DRAFT' || game?.phase === 'STARTING_PLACEMENT',
  });

  useEffect(() => {
    if (
      game?.phase === 'HERO_DRAFT' &&
      heroDraftQuery.data?.currentDraftPlayerId &&
      !seat &&
      !handover
    ) {
      const draftingSeat = seats.find(
        (candidate) => candidate.playerId === heroDraftQuery.data?.currentDraftPlayerId,
      );
      if (draftingSeat) setHandover(draftingSeat);
    }
  }, [game?.phase, handover, heroDraftQuery.data?.currentDraftPlayerId, seat, seats]);

  useEffect(
    () =>
      subscribeGame(
        id,
        () => {
          queryClient.invalidateQueries({ queryKey: ['game', id] });
          if (seat) api.private(id, seat).then(setView);
        },
        setConnection,
      ),
    [id, queryClient, seat, setConnection],
  );

  const mutation = useMutation({
    mutationFn: ({ type, payload }: { type: string; payload?: unknown }) =>
      api.command(game!, seat!, type, payload),
    onSuccess: async (updatedGame, variables) => {
      queryClient.setQueryData(['game', id], updatedGame);
      setError('');

      if (variables.type === 'ROLL_WORLD') {
        window.setTimeout(() => setRolling(false), 800);
      }

      if (variables.type === 'SELECT_ACTION') {
        const nextSeat = seats.find((candidate) => {
          const player = updatedGame.players.find((item) => item.id === candidate.playerId);
          return player && !player.hasSelectedAction;
        });
        setView(undefined);
        setSeat(undefined);
        setHandover(nextSeat ?? seats[0]);
        return;
      }

      if (variables.type === 'CONFIRM_HERO') {
        const draft = await api.heroDraft(id);
        queryClient.setQueryData(['hero-draft', id, updatedGame.version], draft);
        const nextSeat = seats.find(
          (candidate) => candidate.playerId === draft.currentDraftPlayerId,
        );
        setView(undefined);
        setSeat(undefined);
        if (nextSeat) setHandover(nextSeat);
        return;
      }

      if (variables.type === 'LOCK_ATTACK_PLAN') {
        const nextAttacker = updatedGame.players.find(
          (player) =>
            player.revealedAction === 'ATTACK' &&
            player.hasSelectedAction &&
            !player.hasLockedAttackPlan,
        );
        const nextSeat = seats.find((candidate) => candidate.playerId === nextAttacker?.id);
        setView(undefined);
        setSeat(undefined);
        setHandover(
          nextSeat ??
            seats.find(
              (candidate) =>
                candidate.playerId ===
                updatedGame.players.find((p) => p.revealedAction === 'ATTACK')?.id,
            ),
        );
        return;
      }

      if (seat) setView(await api.private(id, seat));
    },
    onError: (cause) => {
      setRolling(false);
      setError(readableError((cause as Error).message));
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    },
  });

  async function reveal(nextSeat: Seat) {
    try {
      setError('');
      setHandover(undefined);
      setSeat(nextSeat);
      setView(await api.private(id, nextSeat));
    } catch (cause) {
      setError(readableError((cause as Error).message));
    }
  }

  function send(type: string, payload?: unknown) {
    if (type === 'ROLL_WORLD') setRolling(true);
    mutation.mutate({ type, payload });
  }

  if (gameQuery.isLoading) return <main className="loading">Открываем долину…</main>;
  if (!game) return <main className="loading error">{(gameQuery.error as Error)?.message}</main>;

  const current = game.players.find((player) => player.id === seat?.playerId);
  const nextResolver = getNextResolver(game);

  return (
    <main className="game">
      <header className="game-header">
        <button
          className="help-button"
          onClick={() => setRulesOpen(true)}
          aria-label="Правила игры"
        >
          ?
        </button>
        <div className="brand">
          HEXBOUND <i>REALMS</i>
        </div>
        <span className="header-meta">
          Мир <code>{game.id.slice(0, 8)}</code>
        </span>
        <span className="header-meta">
          Seed <code>{game.seed}</code>
        </span>
        <button className="quiet-button" onClick={reset}>
          Сбросить карту
        </button>
        <span className={`status ${connection}`}>● {connectionLabel(connection)}</span>
      </header>

      <RealmPanel game={game} rolling={rolling} />
      <section className="map">
        <Board game={game} legal={legalQuery.data?.buildTargets ?? []} />
      </section>
      <PlayerPanel view={view} />

      <section className="controls">
        <CombatScene game={game} />
        <div className="control-heading">
          <div>
            <p className="eyebrow">Управление ходом</p>
            <b>{phaseInstruction(game.phase)}</b>
          </div>
          <div className="seat-tabs">
            {seats.map((candidate) => {
              const player = game.players.find((item) => item.id === candidate.playerId);
              return (
                <button
                  key={candidate.playerId}
                  className={seat?.playerId === candidate.playerId ? 'active' : ''}
                  onClick={() => {
                    setView(undefined);
                    setSeat(undefined);
                    setHandover(candidate);
                  }}
                >
                  <i className={`player-dot ${player?.color.toLowerCase()}`} />
                  {candidate.displayName}
                  {player?.hasSelectedAction && <span>✓</span>}
                </button>
              );
            })}
          </div>
        </div>

        {error && <div className="error-banner">{error}</div>}

        {!seat ? (
          <div className="empty-controls">
            <b>Выберите игрока</b>
            <span>Личная информация появится только после подтверждения передачи устройства.</span>
          </div>
        ) : (
          <PhaseControls
            game={game}
            view={view}
            selected={selected}
            legalActions={legalQuery.data?.actions}
            legalBuildTargets={legalQuery.data?.buildTargets ?? []}
            heroDraft={heroDraftQuery.data}
            send={send}
            busy={mutation.isPending}
            reaction={reaction}
            setReaction={setReaction}
            currentName={current?.displayName}
            canResolve={!nextResolver || nextResolver.id === view?.playerId}
            nextResolverName={nextResolver?.displayName}
          />
        )}
      </section>

      {handover && (
        <div className="veil">
          <div>
            <div className="handover-icon">◎</div>
            <p>Передайте устройство</p>
            <h2>{handover.displayName}</h2>
            <p className="muted">Остальные игроки не должны видеть тайное действие и карты.</p>
            <button className="primary large-button" onClick={() => reveal(handover)}>
              Я — {handover.displayName}
            </button>
          </div>
        </div>
      )}

      {rulesOpen && <RulesModal onClose={() => setRulesOpen(false)} />}
    </main>
  );
}

function CombatScene({ game }: { game: Game }) {
  if (!game.combatReport?.length) return null;
  return (
    <section className="combat-scene" aria-label="Отчёт боя">
      <div className="combat-scene-heading">
        <p className="eyebrow">Отчёт боя</p>
        <b>Что произошло в атаке</b>
      </div>
      <div className="combat-rounds">
        {game.combatReport.map((entry, index) => {
          const attacker = game.players.find((player) => player.id === entry.attackerId);
          const defender = game.players.find((player) => player.id === entry.defenderId);
          const monster = game.monsters.find((item) => item.id === entry.monsterId);
          const targetName =
            defender?.displayName ?? monster?.type ?? (entry.monsterId ? 'Монстр' : 'Цель');
          return (
            <article key={`${entry.attackerId}-${index}`} className="combat-card">
              <div className={`fighter ${attacker?.color.toLowerCase() ?? 'neutral'}`}>
                <span>{heroInitial(attacker?.heroClass)}</span>
                <b>{attacker?.displayName ?? 'Атакующий'}</b>
                <small>
                  {entry.source.q},{entry.source.r}
                </small>
              </div>
              <div className="combat-impact">
                <i>⚔</i>
                <strong>
                  {entry.attackTotal} / {entry.defenseTotal}
                </strong>
                <span>d20: {entry.roll}</span>
                <em>{conflictLabel(entry.conflictType)}</em>
              </div>
              <div className={`fighter defender ${defender?.color.toLowerCase() ?? 'neutral'}`}>
                <span>{monster ? 'M' : heroInitial(defender?.heroClass)}</span>
                <b>{targetName}</b>
                <small>
                  {entry.target.q},{entry.target.r}
                </small>
              </div>
              <p>
                {entry.damage > 0
                  ? `${attacker?.displayName ?? 'Атакующий'} нанёс ${entry.damage} урона.`
                  : `${targetName} удержал позицию без урона.`}
                {entry.unitDamage > 0 && ` Отряды получили: ${entry.unitDamage}.`}
                {entry.settlementDamage > 0 &&
                  ` Поселение потеряло прочность: ${entry.settlementDamage}.`}
                {entry.monsterDamage > 0 && ` Монстр потерял HP: ${entry.monsterDamage}.`}
              </p>
            </article>
          );
        })}
      </div>
    </section>
  );
}

export function PhaseControls({
  game,
  view,
  selected,
  legalActions,
  legalBuildTargets,
  heroDraft,
  send,
  busy,
  reaction,
  setReaction,
  currentName,
  canResolve,
  nextResolverName,
}: {
  game: Game;
  view?: PrivateView;
  selected?: Coord;
  legalActions?: string[];
  legalBuildTargets: string[];
  heroDraft?: HeroDraft;
  send: (type: string, payload?: unknown) => void;
  busy: boolean;
  reaction: string;
  setReaction: (reaction: string) => void;
  currentName?: string;
  canResolve: boolean;
  nextResolverName?: string;
}) {
  const [draftAction, setDraftAction] = useState<string>();

  useEffect(() => setDraftAction(undefined), [view?.playerId, game.phase, view?.selectedAction]);

  if (!view) return null;

  switch (game.phase) {
    case 'HERO_DRAFT':
      return (
        <HeroDraftControls game={game} view={view} draft={heroDraft} busy={busy} send={send} />
      );

    case 'STARTING_PLACEMENT':
      return (
        <div className="phase-action-row">
          <div>
            <b>Все герои подтверждены</b>
            <span>
              Аванпосты будут размещены в начальном порядке, дороги — в обратном порядке змейки.
            </span>
          </div>
          <button
            className="primary large-button"
            disabled={busy}
            onClick={() => send('START_STARTING_PLACEMENT')}
          >
            Выполнить стартовое размещение
          </button>
        </div>
      );

    case 'WORLD':
      return (
        <div className="phase-action-row">
          <div>
            <b>
              {game.firstPlayerId === view.playerId
                ? `${currentName}, бросайте кубики`
                : 'Ожидаем первого игрока'}
            </b>
            <span>Бросок 2d6 определит производство или вызовет монстра при результате 7.</span>
          </div>
          <button
            disabled={busy || game.firstPlayerId !== view.playerId}
            className="primary large-button"
            onClick={() => send('ROLL_WORLD')}
          >
            Бросить 2d6
          </button>
          {game.debugMode && game.firstPlayerId === view.playerId && (
            <button onClick={() => send('DEBUG', { operation: 'FORCE_2D6', value: 7 })}>
              Следующий бросок: 7
            </button>
          )}
        </div>
      );

    case 'MONSTER_EVENT':
      return (
        <div className="phase-action-row warning-row">
          <div>
            <b>В долине появился монстр</b>
            <span>Он блокирует ближайшее производство и усиливается каждый раунд.</span>
          </div>
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>
            Перейти к рынку
          </button>
        </div>
      );

    case 'MARKET':
      return (
        <div className="market-layout">
          <div className="market-cards">
            {game.market.map((card) => {
              const affordable = covers(view.resources, card.cost);
              return (
                <button
                  key={card.id}
                  className="market-card"
                  disabled={busy || !affordable}
                  onClick={() => send('BUY_MARKET_CARD', { cardId: card.id })}
                >
                  <small>{card.category}</small>
                  <b>{card.name}</b>
                  <p>{card.effect}</p>
                  <em>{card.cost.gold} золота</em>
                  <span>{affordable ? 'Купить' : 'Не хватает золота'}</span>
                </button>
              );
            })}
          </div>
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>
            К планированию
          </button>
        </div>
      );

    case 'PLANNING': {
      if (view.selectedAction) {
        return (
          <div className="locked-action">
            <span>✓</span>
            <div>
              <b>Действие выбрано и скрыто</b>
              <p>
                Передайте устройство следующему игроку. Карта раскроется после выбора всех четырёх.
              </p>
            </div>
          </div>
        );
      }

      return (
        <div className="planning-layout">
          <div className="action-cards">
            {ACTIONS.map((action) => {
              const coolingDown = action.id === view.previousAction;
              const allowed = legalActions ? legalActions.includes(action.id) : !coolingDown;
              return (
                <button
                  className={`action-card ${draftAction === action.id ? 'selected' : ''}`}
                  key={action.id}
                  disabled={busy || !allowed}
                  onClick={() => setDraftAction(action.id)}
                >
                  <span>{action.icon}</span>
                  <b>{action.title}</b>
                  <small>{action.text}</small>
                  {coolingDown && <em>Перезарядка</em>}
                </button>
              );
            })}
          </div>
          <div className="planning-confirm">
            <span>
              {draftAction
                ? `Выбрано: ${ACTIONS.find((action) => action.id === draftAction)?.title}`
                : 'Сначала выберите одну карту'}
            </span>
            <button
              className="primary large-button"
              disabled={!draftAction || busy}
              onClick={() => send('SELECT_ACTION', { action: draftAction })}
            >
              Подтвердить тайное действие
            </button>
          </div>
        </div>
      );
    }

    case 'REVEAL':
      return (
        <div className="reveal-layout">
          <div className="revealed-actions">
            {game.players.map((player) => {
              const action = ACTIONS.find((item) => item.id === player.revealedAction);
              return (
                <span key={player.id}>
                  <i className={`player-dot ${player.color.toLowerCase()}`} />
                  <b>{player.displayName}</b>
                  <em>{action?.title ?? player.revealedAction}</em>
                </span>
              );
            })}
          </div>
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>
            Начать разыгрывание
          </button>
        </div>
      );

    case 'RESOLUTION':
      if (!canResolve) {
        return (
          <div className="waiting-turn">
            <span>⌛</span>
            <div>
              <b>Сейчас действует {nextResolverName}</b>
              <p>Передайте устройство этому игроку. Порядок карт определяется правилами.</p>
            </div>
          </div>
        );
      }
      return (
        <Resolution
          view={view}
          selected={selected}
          legalBuildTargets={legalBuildTargets}
          send={send}
          disabled={busy}
          reaction={reaction}
          setReaction={setReaction}
        />
      );

    case 'END_ROUND':
      return (
        <button className="primary large-button" disabled={busy} onClick={() => send('END_ROUND')}>
          Завершить раунд {game.roundNumber}
        </button>
      );

    case 'FINAL_ROUND':
      return (
        <button className="primary large-button" disabled={busy} onClick={() => send('END_ROUND')}>
          Завершить финальный раунд
        </button>
      );

    case 'GAME_OVER':
      return (
        <div className="victory-message">
          <span>✦</span>
          <div>
            <h2>Королевство связано</h2>
            <p>
              Победитель:{' '}
              {game.players
                .filter((player) => game.winners.includes(player.id))
                .map((player) => player.displayName)
                .join(' и ')}
            </p>
          </div>
        </div>
      );

    default:
      return <p>Ожидаем следующий этап…</p>;
  }
}

function HeroDraftControls({
  game,
  view,
  draft,
  busy,
  send,
}: {
  game: Game;
  view: PrivateView;
  draft?: HeroDraft;
  busy: boolean;
  send: (type: string, payload?: unknown) => void;
}) {
  if (!draft) return <p>Загружаем порядок драфта…</p>;
  const isCurrentPlayer = draft.currentDraftPlayerId === view.playerId;
  const temporary = view.temporaryHeroClass;
  return (
    <div className="hero-draft-layout">
      <div className="draft-order">
        <b>Обратный порядок драфта</b>
        {draft.draftOrder.map((playerId, index) => {
          const player = game.players.find((item) => item.id === playerId);
          const confirmed = draft.confirmedSelections[playerId];
          return (
            <span
              className={draft.currentDraftPlayerId === playerId ? 'current' : ''}
              key={playerId}
            >
              <i>{index + 1}</i>
              {player?.displayName}
              <small>
                {confirmed ? HEROES.find((hero) => hero.id === confirmed)?.name : 'ожидает'}
              </small>
            </span>
          );
        })}
      </div>
      <div className="hero-draft-cards">
        {HEROES.map((hero) => (
          <button
            key={hero.id}
            className={`hero-card ${temporary === hero.id ? 'selected' : ''}`}
            disabled={busy || !isCurrentPlayer}
            onClick={() => send('SELECT_HERO', { heroClass: hero.id })}
          >
            <div>
              <b>{hero.name}</b>
              <em>{hero.role}</em>
            </div>
            <small>
              <strong>Сила:</strong> {hero.strength}
            </small>
            <small>
              <strong>Слабость:</strong> {hero.weakness}
            </small>
            <small>
              <strong>Ресурс:</strong> {hero.resource}
            </small>
          </button>
        ))}
      </div>
      <div className="draft-confirm">
        <span>
          {isCurrentPlayer
            ? temporary
              ? `Временный выбор: ${HEROES.find((hero) => hero.id === temporary)?.name}`
              : 'Выберите героя — выбор пока видите только вы'
            : `Сейчас выбирает ${game.players.find((p) => p.id === draft.currentDraftPlayerId)?.displayName}`}
        </span>
        {isCurrentPlayer && (
          <>
            <button
              className="quiet-button"
              disabled={!temporary || busy}
              onClick={() => send('CANCEL_HERO_SELECTION')}
            >
              Сбросить
            </button>
            <button
              className="primary"
              disabled={!temporary || busy}
              onClick={() => send('CONFIRM_HERO')}
            >
              Подтвердить героя
            </button>
          </>
        )}
      </div>
    </div>
  );
}

function Resolution({
  view,
  selected,
  legalBuildTargets,
  send,
  disabled,
  reaction,
  setReaction,
}: {
  view: PrivateView;
  selected?: Coord;
  legalBuildTargets: string[];
  send: (type: string, payload?: unknown) => void;
  disabled: boolean;
  reaction: string;
  setReaction: (reaction: string) => void;
}) {
  const [give, setGive] = useState<keyof Resources>('wood');
  const [receive, setReceive] = useState<keyof Resources>('gold');
  const [attackUnitIds, setAttackUnitIds] = useState<string[]>([]);
  const [heroParticipates, setHeroParticipates] = useState(false);
  const [attackSource, setAttackSource] = useState(
    view.settlements[0]
      ? `${view.settlements[0].location.q},${view.settlements[0].location.r}`
      : '',
  );
  useEffect(() => {
    setAttackUnitIds([]);
    setHeroParticipates(false);
    const first = view.settlements[0]?.location;
    setAttackSource(first ? `${first.q},${first.r}` : '');
  }, [view.playerId]);
  const action = view.selectedAction;

  if (!action) return <p>Действие этого игрока уже разыграно.</p>;

  const pass = (
    <button className="quiet-button" disabled={disabled} onClick={() => send('RESOLVE_ACTION')}>
      Пропустить действие
    </button>
  );

  if (action === 'FORTIFY') {
    const affordable =
      view.resources.wood >= 1 && view.resources.ore >= 1 && view.resources.food >= 1;
    return (
      <div className="resolution-card">
        <div>
          <h3>⬟ Укрепление</h3>
          <p>Стоимость: 1 дерево, 1 руда и 1 еда. Вы получите жетоны защиты.</p>
        </div>
        <button
          className="primary"
          disabled={disabled || !affordable}
          onClick={() => send('FORTIFY')}
        >
          Укрепиться
        </button>
        {pass}
      </div>
    );
  }

  if (action === 'TRADE') {
    return (
      <div className="resolution-card">
        <div>
          <h3>◇ Обмен ресурсов</h3>
          <p>Выберите один свой ресурс и получите один ресурс другого типа.</p>
        </div>
        <label>
          Отдать
          <select value={give} onChange={(event) => setGive(event.target.value as keyof Resources)}>
            {resourceOptions(view.resources)}
          </select>
        </label>
        <label>
          Получить
          <select
            value={receive}
            onChange={(event) => setReceive(event.target.value as keyof Resources)}
          >
            {resourceOptions()}
          </select>
        </label>
        <button
          className="primary"
          disabled={disabled || give === receive || view.resources[give] < 1}
          onClick={() =>
            send('TRADE_RESOURCE', { give: give.toUpperCase(), receive: receive.toUpperCase() })
          }
        >
          Обменять
        </button>
        {pass}
      </div>
    );
  }

  if (action === 'RECRUIT') {
    const units = [
      { id: 'MILITIA', name: 'Ополчение', cost: '1 еда', affordable: view.resources.food >= 1 },
      {
        id: 'INFANTRY',
        name: 'Пехота',
        cost: '1 еда + 1 руда',
        affordable: view.resources.food >= 1 && view.resources.ore >= 1,
      },
      {
        id: 'ARCHER',
        name: 'Лучник',
        cost: '1 еда + 1 дерево',
        affordable: view.resources.food >= 1 && view.resources.wood >= 1,
      },
      {
        id: 'CAVALRY',
        name: 'Кавалерия',
        cost: '2 еды + 1 золото',
        affordable: view.resources.food >= 2 && view.resources.gold >= 1,
      },
      {
        id: 'MERCENARY',
        name: 'Наёмник',
        cost: '2 золота',
        affordable: view.resources.gold >= 2,
      },
    ];
    return (
      <div className="recruit-grid">
        {units.map((unit) => (
          <button
            key={unit.id}
            disabled={disabled || !unit.affordable}
            onClick={() => send('RECRUIT', { unitType: unit.id })}
          >
            <b>{unit.name}</b>
            <small>{unit.cost}</small>
          </button>
        ))}
        {pass}
      </div>
    );
  }

  if (action === 'BUILD') {
    const targetKey = selected && `${selected.q},${selected.r}`;
    const outpostLegal = Boolean(targetKey && legalBuildTargets.includes(targetKey));
    return (
      <div className="resolution-card">
        <div>
          <h3>⌂ Строительство</h3>
          <p>
            Выберите подсвеченную землю на карте. Сервер повторно проверит сеть дорог и стоимость.
          </p>
        </div>
        <button
          disabled={disabled || !outpostLegal}
          onClick={() => send('BUILD_OUTPOST', { at: selected })}
        >
          Построить аванпост
        </button>
        <button
          disabled={disabled || !selected}
          onClick={() => {
            const from =
              view.roads
                .flatMap((road) => [road.from, road.to])
                .find(
                  (coordinate) =>
                    Math.max(
                      Math.abs(coordinate.q - selected!.q),
                      Math.abs(coordinate.r - selected!.r),
                    ) <= 1,
                ) ?? view.settlements[0]?.location;
            send('BUILD_ROAD', { from, to: selected });
          }}
        >
          Проложить дорогу
        </button>
        {pass}
      </div>
    );
  }

  if (action === 'EXPLORE') {
    return (
      <div className="resolution-card">
        <div>
          <h3>⌁ Исследование</h3>
          <p>Выберите древние руины рядом с героем. Награда: 1 золото и 1 репутация.</p>
        </div>
        <button
          className="primary"
          disabled={disabled || !selected}
          onClick={() => send('EXPLORE', { target: selected })}
        >
          Исследовать выбранную землю
        </button>
        {pass}
      </div>
    );
  }

  if (view.attackPlan) {
    const allPlansRevealed = view.game.revealedAttackPlans.length > 0;
    return (
      <div className="resolution-card attack-plan-locked">
        <div>
          <h3>🔒 План атаки зафиксирован</h3>
          <p>
            {view.attackPlan.source.q},{view.attackPlan.source.r} → {view.attackPlan.target.q},
            {view.attackPlan.target.r}. Изменить состав после раскрытия других целей нельзя.
          </p>
        </div>
        {allPlansRevealed ? (
          <button
            className="danger"
            disabled={disabled}
            onClick={() => send('RESOLVE_ATTACK_BATCH')}
          >
            Одновременно разрешить все атаки
          </button>
        ) : (
          <span className="muted">Ожидаем планы остальных атакующих игроков…</span>
        )}
      </div>
    );
  }

  const [sourceQ, sourceR] = attackSource.split(',').map(Number);
  return (
    <div className="resolution-card">
      <div>
        <h3>⚔ Тайный план атаки</h3>
        <p>
          Выберите источник, цель и участников. Все цели раскроются только после фиксации планов.
        </p>
      </div>
      <label>
        Территория-источник
        <select value={attackSource} onChange={(event) => setAttackSource(event.target.value)}>
          {view.settlements.map((settlement) => (
            <option key={settlement.id} value={`${settlement.location.q},${settlement.location.r}`}>
              {settlement.level} ({settlement.location.q}, {settlement.location.r})
            </option>
          ))}
        </select>
      </label>
      <div className="attack-units">
        {view.units.map((unit) => (
          <label key={unit.id}>
            <input
              type="checkbox"
              checked={attackUnitIds.includes(unit.id)}
              disabled={unit.fatigue === 'EXHAUSTED'}
              onChange={(event) =>
                setAttackUnitIds((current) =>
                  event.target.checked
                    ? [...current, unit.id]
                    : current.filter((id) => id !== unit.id),
                )
              }
            />
            {unit.type} · {unit.fatigue}
          </label>
        ))}
        <label>
          <input
            type="checkbox"
            checked={heroParticipates}
            onChange={(event) => setHeroParticipates(event.target.checked)}
          />
          Герой участвует
        </label>
      </div>
      <button
        className="danger"
        disabled={disabled || !selected || (!attackUnitIds.length && !heroParticipates)}
        onClick={() =>
          send('LOCK_ATTACK_PLAN', {
            source: { q: sourceQ, r: sourceR },
            target: selected,
            participatingUnitIds: attackUnitIds,
            heroParticipates,
          })
        }
      >
        Зафиксировать план атаки
      </button>
    </div>
  );
}

function resourceOptions(resources?: Resources) {
  const names: Record<keyof Resources, string> = {
    wood: 'Дерево',
    food: 'Еда',
    ore: 'Руда',
    stone: 'Камень',
    gold: 'Золото',
  };
  return (Object.keys(names) as (keyof Resources)[]).map((resource) => (
    <option key={resource} value={resource} disabled={resources ? resources[resource] < 1 : false}>
      {names[resource]}
      {resources ? ` (${resources[resource]})` : ''}
    </option>
  ));
}

function covers(resources: Resources, cost: Resources) {
  return (
    resources.wood >= cost.wood &&
    resources.food >= cost.food &&
    resources.ore >= cost.ore &&
    resources.stone >= cost.stone &&
    resources.gold >= cost.gold
  );
}

function heroInitial(heroClass?: string) {
  const initials: Record<string, string> = {
    KNIGHT: 'K',
    MERCHANT: 'T',
    PRIEST: 'Ж',
    RANGER: 'С',
    MAGE: 'М',
    ENGINEER: 'И',
  };
  return initials[heroClass ?? ''] ?? '?';
}

function conflictLabel(type: string) {
  const labels: Record<string, string> = {
    ONE_WAY_ASSAULT: 'прямой удар',
    MULTI_ATTACK: 'общая цель',
    RECIPROCAL_CLASH: 'встречный бой',
    HERO_DUEL: 'дуэль героев',
    CHAIN_ATTACK: 'цепочка атак',
    CONTESTED_DESTINATION: 'спорная цель',
  };
  return labels[type] ?? type;
}

function getNextResolver(game: Game) {
  if (game.phase !== 'RESOLUTION') return undefined;
  const ordered = [...game.players]
    .filter((player) => player.hasSelectedAction && player.revealedAction)
    .sort((left, right) => {
      const actionDifference =
        ACTION_ORDER.indexOf(left.revealedAction as (typeof ACTION_ORDER)[number]) -
        ACTION_ORDER.indexOf(right.revealedAction as (typeof ACTION_ORDER)[number]);
      if (actionDifference) return actionDifference;
      const firstIndex = game.players.findIndex((player) => player.id === game.firstPlayerId);
      const leftIndex = game.players.findIndex((player) => player.id === left.id);
      const rightIndex = game.players.findIndex((player) => player.id === right.id);
      return (
        ((leftIndex - firstIndex + game.players.length) % game.players.length) -
        ((rightIndex - firstIndex + game.players.length) % game.players.length)
      );
    });
  if (ordered[0]?.revealedAction === 'ATTACK') {
    return (
      ordered.find((player) => player.revealedAction === 'ATTACK' && !player.hasLockedAttackPlan) ??
      ordered[0]
    );
  }
  return ordered[0];
}

function phaseInstruction(phase: string) {
  return (
    {
      HERO_DRAFT: 'Выберите героев в обратном порядке инициативы',
      STARTING_PLACEMENT: 'Подтвердите размещение змейкой',
      WORLD: 'Бросьте кубики и распределите производство',
      MONSTER_EVENT: 'Оцените новую угрозу',
      MARKET: 'Изучите пять предложений рынка',
      PLANNING: 'Каждый игрок тайно выбирает карту действия',
      REVEAL: 'Карты раскрыты — проверьте порядок',
      RESOLUTION: 'Разыграйте действия по очереди',
      END_ROUND: 'Восстановите силы и передайте жетон первого игрока',
    }[phase] ?? 'Следуйте текущей фазе'
  );
}

function connectionLabel(connection: string) {
  return (
    { connected: 'на связи', reconnecting: 'переподключение', offline: 'нет связи' }[connection] ??
    connection
  );
}

function readableError(message: string) {
  const translations: Record<string, string> = {
    'Choose a different action this round':
      'Эта карта использовалась в прошлом раунде — выберите другую.',
    'Action already locked': 'Действие уже подтверждено.',
    'At least one ready field unit is required':
      'Для атаки нужен хотя бы один готовый полевой отряд.',
    'Road must extend your connected network': 'Дорога должна продолжать вашу существующую сеть.',
    'Select an Ancient Ruin within hero range':
      'Выберите древние руины в радиусе перемещения героя.',
  };
  return translations[message] ?? message;
}
