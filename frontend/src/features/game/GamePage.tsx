import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { subscribeGame } from '../../api/websocket';
import { Board } from '../../components/board/Board';
import { CombatPreview, MonsterEventModal } from '../../components/combat/CombatPanels';
import { RulesModal } from '../../components/dialogs/RulesModal';
import { PlayerPanel, RealmPanel } from '../../components/panels/Panels';
import { ActionButton } from '../../components/actions/ActionButton';
import { BankTradePanel, PendingTradesPanel } from '../../components/trade/TradePanels';
import { useUi } from '../../store/ui';
import { heroCardArt } from '../../assets/gameAssets';
import type {
  Coord,
  Game,
  HeroDraft,
  LegalAction,
  PrivateView,
  Resources,
  Seat,
  TargetType,
} from '../../types/game';
import type { GuidanceLevel } from '../../components/guidance/Guidance';

type TargetingMode = {
  actionType: string;
  targetType: TargetType;
  validTargetHexIds: string[];
  validTargetUnitIds: string[];
  instruction: string;
};

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

const ACTION_ORDER = ['EXPLORE', 'TRADE', 'BUILD', 'RECRUIT', 'FORTIFY', 'ATTACK'] as const;
type ActionId = (typeof ACTION_ORDER)[number];

const ACTION_COPY: Record<ActionId, { icon: string; title: string; text: string }> = {
  EXPLORE: {
    icon: '⌁',
    title: 'Explore',
    text: 'Scout a nearby hex. Explore card: first explore costs 0 AP.',
  },
  TRADE: {
    icon: '◇',
    title: 'Trade',
    text: 'Negotiate freely and use better bank rates. Trade card: bank trade is 3:1.',
  },
  BUILD: {
    icon: '⌂',
    title: 'Build',
    text: 'Roads and outposts are cheaper. Build card: first road costs 0 AP.',
  },
  RECRUIT: {
    icon: '♟',
    title: 'Recruit',
    text: 'Raise troops. Recruit card: first Militia costs 0 AP.',
  },
  FORTIFY: {
    icon: '⬟',
    title: 'Fortify',
    text: 'Gain defense tokens at the start of your turn.',
  },
  ATTACK: {
    icon: '⚔',
    title: 'Attack',
    text: 'Resolve one simple d20 attack. Attack card: first attack costs 1 AP.',
  },
};

const ACTION_DECK = ACTION_ORDER.map((id) => ({ id, ...ACTION_COPY[id] }));

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
  const [seats, setSeats] = useState<Seat[]>(() =>
    JSON.parse(localStorage.getItem(`seats:${id}`) ?? '[]'),
  );
  const [seat, setSeat] = useState<Seat | undefined>(() =>
    JSON.parse(localStorage.getItem(`seat:${id}`) ?? 'null') ?? undefined,
  );
  const [isHost] = useState(() => localStorage.getItem(`host:${id}`) === 'true');
  const [view, setView] = useState<PrivateView>();
  const [handover, setHandover] = useState<Seat>();
  const [joinName, setJoinName] = useState('');
  const [joinColor, setJoinColor] = useState('BLUE');
  const [rulesOpen, setRulesOpen] = useState(false);
  const [rolling, setRolling] = useState(false);
  const [rollingMode, setRollingMode] = useState<'world' | 'combat'>();
  const [error, setError] = useState('');
  const [invalidSelectionReason, setInvalidSelectionReason] = useState('');
  const [targetingMode, setTargetingMode] = useState<TargetingMode>();
  const [guidanceLevel, setGuidanceLevel] = useState<GuidanceLevel>(() => {
    const stored = localStorage.getItem('hexbound:guidance');
    return stored === 'NORMAL' || stored === 'EXPERT' ? stored : 'BEGINNER';
  });
  const { selected, reset, connection, setConnection } = useUi();

  useEffect(() => {
    localStorage.setItem('hexbound:guidance', guidanceLevel);
  }, [guidanceLevel]);

  useEffect(() => {
    if (!game || !seat) return;
    api.private(id, seat).then(setView).catch(() => {
      localStorage.removeItem(`seat:${id}`);
      setSeat(undefined);
      setView(undefined);
    });
  }, [game?.version, id, seat?.playerId]);

  useEffect(() => {
    if (game?.status !== 'LOBBY') return;
    const colors = ['BLUE', 'RED', 'GREEN', 'GOLD'];
    const taken = new Set(game.players.map((player) => player.color));
    if (taken.has(joinColor)) {
      setJoinColor(colors.find((color) => !taken.has(color)) ?? joinColor);
    }
  }, [game?.players, game?.status, joinColor]);

  const legalQuery = useQuery({
    queryKey: ['legal', id, seat?.playerId, game?.version],
    queryFn: () => api.legal(id, seat!.playerId),
    enabled: Boolean(seat && game),
  });
  const heroDraftQuery = useQuery({
    queryKey: ['hero-draft', id, game?.version],
    queryFn: () => api.heroDraft(id),
    enabled:
      game?.phase === 'HERO_SELECTION' ||
      game?.phase === 'HERO_DRAFT' ||
      game?.phase === 'HERO_REVEAL' ||
      game?.phase === 'STARTING_PLACEMENT',
  });

  useEffect(() => {
    if (
      (game?.phase === 'HERO_SELECTION' || game?.phase === 'HERO_DRAFT') &&
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
        window.setTimeout(() => {
          setRolling(false);
          setRollingMode(undefined);
        }, 800);
      } else if (isDiceCommand(variables.type)) {
        const previousCombat = game?.combatReport?.at(-1);
        const nextCombat = updatedGame.combatReport?.at(-1);
        const combatChanged =
          nextCombat && JSON.stringify(nextCombat) !== JSON.stringify(previousCombat);
        if (combatChanged) {
          setRolling(true);
          setRollingMode('combat');
          window.setTimeout(() => {
            setRolling(false);
            setRollingMode(undefined);
          }, 900);
        }
      }

      if (variables.type === 'SELECT_ACTION') {
        if (updatedGame.phase === 'RESOLUTION' || updatedGame.phase === 'ACTION_CARD_REVEAL') {
          if (seat) setView(await api.private(id, seat));
          return;
        }
        const nextSeat = seats.find((candidate) => {
          const player = updatedGame.players.find((item) => item.id === candidate.playerId);
          return player && !player.hasSelectedAction;
        });
        if (nextSeat && nextSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(nextSeat);
          return;
        }
        if (seat) setView(await api.private(id, seat));
        return;
      }

      if (variables.type === 'CONFIRM_HERO') {
        const draft = await api.heroDraft(id);
        queryClient.setQueryData(['hero-draft', id, updatedGame.version], draft);
        const nextSeat = seats.find(
          (candidate) => candidate.playerId === draft.currentDraftPlayerId,
        );
        if (nextSeat && nextSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(nextSeat);
          return;
        }
        if (seat) setView(await api.private(id, seat));
        return;
      }

      if (
        variables.type === 'START_STARTING_PLACEMENT' ||
        variables.type === 'PLACE_STARTING_OUTPOST' ||
        variables.type === 'PLACE_STARTING_ROAD'
      ) {
        const nextSeat = seats.find(
          (candidate) => candidate.playerId === updatedGame.currentStartingPlacementPlayerId,
        );
        if (nextSeat && nextSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(nextSeat);
          return;
        }
      }

      if (updatedGame.phase === 'WAITING_FOR_DEFENDER_REACTION' && updatedGame.pendingConflict) {
        const defenderSeat = seats.find(
          (candidate) => candidate.playerId === updatedGame.pendingConflict?.defenderPlayerId,
        );
        if (defenderSeat && defenderSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(defenderSeat);
          return;
        }
      }

      if (updatedGame.phase === 'PLAYER_TURNS' && updatedGame.currentTurnPlayerId) {
        const nextSeat = seats.find(
          (candidate) => candidate.playerId === updatedGame.currentTurnPlayerId,
        );
        if (nextSeat && nextSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(nextSeat);
          return;
        }
      }

      if (variables.type === 'LOCK_ATTACK_PLAN') {
        const nextAttacker = updatedGame.players.find(
          (player) =>
            player.revealedAction === 'ATTACK' &&
            player.hasSelectedAction &&
            !player.hasLockedAttackPlan,
        );
        const nextSeat = seats.find((candidate) => candidate.playerId === nextAttacker?.id);
        if (nextSeat && nextSeat.playerId !== seat?.playerId && seats.length > 1) {
          setView(undefined);
          setSeat(undefined);
          setHandover(nextSeat);
          return;
        }
        if (seat) setView(await api.private(id, seat));
        return;
      }

      if (seat) setView(await api.private(id, seat));
    },
    onError: (cause) => {
      setRolling(false);
      setRollingMode(undefined);
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

  async function joinLobby() {
    if (!game) return;
    try {
      setError('');
      const joined = await api.join(game.id, {
        displayName: joinName.trim() || `Player ${game.players.length + 1}`,
        playerColor: joinColor,
      });
      localStorage.setItem(`seat:${game.id}`, JSON.stringify(joined));
      const nextSeats = [...seats.filter((item) => item.playerId !== joined.playerId), joined];
      setSeats(nextSeats);
      localStorage.setItem(`seats:${game.id}`, JSON.stringify(nextSeats));
      setSeat(joined);
      setView(await api.private(game.id, joined));
      queryClient.invalidateQueries({ queryKey: ['game', id] });
    } catch (cause) {
      setError(readableError((cause as Error).message));
    }
  }

  async function enterAsGuest(playerId: string) {
    if (!game) return;
    const player = game.players.find((item) => item.id === playerId);
    if (!player) return;
    try {
      setError('');
      const recovered = await api.guestLogin(
        game.id,
        { playerColor: player.color },
        player.displayName,
      );
      localStorage.setItem(`seat:${game.id}`, JSON.stringify(recovered));
      const nextSeats = [...seats.filter((item) => item.playerId !== recovered.playerId), recovered];
      setSeats(nextSeats);
      localStorage.setItem(`seats:${game.id}`, JSON.stringify(nextSeats));
      setSeat(recovered);
      setView(await api.private(game.id, recovered));
    } catch (cause) {
      setError(readableError((cause as Error).message));
    }
  }

  async function startLobby() {
    if (!game) return;
    try {
      setError('');
      const updated = await api.start(game.id);
      queryClient.setQueryData(['game', id], updated);
    } catch (cause) {
      setError(readableError((cause as Error).message));
    }
  }

  function send(type: string, payload?: unknown) {
    if (type === 'ROLL_WORLD') {
      setRolling(true);
      setRollingMode('world');
    } else if (isDiceCommand(type)) {
      setRolling(false);
      setRollingMode(undefined);
    }
    setTargetingMode(undefined);
    mutation.mutate({ type, payload });
  }

  if (gameQuery.isLoading) return <main className="loading">Открываем долину…</main>;
  if (!game) return <main className="loading error">{(gameQuery.error as Error)?.message}</main>;

  if (game.status === 'LOBBY' && game.phase === 'SETUP') {
    const colors = ['BLUE', 'RED', 'GREEN', 'GOLD'];
    const takenColors = new Set(game.players.map((player) => player.color));
    const shareUrl = window.location.href;
    const canJoin = !seat && game.players.length < 4 && !takenColors.has(joinColor);
    return (
      <main className="setup lobby-page">
        <section className="setup-card lobby-card">
          <p className="eyebrow">Lobby</p>
          <h1>{game.name}</h1>
          <p className="setup-lead">
            Open this URL in up to four tabs. Each tab joins as one player and keeps its own private
            profile.
          </p>
          <div className="lobby-link">
            <input readOnly value={shareUrl} onFocus={(event) => event.currentTarget.select()} />
            <button className="quiet-button" onClick={() => navigator.clipboard?.writeText(shareUrl)}>
              Copy link
            </button>
          </div>
          <div className="lobby-mode">
            <b>{game.gameMode === 'BEGINNER' ? 'Beginner / Fast mode' : 'Standard mode'}</b>
            <span>
              {game.gameMode === 'BEGINNER'
                ? '4 AP per turn. Production triggers more often.'
                : '3 AP per turn. Normal production pace.'}
            </span>
          </div>
          <div className="setup-players">
            {colors.map((color) => {
              const player = game.players.find((item) => item.color === color);
              return (
                <span key={color} className={player ? '' : 'empty-seat'}>
                  <i className={`player-dot ${color.toLowerCase()}`} />
                  {player?.displayName ?? `${color} seat open`}
                  <small>{player ? 'Joined' : 'Waiting for player'}</small>
                </span>
              );
            })}
          </div>
          {!seat ? (
            <>
              <div className="join-form">
                <label>
                  Your name
                  <input
                    value={joinName}
                    onChange={(event) => setJoinName(event.target.value)}
                    placeholder={`Player ${game.players.length + 1}`}
                  />
                </label>
                <label>
                  Color
                  <select value={joinColor} onChange={(event) => setJoinColor(event.target.value)}>
                    {colors.map((color) => (
                      <option key={color} value={color} disabled={takenColors.has(color)}>
                        {color}
                      </option>
                    ))}
                  </select>
                </label>
                <button className="primary" disabled={!canJoin} onClick={joinLobby}>
                  Join as new guest
                </button>
              </div>
              {game.players.length > 0 && (
                <div className="guest-login-card">
                  <b>Already joined from another tab?</b>
                  <span>Choose your player to enter this lobby with a temporary guest token.</span>
                  <div className="guest-player-grid">
                    {game.players.map((player) => (
                      <button key={player.id} onClick={() => enterAsGuest(player.id)}>
                        <i className={`player-dot ${player.color.toLowerCase()}`} />
                        <b>{player.displayName}</b>
                        <small>{player.color}</small>
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </>
          ) : (
            <p className="lobby-you">
              You joined as <b>{seat.displayName}</b>. Keep this tab for your private profile.
            </p>
          )}
          {error && <p className="error">{error}</p>}
          {isHost ? (
            <button
              className="primary large-button"
              disabled={game.players.length === 0}
              onClick={startLobby}
            >
              Start game with {game.players.length}/4 players
            </button>
          ) : (
            <p className="lobby-waiting-host">
              Waiting for the lobby host to start the game.
            </p>
          )}
        </section>
      </main>
    );
  }

  if (!seat) {
    const activeId =
      game.currentStartingPlacementPlayerId ??
      game.currentTurnPlayerId ??
      getNextResolver(game)?.id ??
      game.firstPlayerId;
    const active = game.players.find((player) => player.id === activeId);
    return (
      <main className="setup identity-page">
        <section className="setup-card lobby-card">
          <p className="eyebrow">Enter game</p>
          <h1>{game.name}</h1>
          <p className="setup-lead">
            This browser tab does not have a player token yet. Choose who you are for this tab.
            Current turn: <b>{active?.displayName ?? 'not selected yet'}</b>.
          </p>
          <div className="guest-login-card">
            <b>Enter as guest player</b>
            <span>Use one tab per player. The tab will receive a temporary token for this game.</span>
            <div className="guest-player-grid">
              {game.players.map((player) => (
                <button key={player.id} onClick={() => enterAsGuest(player.id)}>
                  <i className={`player-dot ${player.color.toLowerCase()}`} />
                  <b>{player.displayName}</b>
                  <small>{active?.id === player.id ? 'Current turn' : player.heroClass ?? player.color}</small>
                </button>
              ))}
            </div>
          </div>
          {error && <p className="error">{error}</p>}
        </section>
      </main>
    );
  }

  const current = game.players.find((player) => player.id === seat?.playerId);
  const nextResolver = getNextResolver(game);
  const activePlayer =
    game.players.find(
      (player) =>
        player.id ===
        (game.currentStartingPlacementPlayerId ??
          game.currentTurnPlayerId ??
          nextResolver?.id ??
          game.firstPlayerId),
    ) ?? current;
  const legalTargets = targetingMode
    ? targetingMode.validTargetHexIds
    : [
        ...(legalQuery.data?.buildTargets ?? []),
        ...(game.phase === 'STARTING_PLACEMENT' ? legalQuery.data?.movementTargets ?? [] : []),
        ...(game.phase === 'PLAYER_TURNS' || game.phase === 'RESOLUTION'
          ? legalQuery.data?.movementTargets ?? []
          : []),
      ];

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
        <span className={`phase-badge phase-${game.phase.toLowerCase()}`}>{game.phase.replaceAll('_', ' ')}</span>
        <span className="header-meta">{game.gameMode === 'BEGINNER' ? 'Beginner · 4 AP' : 'Standard · 3 AP'}</span>
        <span className="header-meta">Round {game.roundNumber}</span>
        <span className="header-meta">{current ? `You are ${current.displayName}` : 'Choose your player'}</span>
        <span className="header-meta">{activePlayer ? `Now: ${activePlayer.displayName}` : 'No active player'}</span>
        {game.lastRoll && <span className="header-meta">Dice {game.lastRoll}</span>}
        <button className="victory-pill" onClick={() => setRulesOpen(true)}>
          Glory {Math.max(0, ...game.players.map((player) => player.glory ?? 0))}/10
        </button>
        <label className="guidance-select">
          Guidance
          <select
            value={guidanceLevel}
            onChange={(event) => setGuidanceLevel(event.target.value as GuidanceLevel)}
          >
            <option value="BEGINNER">Beginner</option>
            <option value="NORMAL">Normal</option>
            <option value="EXPERT">Expert</option>
          </select>
        </label>
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

      <RealmPanel
        game={game}
        guidanceLevel={guidanceLevel}
        view={view}
        invalidSelectionReason={invalidSelectionReason}
      />
      <section className="map">
        <Board
          game={game}
          legal={legalTargets}
          overlayType={targetingMode?.actionType ?? targetingMode?.targetType}
          explainInvalid={(hex) => setInvalidSelectionReason(invalidHexReason(game, hex.terrain))}
          rolling={rolling}
          rollingMode={rollingMode}
        />
      </section>
      <PlayerPanel view={view} />

      <section className="controls">
        <CombatScene game={game} />
        <MonsterEventModal game={game} busy={mutation.isPending} onContinue={() => send('RESOLVE_ACTION')} />
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
                    if (seat?.playerId === candidate.playerId) return;
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
          <div className="empty-controls guest-login-panel">
            <b>Enter as guest</b>
            <span>Choose one existing player for this browser tab. The game will restore a temporary token for that player.</span>
            <div className="guest-player-grid">
              {game.players.map((player) => (
                <button key={player.id} onClick={() => enterAsGuest(player.id)}>
                  <i className={`player-dot ${player.color.toLowerCase()}`} />
                  <b>{player.displayName}</b>
                  <small>
                    {activePlayer?.id === player.id ? 'Current turn' : player.heroClass ?? 'Joined'}
                  </small>
                </button>
              ))}
            </div>
            <b>Выберите игрока</b>
            <span>Личная информация появится только после подтверждения передачи устройства.</span>
          </div>
        ) : (
          <PhaseControls
            game={game}
            view={view}
            selected={selected}
            legalActions={legalQuery.data?.actions}
            availableActions={legalQuery.data?.availableActions ?? []}
            legalBuildTargets={legalQuery.data?.buildTargets ?? []}
            legalMovementTargets={legalQuery.data?.movementTargets ?? []}
            targetingMode={targetingMode}
            setTargetingMode={setTargetingMode}
            heroDraft={heroDraftQuery.data}
            send={send}
            busy={mutation.isPending}
            guidanceLevel={guidanceLevel}
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
          const monsterAttack = entry.conflictType === 'MONSTER_ATTACK';
          const attackerName = monsterAttack ? (monster?.type ?? 'Monster') : (attacker?.displayName ?? 'Атакующий');
          const targetName =
            defender?.displayName ?? (monsterAttack ? 'Цель' : monster?.type) ?? (entry.monsterId ? 'Монстр' : 'Цель');
          return (
            <article key={`${entry.attackerId}-${index}`} className="combat-card">
              <div className={`fighter ${monsterAttack ? 'monster-side' : (attacker?.color.toLowerCase() ?? 'neutral')}`}>
                <span>{monsterAttack ? 'M' : heroInitial(attacker?.heroClass)}</span>
                <b>{attackerName}</b>
                <small>
                  {entry.source.q},{entry.source.r}
                </small>
              </div>
              <div className="combat-impact">
                <i>⚔</i>
                <strong>
                  {entry.attackTotal} / {entry.defenseTotal}
                </strong>
                <span>
                  {entry.defenseRoll
                    ? `d20: ${entry.roll} vs ${entry.defenseRoll}`
                    : `d20: ${entry.roll}`}
                </span>
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
                  ? `${attackerName} нанёс ${entry.damage} урона.`
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

export function AvailableActionsPanel({
  actions,
  activeCard,
  busy,
  currentName,
  offerType,
  requestType,
  selected,
  setOfferType,
  setRequestType,
  setTargetingMode,
  targetingMode,
  view,
  game,
  send,
}: {
  actions: LegalAction[];
  activeCard: string;
  busy: boolean;
  currentName?: string;
  offerType: keyof Resources;
  requestType: keyof Resources;
  selected?: Coord;
  setOfferType: (resource: keyof Resources) => void;
  setRequestType: (resource: keyof Resources) => void;
  setTargetingMode?: (mode?: TargetingMode) => void;
  targetingMode?: TargetingMode;
  view: PrivateView;
  game: Game;
  send: (type: string, payload?: unknown) => void;
}) {
  const selectedId = selected ? `${selected.q},${selected.r}` : '';
  const activeTargetAction = actions.find((action) => action.actionType === targetingMode?.actionType);
  const selectedValid = Boolean(
    activeTargetAction && selectedId && activeTargetAction.validTargetHexIds.includes(selectedId),
  );
  const primaryActions = actions.filter(
    (action) => action.actionType !== 'TRANSMUTE' && action.actionType !== 'BUY_MARKET_CARD',
  );
  const marketAction = actions.find((action) => action.actionType === 'BUY_MARKET_CARD');
  const grouped = groupActions(primaryActions);

  return (
    <div className="available-actions-panel">
      <header>
        <div>
          <p className="eyebrow">Available Actions</p>
          <h3>
            {currentName} Turn · {activeCard} card · Action Points: {view.basicActionPoints} /{' '}
            {game.gameMode === 'BEGINNER' ? 4 : 3}
          </h3>
        </div>
        {view.basicActionPoints === 0 && <span className="ap-empty">End Turn recommended</span>}
      </header>

      {targetingMode && activeTargetAction && (
        <section className="targeting-confirm">
          <div>
            <b>{targetingMode.instruction}</b>
            <span>
              {selectedValid
                ? `Selected target ${selectedId}. Confirm to resolve on the server.`
                : 'Valid targets are highlighted on the board.'}
            </span>
          </div>
          {isAttackAction(activeTargetAction.actionType) && (
            <AttackPreviewCard
              selected={selected}
              view={view}
            />
          )}
          <button disabled={busy || !selectedValid} onClick={() => sendAction(activeTargetAction)}>
            Confirm {activeTargetAction.label}
          </button>
          <button className="quiet-button" onClick={() => setTargetingMode?.(undefined)}>
            Cancel
          </button>
        </section>
      )}

      {actions.some((action) => action.actionType === 'TRANSMUTE') && (
        <section className="transmute-picker">
          <div>
            <b>Mage Transmute</b>
            <span>Choose exactly which resource becomes another one.</span>
          </div>
          <label>
            Give
            <select value={offerType} onChange={(event) => setOfferType(event.target.value as keyof Resources)}>
              {resourceOptions(view.resources)}
            </select>
          </label>
          <label>
            Receive
            <select value={requestType} onChange={(event) => setRequestType(event.target.value as keyof Resources)}>
              {resourceOptions()}
            </select>
          </label>
          <button
            className="primary"
            disabled={busy || offerType === requestType || view.resources[offerType] < 1}
            onClick={() => send('TRANSMUTE', { give: offerType.toUpperCase(), receive: requestType.toUpperCase() })}
          >
            Transmute
          </button>
        </section>
      )}

      {marketAction && game.market.length > 0 && (
        <section className="turn-market">
          <div className="turn-market-header">
            <div>
              <b>Market cards</b>
              <span>
                Buy a specific visible card
                {marketAction.apCost === 0 ? ' for 0 AP with your Trade card bonus.' : ` for ${marketAction.apCost} AP.`}
              </span>
            </div>
            {!marketAction.available && marketAction.disabledReason && (
              <em>{marketAction.disabledReason}</em>
            )}
          </div>
          <div className="market-cards compact-market">
            {game.market.map((card) => {
              const affordable = covers(view.resources, card.cost);
              return (
                <button
                  key={card.id}
                  className="market-card"
                  disabled={busy || !marketAction.available || !affordable}
                  onClick={() => send('BUY_MARKET_CARD', { cardId: card.id })}
                >
                  <small>{card.category}</small>
                  <b>{card.name}</b>
                  <p>{card.effect}</p>
                  <em>Cost: {card.cost.gold} gold · {marketAction.apCost} AP</em>
                  <span>{affordable ? `Buy this card` : `Need ${card.cost.gold} gold`}</span>
                </button>
              );
            })}
          </div>
        </section>
      )}

      <div className="action-groups">
        {Object.entries(grouped).map(([group, items]) => (
          <section className="action-group" key={group}>
            <h4>{group}</h4>
            <div className="action-list">
              {items.map((action) => (
                <button
                  key={action.actionType}
                  className={`available-action ${action.available ? '' : 'disabled'} ${action.actionType === 'CHALLENGE' ? 'passive' : ''}`}
                  disabled={busy || !action.available || action.actionType === 'CHALLENGE'}
                  onClick={() => {
                    if (action.actionType === 'CHALLENGE') return;
                    if (action.requiresTarget) {
                      setTargetingMode?.({
                        actionType: action.actionType,
                        targetType: action.targetType,
                        validTargetHexIds: action.validTargetHexIds,
                        validTargetUnitIds: action.validTargetUnitIds,
                        instruction: targetingInstruction(action),
                      });
                    } else {
                      sendAction(action);
                    }
                  }}
                >
                  <span>
                    <b>{action.label}</b>
                    <small>{action.description}</small>
                    {!action.available && action.disabledReason && (
                      <em>Disabled: {action.disabledReason}</em>
                    )}
                  </span>
                  <strong>
                    {action.actionType === 'CHALLENGE'
                      ? 'Passive'
                      : action.available
                        ? `${action.apCost} AP`
                        : 'Unavailable'}
                  </strong>
                  {resourceCostText(action) && <i>{resourceCostText(action)}</i>}
                </button>
              ))}
            </div>
          </section>
        ))}
      </div>
    </div>
  );

  function sendAction(action: LegalAction) {
    const target = selected;
    const tradePayload = { give: offerType.toUpperCase(), receive: requestType.toUpperCase() };
    switch (action.actionType) {
      case 'MOVE_HERO':
      case 'SWIFT_MOVE':
        send(action.actionType, { to: target });
        return;
      case 'BUILD_ROAD':
      case 'QUICK_ROAD': {
        const from = preferredRoadSource(view, target);
        send(action.actionType, { from, to: target });
        return;
      }
      case 'BUILD_OUTPOST':
        send('BUILD_OUTPOST', { at: target });
        return;
      case 'ASSIGN_FORTIFY_TOKEN':
        send('ASSIGN_FORTIFY_TOKEN', { target, amount: 1 });
        return;
      case 'EXPLORE_HEX':
      case 'DEEP_EXPLORE':
      case 'SMALL_RAID':
        send(action.actionType, { target });
        return;
      case 'FULL_ATTACK':
        send(action.actionType, { target });
        return;
      case 'PRIEST_HEAL':
      case 'PRIEST_BLESS':
      case 'PRIEST_SANCTUARY':
      case 'ARCANE_BOLT':
      case 'MAGE_WARD':
      case 'MAGE_REVEAL':
      case 'SCOUT':
      case 'REPAIR':
        send(action.actionType, { target });
        return;
      case 'RECRUIT':
        send('RECRUIT', { unitType: 'MILITIA' });
        return;
      case 'BANK_TRADE':
      case 'MARKET_DEAL':
      case 'TRANSMUTE':
        send(action.actionType, tradePayload);
        return;
      case 'BUY_MARKET_CARD': {
        const firstAffordable = game.market.find((card) => covers(view.resources, card.cost)) ?? game.market[0];
        if (firstAffordable) send('BUY_MARKET_CARD', { cardId: firstAffordable.id });
        return;
      }
      case 'BUY_FORTIFY_TOKEN':
        send('BUY_FORTIFY_TOKEN');
        return;
      case 'REST_UNIT': {
        const unitId = action.validTargetUnitIds[0];
        if (unitId) send('REST_UNIT', { unitId });
        return;
      }
      case 'FEED_TROOPS': {
        const unitIds = action.validTargetUnitIds.slice(0, 2);
        if (unitIds.length) send('FEED_TROOPS', { unitIds });
        return;
      }
      case 'END_PLAYER_TURN':
        send('END_PLAYER_TURN');
        return;
      default:
        send(action.actionType, target ? { target } : undefined);
    }
  }
}

export function PhaseControls({
  game,
  view,
  selected,
  legalActions,
  availableActions = [],
  legalBuildTargets,
  legalMovementTargets = [],
  targetingMode,
  setTargetingMode,
  heroDraft,
  send,
  busy,
  guidanceLevel = 'BEGINNER',
  currentName,
  canResolve,
  nextResolverName,
}: {
  game: Game;
  view?: PrivateView;
  selected?: Coord;
  legalActions?: string[];
  availableActions?: LegalAction[];
  legalBuildTargets: string[];
  legalMovementTargets?: string[];
  targetingMode?: TargetingMode;
  setTargetingMode?: (mode?: TargetingMode) => void;
  heroDraft?: HeroDraft;
  send: (type: string, payload?: unknown) => void;
  busy: boolean;
  guidanceLevel?: GuidanceLevel;
  currentName?: string;
  canResolve: boolean;
  nextResolverName?: string;
}) {
  const [draftAction, setDraftAction] = useState<string>();
  const [tradeTarget, setTradeTarget] = useState('');
  const [offerType, setOfferType] = useState<keyof Resources>('wood');
  const [requestType, setRequestType] = useState<keyof Resources>('food');
  const [offerAmount, setOfferAmount] = useState(1);
  const [requestAmount, setRequestAmount] = useState(1);
  const [offeredGold, setOfferedGold] = useState(0);
  const [requestedGold, setRequestedGold] = useState(0);
  const [fortifySpend, setFortifySpend] = useState(0);

  useEffect(() => setDraftAction(undefined), [view?.playerId, game.phase, view?.selectedAction]);
  useEffect(() => setFortifySpend(0), [view?.playerId, game.phase, game.pendingConflict?.conflictId]);

  if (!view) return null;

  const freeTradeEmpty = (): Resources => ({ wood: 0, food: 0, ore: 0, stone: 0, gold: 0 });
  const freeTradeOffered = freeTradeEmpty();
  const freeTradeRequested = freeTradeEmpty();
  freeTradeOffered[offerType] = offerAmount;
  freeTradeRequested[requestType] = requestAmount;
  const freeTradePending = (game.tradeProposals ?? []).filter((proposal) => proposal.status === 'PENDING');
  const freePlayerTradePanel = (
    <div className="resolution-card free-trade-card">
      <h3>Free player trade</h3>
      <p>Player-to-player offers are always free during player turns and do not spend AP.</p>
      <label>Trade with<select value={tradeTarget} onChange={(event) => setTradeTarget(event.target.value)}>
        <option value="">Choose player</option>
        {game.players.filter((player) => player.id !== view.playerId).map((player) =>
          <option key={player.id} value={player.id}>{player.displayName}</option>)}
      </select></label>
      <label>Offer<select value={offerType} onChange={(event) => setOfferType(event.target.value as keyof Resources)}>{resourceOptions()}</select>
        <input type="number" min="0" value={offerAmount} onChange={(event) => setOfferAmount(Math.max(0, Number(event.target.value)))} /></label>
      <label>Request<select value={requestType} onChange={(event) => setRequestType(event.target.value as keyof Resources)}>{resourceOptions()}</select>
        <input type="number" min="0" value={requestAmount} onChange={(event) => setRequestAmount(Math.max(0, Number(event.target.value)))} /></label>
      <label>Offer gold<input type="number" min="0" value={offeredGold} onChange={(event) => setOfferedGold(Math.max(0, Number(event.target.value)))} /></label>
      <label>Request gold<input type="number" min="0" value={requestedGold} onChange={(event) => setRequestedGold(Math.max(0, Number(event.target.value)))} /></label>
      <button className="primary" disabled={busy || !tradeTarget} onClick={() => send('PROPOSE_TRADE', {
        targetPlayerId: tradeTarget,
        offeredResources: freeTradeOffered,
        requestedResources: freeTradeRequested,
        offeredGold,
        requestedGold,
      })}>Propose free trade</button>
      <div className="pending-free-trades">
        <b>Pending proposals</b>
        {freeTradePending.length === 0 && <span>No proposals right now.</span>}
        {freeTradePending.map((proposal) => <div key={proposal.proposalId} className="phase-action-row">
          <span>{game.players.find((p) => p.id === proposal.proposerPlayerId)?.displayName} → {game.players.find((p) => p.id === proposal.targetPlayerId)?.displayName}</span>
          {proposal.targetPlayerId === view.playerId && <><button onClick={() => send('ACCEPT_TRADE', { proposalId: proposal.proposalId })}>Accept</button><button onClick={() => send('REJECT_TRADE', { proposalId: proposal.proposalId })}>Reject</button></>}
          {proposal.proposerPlayerId === view.playerId && <button onClick={() => send('CANCEL_TRADE', { proposalId: proposal.proposalId })}>Cancel</button>}
        </div>)}
      </div>
    </div>
  );

  if (game.phase === 'WAITING_FOR_DEFENDER_REACTION') {
    const conflict = game.pendingConflict;
    const attacker = game.players.find((player) => player.id === conflict?.attackerPlayerId);
    const defender = game.players.find((player) => player.id === conflict?.defenderPlayerId);
    const isDefender = conflict?.defenderPlayerId === view.playerId;
    if (!conflict) {
      return <div className="waiting-turn">Waiting for conflict data…</div>;
    }
    const targetKey = `${conflict.target.q},${conflict.target.r}`;
    const assignedFortify =
      (view.assignedFortifyTokens?.[targetKey] ?? 0) + (view.temporaryAssignedFortifyTokens?.[targetKey] ?? 0);
    const fortifyCap = conflict.attackType === 'SMALL_RAID' ? 1 : 2;
    const maxFortifySpend = Math.min(assignedFortify, fortifyCap);
    const reactionPayload = (reaction: string) => ({
      reaction,
      fortifyTokensToSpend: Math.min(fortifySpend, maxFortifySpend),
    });
    if (!isDefender) {
      return (
        <div className="waiting-turn defender-wait">
          <span>🛡</span>
          <div>
            <b>Defender reaction: {defender?.displayName ?? 'the defending player'}</b>
            <p>
              {attacker?.displayName ?? 'The attacker'} declared {conflict.attackType.replace('_', ' ')} at{' '}
              {conflict.target.q},{conflict.target.r}. The defender chooses privately in their own tab.
            </p>
          </div>
        </div>
      );
    }
    return (
      <div className="defender-reaction-panel">
        <p className="eyebrow">Private Defender Reaction</p>
        <h3>{defender?.displayName}, choose your response</h3>
        <p>
          {attacker?.displayName ?? 'Attacker'} declared {conflict.attackType.replace('_', ' ')} at{' '}
          {conflict.target.q},{conflict.target.r}. Pick one reaction; then the server will roll and reveal
          the conflict result.
        </p>
        <div className="fortify-spend-box">
          <b>Assigned Fortify on this settlement: {assignedFortify}</b>
          <label>
            Spend Fortify
            <select
              value={Math.min(fortifySpend, maxFortifySpend)}
              onChange={(event) => setFortifySpend(Number(event.target.value))}
              disabled={maxFortifySpend === 0}
            >
              {Array.from({ length: maxFortifySpend + 1 }, (_, value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <span>
            Max {fortifyCap} for {conflict.attackType.replace('_', ' ')}. Fortify only protects this targeted settlement.
          </span>
        </div>
        <div className="reaction-grid">
          <button disabled={busy} onClick={() => send('DEFENDER_REACTION', reactionPayload('SHIELD'))}>
            <b>Shield</b>
            <span>Reduce or block incoming damage/resource loss.</span>
          </button>
          <button disabled={busy} onClick={() => send('DEFENDER_REACTION', reactionPayload('COUNTERATTACK'))}>
            <b>Counterattack</b>
            <span>Riskier response; can punish a failed attack.</span>
          </button>
          <button disabled={busy} onClick={() => send('DEFENDER_REACTION', reactionPayload('EVACUATION'))}>
            <b>Evacuation</b>
            <span>Avoid the worst effect but lose a smaller guaranteed amount.</span>
          </button>
          <button disabled={busy} onClick={() => send('DEFENDER_REACTION', reactionPayload('NONE'))}>
            <b>No Reaction</b>
            <span>Accept the attack and conserve defenses.</span>
          </button>
        </div>
      </div>
    );
  }

  if (game.phase === 'PLAYER_TURNS' && game.currentTurnPlayerId && game.currentTurnPlayerId !== view.playerId) {
    const active = game.players.find((player) => player.id === game.currentTurnPlayerId);
    return (
      <div className="waiting-turn turn-wait">
        <span>⏳</span>
        <div>
          <b>Сейчас ход {active?.displayName ?? 'другого игрока'}</b>
          <p>Вы играете за {currentName ?? 'своего игрока'}. Действия появятся автоматически, когда ход перейдёт к вам.</p>
        </div>
      </div>
    );
  }

  if (game.phase === 'STARTING_PLACEMENT') {
    if (game.status !== 'ACTIVE') {
      return (
        <div className="phase-action-row">
          <div><b>Manual starting placement</b><span>Outposts go in turn order; Roads go in reverse order.</span></div>
          <button className="primary large-button" disabled={busy} onClick={() => send('START_STARTING_PLACEMENT')}>Begin placement</button>
        </div>
      );
    }
    const isTurn = game.currentStartingPlacementPlayerId === view.playerId;
    const selectedKey = selected && `${selected.q},${selected.r}`;
    const isOutpost = game.startingPlacementStep === 'OUTPOST';
    const legal = Boolean(selectedKey && (isOutpost
      ? legalBuildTargets.includes(selectedKey)
      : legalMovementTargets.includes(selectedKey)));
    const currentPlayer = game.players.find((player) => player.id === game.currentStartingPlacementPlayerId);
    return (
      <div className="phase-action-row">
        <div>
          <b>{isOutpost ? 'Place a starting Outpost' : 'Place a starting Road'}</b>
          <span>{isTurn ? 'Select a highlighted hex; the server validates it.' : `Waiting for ${currentPlayer?.displayName ?? 'the current player'}.`}</span>
        </div>
        <button className="primary large-button" disabled={busy || !isTurn || !legal}
          onClick={() => send(isOutpost ? 'PLACE_STARTING_OUTPOST' : 'PLACE_STARTING_ROAD', isOutpost ? { at: selected } : { to: selected })}>
          Confirm {isOutpost ? 'Outpost' : 'Road'}
        </button>
      </div>
    );
  }

  if (game.phase === 'NEGOTIATION' || game.phase === 'TRADE_NEGOTIATION') {
    const empty = (): Resources => ({ wood: 0, food: 0, ore: 0, stone: 0, gold: 0 });
    const offered = empty();
    const requested = empty();
    offered[offerType] = offerAmount;
    requested[requestType] = requestAmount;
    const pending = (game.tradeProposals ?? []).filter((proposal) => proposal.status === 'PENDING');
    return (
      <div className="planning-layout">
        <div className="resolution-card">
          <h3>Open Trade &amp; Negotiation</h3>
          <label>Trade with<select value={tradeTarget} onChange={(event) => setTradeTarget(event.target.value)}>
            <option value="">Choose player</option>
            {game.players.filter((player) => player.id !== view.playerId).map((player) =>
              <option key={player.id} value={player.id}>{player.displayName}</option>)}
          </select></label>
          <label>Offer<select value={offerType} onChange={(event) => setOfferType(event.target.value as keyof Resources)}>{resourceOptions()}</select>
            <input type="number" min="0" value={offerAmount} onChange={(event) => setOfferAmount(Math.max(0, Number(event.target.value)))} /></label>
          <label>Request<select value={requestType} onChange={(event) => setRequestType(event.target.value as keyof Resources)}>{resourceOptions()}</select>
            <input type="number" min="0" value={requestAmount} onChange={(event) => setRequestAmount(Math.max(0, Number(event.target.value)))} /></label>
          <label>Offer gold<input type="number" min="0" value={offeredGold} onChange={(event) => setOfferedGold(Math.max(0, Number(event.target.value)))} /></label>
          <label>Request gold<input type="number" min="0" value={requestedGold} onChange={(event) => setRequestedGold(Math.max(0, Number(event.target.value)))} /></label>
          <button className="primary" disabled={busy || !tradeTarget} onClick={() => send('PROPOSE_TRADE', {
            targetPlayerId: tradeTarget, offeredResources: offered, requestedResources: requested, offeredGold, requestedGold,
          })}>Propose trade</button>
        </div>
        <div className="resolution-card">
          <h3>Pending proposals</h3>
          {pending.length === 0 && <p>No proposals yet.</p>}
          {pending.map((proposal) => <div key={proposal.proposalId} className="phase-action-row">
            <span>{game.players.find((p) => p.id === proposal.proposerPlayerId)?.displayName} → {game.players.find((p) => p.id === proposal.targetPlayerId)?.displayName}</span>
            {proposal.targetPlayerId === view.playerId && <><button onClick={() => send('ACCEPT_TRADE', { proposalId: proposal.proposalId })}>Accept</button><button onClick={() => send('REJECT_TRADE', { proposalId: proposal.proposalId })}>Reject</button></>}
            {proposal.proposerPlayerId === view.playerId && <button onClick={() => send('CANCEL_TRADE', { proposalId: proposal.proposalId })}>Cancel</button>}
          </div>)}
          <PendingTradesPanel game={game} playerId={view.playerId} busy={busy} send={send} />
          {guidanceLevel === 'BEGINNER' && (
            <BankTradePanel
              rate="Bank fallback: 4 resources for 1"
              tip="Player trades are usually better, but the bank is always safe."
            />
          )}
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>Close negotiations</button>
        </div>
      </div>
    );
  }

  switch (game.phase) {
    case 'HERO_SELECTION':
    case 'HERO_DRAFT':
      return (
        <HeroDraftControls game={game} view={view} draft={heroDraft} busy={busy} send={send} />
      );

    case 'HERO_REVEAL':
      return (
        <div className="phase-action-row">
          <div>
            <b>Heroes revealed</b>
            <span>All heroes are public. Begin manual starting placement.</span>
          </div>
          <button
            className="primary large-button"
            disabled={busy}
            onClick={() => send('START_STARTING_PLACEMENT')}
          >
            Begin placement
          </button>
        </div>
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

    case 'WORLD_ROLL':
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

    case 'PRODUCTION':
      return (
        <div className="phase-action-row">
          <div>
            <b>Production resolved</b>
            <span>Matching settlements produced resources. Continue to negotiation.</span>
          </div>
          <button
            className="primary large-button"
            disabled={busy}
            onClick={() => send('RESOLVE_ACTION')}
          >
            Start negotiation
          </button>
        </div>
      );

    case 'MONSTER_EVENT':
      return (
        <div className="phase-action-row warning-row">
          <div>
            <b>В долине появился монстр</b>
            <span>
              Он блокирует ближайшее производство, но не атакует сразу при появлении. Монстры
              атакуют в конце раунда и усиливаются каждый второй раунд.
            </span>
          </div>
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>
            Продолжить
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
                  <em>Cost: {card.cost.gold} gold</em>
                  <span>{affordable ? `Buy for ${card.cost.gold} gold` : `Need ${card.cost.gold} gold`}</span>
                </button>
              );
            })}
          </div>
          <button className="primary" disabled={busy} onClick={() => send('RESOLVE_ACTION')}>
            К планированию
          </button>
        </div>
      );

    case 'PLAYER_TURNS':
    case 'ACTION_CARD_SELECTION':
    case 'PLANNING': {
      if (game.phase === 'PLAYER_TURNS') {
        const activeCard = view.selectedAction ?? 'No card';
        return (
          <div className="planning-layout">
            <AvailableActionsPanel
              actions={availableActions}
              activeCard={activeCard}
              busy={busy}
              currentName={currentName}
              offerType={offerType}
              requestType={requestType}
              selected={selected}
              setOfferType={setOfferType}
              setRequestType={setRequestType}
              setTargetingMode={setTargetingMode}
              targetingMode={targetingMode}
              view={view}
              game={game}
              send={send}
            />
            {freePlayerTradePanel}
          </div>
        );
      }
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
            {ACTION_DECK.map((action) => {
              const coolingDown = action.id === view.previousAction;
              const allowed = legalActions ? legalActions.includes(action.id) : !coolingDown;
              const disabledReason = coolingDown
                ? 'This action card was used last round.'
                : !allowed
                  ? 'The backend says this action is not available right now.'
                  : undefined;
              return (
                <ActionButton
                  key={action.id}
                  icon={action.icon}
                  label={action.title}
                  summary={action.text}
                  cost={mainActionCost(action.id)}
                  selected={draftAction === action.id}
                  busy={busy}
                  disabledReason={disabledReason}
                  onClick={() => setDraftAction(action.id)}
                />
              );
            })}
          </div>
          <div className="planning-confirm">
            <span>
              {draftAction
                ? `Selected: ${ACTION_COPY[draftAction as ActionId]?.title ?? draftAction}`
                : 'Choose one secret action card'}
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

    case 'ACTION_CARD_REVEAL':
    case 'REVEAL':
      return (
        <div className="reveal-layout">
          <div className="revealed-actions">
            {game.players.map((player) => {
              const action =
                ACTION_COPY[player.revealedAction as ActionId] ??
                ACTIONS.find((item) => item.id === player.revealedAction);
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
          game={game}
          view={view}
          selected={selected}
          legalBuildTargets={legalBuildTargets}
          send={send}
          disabled={busy}
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
            <img className="hero-card-art" src={heroCardArt[hero.id]} alt="" />
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
  game,
  view,
  selected,
  legalBuildTargets,
  send,
  disabled,
}: {
  game: Game;
  view: PrivateView;
  selected?: Coord;
  legalBuildTargets: string[];
  send: (type: string, payload?: unknown) => void;
  disabled: boolean;
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
            const from = preferredRoadSource(view, selected);
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
    <>
    <CombatPreview game={game} view={view} />
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
    </>
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

function preferredRoadSource(view: PrivateView, target?: Coord) {
  if (!target) return view.settlements[0]?.location;
  const settlementSource = view.settlements
    .map((settlement) => settlement.location)
    .find((coordinate) => hexDistance(coordinate, target) === 1);
  if (settlementSource) return settlementSource;
  return (
    view.roads
      .flatMap((road) => [road.from, road.to])
      .find((coordinate) => hexDistance(coordinate, target) === 1) ?? view.settlements[0]?.location
  );
}

function hexDistance(a: Coord, b: Coord) {
  const ds = -a.q - a.r - (-b.q - b.r);
  return (Math.abs(a.q - b.q) + Math.abs(a.r - b.r) + Math.abs(ds)) / 2;
}

function mainActionCost(action: string) {
  const costs: Record<string, string> = {
    EXPLORE: 'Card bonus: first explore costs 0 AP',
    TRADE: 'Card bonus: bank trade improves to 3:1',
    BUILD: 'Card bonus: first road costs 0 AP',
    RECRUIT: 'Card bonus: first Militia costs 0 AP',
    FORTIFY: 'Card bonus: defense tokens at turn start',
    ATTACK: 'Card bonus: first attack costs 1 AP',
  };
  return costs[action] ?? '3 AP turn, backend validated';
}

function AttackPreviewCard({
  selected,
  view,
}: {
  selected?: Coord;
  view: PrivateView;
}) {
  const hero = view.hero;
  const heroBonus = hero?.heroClass === 'KNIGHT' ? 2 : hero?.heroClass === 'MAGE' ? 2 : 0;
  const readyUnits = view.units.filter((unit) => unit.fatigue !== 'EXHAUSTED' && !unit.garrison);
  const armyBonus = Math.min(6, readyUnits.length * 1);
  const cardBonus = view.selectedAction === 'ATTACK' ? 1 : 0;
  return (
    <aside className="attack-preview-card" aria-label="Attack Preview">
      <b>Attack Preview</b>
      <span>Target: {selected ? `${selected.q},${selected.r}` : 'choose a highlighted target'}</span>
      <span>Hero: {hero?.heroClass ?? 'none'} · Hero Bonus: +{heroBonus}</span>
      <span>Units: {readyUnits.length} ready · Army Bonus: +{armyBonus}</span>
      <span>Action Card Bonus: +{cardBonus}</span>
      <span>Base Defense: 10 · defender reaction stays hidden until resolution</span>
    </aside>
  );
}

function isAttackAction(actionType: string) {
  return actionType.includes('ATTACK') || actionType.includes('RAID') || actionType === 'ARCANE_BOLT';
}

function isDiceCommand(type: string) {
  return (
    type === 'ROLL_WORLD' ||
    type === 'SMALL_RAID' ||
    type === 'FULL_ATTACK' ||
    type === 'ARCANE_BOLT' ||
    type === 'DEFENDER_REACTION' ||
    type.startsWith('DEFENDER_REACTION_') ||
    type === 'RESOLVE_ATTACK_BATCH' ||
    type === 'END_ROUND'
  );
}

function groupActions(actions: LegalAction[]) {
  const order = ['Movement', 'Building', 'Trading', 'Exploration', 'Combat', 'Recovery', 'Hero Skills', 'Cards', 'Turn'];
  const grouped = Object.fromEntries(order.map((group) => [group, [] as LegalAction[]]));
  for (const action of actions) {
    grouped[actionGroup(action.actionType)].push(action);
  }
  return Object.fromEntries(Object.entries(grouped).filter(([, items]) => items.length > 0));
}

function actionGroup(actionType: string) {
  if (actionType.includes('MOVE')) return 'Movement';
  if (actionType.includes('BUILD') || actionType.includes('ROAD') || actionType.includes('FORTIFY') || actionType === 'REPAIR') return 'Building';
  if (actionType.includes('TRADE') || actionType.includes('DEAL') || actionType === 'TRANSMUTE') return 'Trading';
  if (actionType.includes('EXPLORE') || actionType === 'SCOUT' || actionType === 'MAGE_REVEAL') return 'Exploration';
  if (actionType.includes('ATTACK') || actionType.includes('RAID') || actionType === 'ARCANE_BOLT' || actionType === 'CHALLENGE') return 'Combat';
  if (actionType === 'REST_UNIT' || actionType === 'FEED_TROOPS') return 'Recovery';
  if (actionType.startsWith('PRIEST') || actionType.startsWith('MAGE') || actionType === 'SWIFT_MOVE' || actionType === 'QUICK_ROAD') return 'Hero Skills';
  if (actionType.includes('CARD')) return 'Cards';
  if (actionType.includes('END')) return 'Turn';
  return 'Cards';
}

function resourceCostText(action: LegalAction) {
  return Object.entries(action.resourceCost ?? {})
    .filter(([, amount]) => Number(amount) > 0)
    .map(([name, amount]) => `${amount} ${name}`)
    .join(' · ');
}

function targetingInstruction(action: LegalAction) {
  if (action.actionType.includes('ATTACK') || action.actionType.includes('RAID') || action.actionType === 'ARCANE_BOLT') {
    return `Choose a target to ${action.label}.`;
  }
  if (action.actionType.includes('HEAL') || action.actionType.includes('BLESS') || action.actionType.includes('WARD')) {
    return `Choose a friendly target for ${action.label}.`;
  }
  if (action.actionType.includes('BUILD') || action.actionType.includes('ROAD')) {
    return `Choose a build target for ${action.label}.`;
  }
  return `Choose a hex to ${action.label}.`;
}

function invalidHexReason(game: Game, terrain: string) {
  if (game.phase === 'STARTING_PLACEMENT') {
    if (terrain === 'RUIN' || terrain === 'MONSTER_LAIR' || terrain === 'ANCIENT_CAPITAL') {
      return 'You cannot place a starting Outpost on this feature hex.';
    }
    if (game.startingPlacementStep === 'ROAD') {
      return 'Starting Roads must extend from the Outpost placed by the current player.';
    }
    return 'This hex is locked because it is too close, occupied, blocked, or not a valid starting terrain.';
  }
  if (game.phase === 'PLAYER_TURNS' || game.phase === 'RESOLUTION') {
    return 'This hex is not currently listed by the backend as a legal target.';
  }
  return 'This hex is informational in the current phase.';
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
      HERO_SELECTION: 'Choose heroes secretly, then reveal together',
      HERO_REVEAL: 'Heroes are revealed; begin starting placement',
      HERO_DRAFT: 'Choose heroes',
      STARTING_PLACEMENT: 'Place starting outposts and roads manually',
      WORLD_ROLL: 'First player rolls 2d6',
      WORLD: 'First player rolls 2d6',
      PRODUCTION: 'Production resolved; continue to negotiation',
      MONSTER_EVENT: 'Review the monster event, then negotiate',
      NEGOTIATION: 'Negotiate player trades freely',
      TRADE_NEGOTIATION: 'Negotiate player trades freely',
      ACTION_CARD_SELECTION: 'Each player secretly chooses one action card',
      PLANNING: 'Each player secretly chooses one action card',
      ACTION_CARD_REVEAL: 'Cards are revealed; start 3 AP turns by initiative',
      REVEAL: 'Cards are revealed; start 3 AP turns by initiative',
      PLAYER_TURNS: 'Active player spends up to 3 AP',
      END_ROUND: 'Clean up and pass the first-player marker',
    }[phase] ?? 'Follow the current backend phase'
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
      'Choose an exploration target within hero range. Exploration can apply to more than Ruins.',
    INVALID_TARGET: 'You cannot use that target for this action right now.',
    INSUFFICIENT_RESOURCES: 'You do not have the resources required for that action.',
    NOT_YOUR_TURN: 'This decision belongs to another player right now.',
    'Not enough resources': 'You do not have the resources required for that action.',
  };
  return (
    translations[message] ??
    (message.includes('_')
      ? 'Something went wrong. Please try again or refresh the game state.'
      : message)
  );
}
