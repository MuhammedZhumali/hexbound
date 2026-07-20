import { useRef, useState } from 'react';
import type { Game, Hex } from '../../types/game';
import { useUi } from '../../store/ui';
import { heroTokenArt, monsterArt } from '../../assets/gameAssets';

const RADIUS = 48;
const HEIGHT = Math.sqrt(3) * RADIUS;

const terrainColors: Record<string, string> = {
  FOREST: '#4f7f5f',
  FIELD: '#b6a84c',
  MOUNTAIN: '#747b83',
  QUARRY: '#8f795f',
  TRADE_LAND: '#b0833d',
  VILLAGE: '#9c715c',
  RUIN: '#746491',
  MONSTER_LAIR: '#673947',
  ANCIENT_CAPITAL: '#c4943f',
};

const terrainNames: Record<string, string> = {
  FOREST: 'Лес',
  FIELD: 'Поля',
  MOUNTAIN: 'Горы',
  QUARRY: 'Каменоломня',
  TRADE_LAND: 'Торговые земли',
  VILLAGE: 'Нейтральная деревня',
  RUIN: 'Древние руины',
  MONSTER_LAIR: 'Логово монстров',
  ANCIENT_CAPITAL: 'Древняя столица',
};

const resourceNames: Record<string, string> = {
  WOOD: 'дерево',
  FOOD: 'еду',
  ORE: 'руду',
  STONE: 'камень',
  GOLD: 'золото',
};

const terrainIcons: Record<string, string> = {
  FOREST: '♣',
  FIELD: '✾',
  MOUNTAIN: '▲',
  QUARRY: '◆',
  TRADE_LAND: '¤',
  VILLAGE: '⌂',
  RUIN: '⌁',
  MONSTER_LAIR: '☠',
  ANCIENT_CAPITAL: '✦',
};

const center = (q: number, r: number) => ({
  x: 360 + RADIUS * 1.5 * q,
  y: 310 + HEIGHT * (r + q / 2),
});

const polygonPoints = (x: number, y: number) =>
  Array.from({ length: 6 }, (_, index) => {
    const angle = (Math.PI / 180) * (60 * index);
    return `${x + RADIUS * Math.cos(angle)},${y + RADIUS * Math.sin(angle)}`;
  }).join(' ');

export function Board({
  game,
  legal = [],
  overlayType,
  explainInvalid,
  rolling = false,
  rollingMode,
}: {
  game: Game;
  legal?: string[];
  overlayType?: string;
  explainInvalid?: (hex: Hex) => void;
  rolling?: boolean;
  rollingMode?: 'world' | 'combat';
}) {
  const { selected, select, zoom, pan, setZoom, setPan } = useUi();
  const drag = useRef<{ x: number; y: number; panX: number; panY: number } | undefined>(undefined);
  const [tooltip, setTooltip] = useState<Hex>();

  return (
    <div
      className="boardWrap"
      onWheel={(event) => {
        event.preventDefault();
        setZoom(Math.max(0.65, Math.min(2.2, zoom - event.deltaY * 0.001)));
      }}
      onPointerDown={(event) =>
        (drag.current = { x: event.clientX, y: event.clientY, panX: pan.x, panY: pan.y })
      }
      onPointerMove={(event) => {
        if (drag.current) {
          setPan({
            x: drag.current.panX + event.clientX - drag.current.x,
            y: drag.current.panY + event.clientY - drag.current.y,
          });
        }
      }}
      onPointerUp={() => (drag.current = undefined)}
      onPointerLeave={() => (drag.current = undefined)}
    >
      <svg viewBox="0 0 720 620" aria-label="Карта из 37 шестиугольных земель">
        <defs>
          <pattern
            id="legal"
            width="8"
            height="8"
            patternUnits="userSpaceOnUse"
            patternTransform="rotate(45)"
          >
            <line x1="0" y1="0" x2="0" y2="8" stroke="#b9f0d0" strokeWidth="3" />
          </pattern>
          <filter id="shadow">
            <feDropShadow dx="0" dy="3" stdDeviation="3" floodOpacity=".3" />
          </filter>
          <marker
            id="attack-arrow"
            markerWidth="8"
            markerHeight="8"
            refX="6"
            refY="3"
            orient="auto"
          >
            <path d="M0,0 L0,6 L7,3 z" fill="#c94e58" />
          </marker>
        </defs>

        <g
          transform={`translate(${pan.x} ${pan.y}) scale(${zoom})`}
          style={{ transformOrigin: '360px 310px' }}
        >
          {game.map.map((hex) => (
            <HexTile
              key={`${hex.coordinate.q},${hex.coordinate.r}`}
              hex={hex}
              selected={selected?.q === hex.coordinate.q && selected.r === hex.coordinate.r}
              legal={legal.includes(`${hex.coordinate.q},${hex.coordinate.r}`)}
              overlayType={overlayType}
              showInvalid={legal.length > 0}
              onSelect={() => {
                if (legal.length > 0 && !legal.includes(`${hex.coordinate.q},${hex.coordinate.r}`)) {
                  explainInvalid?.(hex);
                }
                select(hex.coordinate);
              }}
              onTooltip={setTooltip}
            />
          ))}

          {game.players.flatMap((player) =>
            player.roads.map((road) => {
              const from = center(road.from.q, road.from.r);
              const to = center(road.to.q, road.to.r);
              return (
                <g key={road.id} className="road-segment">
                  <line
                    x1={from.x}
                    y1={from.y}
                    x2={to.x}
                    y2={to.y}
                    className="road-outline"
                  />
                  <line
                    x1={from.x}
                    y1={from.y}
                    x2={to.x}
                    y2={to.y}
                    stroke={player.color.toLowerCase()}
                    className="road"
                  />
                </g>
              );
            }),
          )}

          {game.revealedAttackPlans.map((plan) => {
            const source = center(plan.source.q, plan.source.r);
            const target = center(plan.target.q, plan.target.r);
            return (
              <g key={`attack-${plan.playerId}`} className="attack-plan-line">
                <line
                  x1={source.x}
                  y1={source.y}
                  x2={target.x}
                  y2={target.y}
                  markerEnd="url(#attack-arrow)"
                />
                <text x={(source.x + target.x) / 2} y={(source.y + target.y) / 2 - 7}>
                  {plan.participatingUnitCount + (plan.heroParticipates ? 1 : 0)}
                </text>
              </g>
            );
          })}

          {game.players.flatMap((player) =>
            player.settlements.map((settlement) => {
              const position = center(settlement.location.q, settlement.location.r);
              return (
                <g key={settlement.id} transform={`translate(${position.x} ${position.y})`}>
                  <path
                    d="M-14 8V-6L0-17 14-6V8Z"
                    fill={player.color.toLowerCase()}
                    stroke="#fff4d6"
                    strokeWidth="2"
                  />
                  <text y="23">{settlement.level[0]}</text>
                  <text className="settlement-durability" y="4">
                    {settlement.durability}/{settlementMaxDurability(settlement.level)}
                  </text>
                  <title>
                    {player.displayName} {settlement.level}: durability {settlement.durability}/
                    {settlementMaxDurability(settlement.level)}
                  </title>
                </g>
              );
            }),
          )}

          {game.players
            .filter((player) => player.hero?.location && !player.hero.defeated)
            .map((player) => {
              const location = player.hero!.location!;
              const position = center(location.q, location.r);
              return (
                <g
                  key={`hero-${player.id}`}
                  className="hero-token"
                  transform={`translate(${position.x + 18} ${position.y - 18})`}
                >
                  <circle r="16" fill={player.color.toLowerCase()} />
                  {heroTokenArt[player.hero?.heroClass ?? player.heroClass ?? ''] ? (
                    <image
                      href={heroTokenArt[player.hero?.heroClass ?? player.heroClass ?? '']}
                      x="-13"
                      y="-13"
                      width="26"
                      height="26"
                      preserveAspectRatio="xMidYMid slice"
                    />
                  ) : (
                    <text y="5">{heroIcon(player.hero?.heroClass ?? player.heroClass)}</text>
                  )}
                  <circle r="16" fill="none" stroke={player.color.toLowerCase()} strokeWidth="4" />
                  <title>
                    {player.displayName} Hero: {player.hero?.heroClass ?? player.heroClass ?? 'Hero'} (
                    {location.q}, {location.r})
                  </title>
                </g>
              );
            })}

          {game.monsters.map((monster) => {
            const position = center(monster.location.q, monster.location.r);
            return (
              <g key={monster.id} className="monster-token" transform={`translate(${position.x} ${position.y})`}>
                <circle r="22" fill="#55303a" stroke="#ff8791" strokeWidth="3" />
                <image
                  href={monsterArt(monster.type)}
                  x="-18"
                  y="-18"
                  width="36"
                  height="36"
                  preserveAspectRatio="xMidYMid slice"
                />
                <circle r="22" fill="none" stroke="#ff8791" strokeWidth="3" />
                <text y="5" fontSize="21">
                  ♞
                </text>
              </g>
            );
          })}
        </g>
      </svg>

      {tooltip && (
        <div className="tooltip">
          <b>{terrainNames[tooltip.terrain] ?? tooltip.terrain}</b>
          <span>
            Координаты: {tooltip.coordinate.q}, {tooltip.coordinate.r}
          </span>
          {tooltip.productionNumber && (
            <span>
              Производит {resourceNames[tooltip.resource ?? ''] ?? tooltip.resource} при броске{' '}
              {tooltip.productionNumber}
            </span>
          )}
          {tooltip.productionNumber && (
            <span>
              Number {tooltip.productionNumber} appears {rollProbability(tooltip.productionNumber)}/36 on
              2d6.
            </span>
          )}
          <span>{explorationHint(tooltip.terrain)}</span>
          {tooltip.villageLoyalty != null && <span>Лояльность: {tooltip.villageLoyalty}</span>}
        </div>
      )}

      <BoardDiceOverlay game={game} rolling={rolling} rollingMode={rollingMode} />
    </div>
  );
}

function BoardDiceOverlay({
  game,
  rolling,
  rollingMode,
}: {
  game: Game;
  rolling: boolean;
  rollingMode?: 'world' | 'combat';
}) {
  const latestCombat = game.combatReport?.at(-1);
  if (rollingMode === 'combat' && !latestCombat) return null;
  const preferWorldRoll =
    rollingMode === 'world' ||
    game.phase === 'WORLD_ROLL' ||
    game.phase === 'PRODUCTION' ||
    (game.phase === 'MONSTER_EVENT' && latestCombat?.conflictType !== 'MONSTER_ATTACK');
  const showCombat =
    latestCombat &&
    !preferWorldRoll &&
    ['FULL_ATTACK', 'SMALL_RAID', 'ARCANE_BOLT', 'MONSTER_ATTACK', 'ONE_WAY_ASSAULT', 'RECIPROCAL_CLASH', 'HERO_DUEL'].includes(
      latestCombat.conflictType,
    );
  if (showCombat) {
    return (
      <aside className="board-dice-overlay" aria-live="polite">
        <small>{latestCombat.conflictType === 'MONSTER_ATTACK' ? 'Monster attack' : 'Combat roll'}</small>
        <div className="d20-row">
          <D20Die value={latestCombat.roll} rolling={rollingMode === 'combat'} label="ATK" />
          {latestCombat.defenseRoll && <D20Die value={latestCombat.defenseRoll} rolling={rollingMode === 'combat'} label="DEF" />}
        </div>
        <b>
          {latestCombat.attackTotal} vs {latestCombat.defenseTotal}
        </b>
      </aside>
    );
  }
  if (!game.lastRoll && !rolling) return null;
  const first = game.lastRoll ? Math.max(1, Math.min(6, game.lastRoll - 6)) : 1;
  const second = game.lastRoll ? Math.max(1, Math.min(6, game.lastRoll - first)) : 1;
  return (
    <aside className="board-dice-overlay" aria-live="polite">
      <small>World roll</small>
      <div className="d6-row">
        <D6Die value={first} rolling={rollingMode === 'world' || (rolling && !rollingMode)} />
        <D6Die value={second} rolling={rollingMode === 'world' || (rolling && !rollingMode)} />
      </div>
      <b>{rolling ? 'Rolling…' : `Total ${game.lastRoll}`}</b>
    </aside>
  );
}

function D20Die({ value, rolling, label }: { value: number; rolling: boolean; label?: string }) {
  const critical = value === 20;
  const fumble = value === 1;
  return (
    <div className={`d20-ui ${rolling ? 'rolling' : ''} ${critical ? 'critical' : ''} ${fumble ? 'fumble' : ''}`}>
      {label && <em>{label}</em>}
      <span>{value}</span>
    </div>
  );
}

function D6Die({ value, rolling }: { value: number; rolling: boolean }) {
  return (
    <div className={`d6-ui face-${value} ${rolling ? 'rolling' : ''}`}>
      {Array.from({ length: value }, (_, index) => (
        <i key={index} />
      ))}
    </div>
  );
}

function settlementMaxDurability(level: string) {
  return level === 'CITY' ? 4 : 2;
}

function HexTile({
  hex,
  selected,
  legal,
  overlayType,
  showInvalid,
  onSelect,
  onTooltip,
}: {
  hex: Hex;
  selected: boolean;
  legal: boolean;
  overlayType?: string;
  showInvalid: boolean;
  onSelect: () => void;
  onTooltip: (hex?: Hex) => void;
}) {
  const position = center(hex.coordinate.q, hex.coordinate.r);
  return (
    <g
      className="hex"
      onClick={(event) => {
        event.stopPropagation();
        onSelect();
      }}
      onMouseEnter={() => onTooltip(hex)}
      onMouseLeave={() => onTooltip()}
      data-testid="hex"
    >
      <polygon
        points={polygonPoints(position.x, position.y)}
        fill={terrainColors[hex.terrain] ?? '#777'}
        stroke={selected ? '#fff2a9' : legal ? 'url(#legal)' : '#425047'}
        strokeWidth={selected || legal ? 5 : showInvalid ? 2.5 : 2}
        className={legal ? `target-overlay target-${overlayType ?? 'hex'}` : undefined}
        filter="url(#shadow)"
      />
      {legal && (
        <g transform={`translate(${position.x - 26} ${position.y - 28})`} className="target-badge">
          <circle r="12" />
          <text y="4">{targetIcon(overlayType)}</text>
        </g>
      )}
      {showInvalid && !legal && (
        <g transform={`translate(${position.x + 23} ${position.y - 28})`} className="hex-lock">
          <circle r="11" />
          <text y="4">!</text>
        </g>
      )}
      <text x={position.x} y={position.y - 15}>
        {terrainIcons[hex.terrain] ?? ''}
      </text>
      {hex.productionNumber && (
        <g className={hex.productionNumber === 6 || hex.productionNumber === 8 ? 'hot-token' : ''}>
          <circle cx={position.x} cy={position.y + 11} r="17" fill="#f2e3bc" />
          <text x={position.x} y={position.y + 16} className="number">
            {hex.productionNumber}
          </text>
          <text x={position.x} y={position.y + 29} className="probability">
            {rollProbability(hex.productionNumber)}/36
          </text>
        </g>
      )}
      <title>
        {terrainNames[hex.terrain] ?? hex.terrain} ({hex.coordinate.q}, {hex.coordinate.r})
      </title>
    </g>
  );
}

function targetIcon(overlayType?: string) {
  if (overlayType?.includes('ATTACK') || overlayType?.includes('ENEMY') || overlayType?.includes('MONSTER')) return '⚔';
  if (overlayType?.includes('HEAL') || overlayType?.includes('HERO') || overlayType?.includes('FRIENDLY')) return '+';
  if (overlayType?.includes('BUILD')) return '⌂';
  if (overlayType?.includes('EXPLORE') || overlayType === 'HEX') return '⌁';
  return '•';
}

function heroIcon(heroClass?: string) {
  switch (heroClass) {
    case 'KNIGHT':
      return '♞';
    case 'MAGE':
      return '✦';
    case 'PRIEST':
      return '✚';
    case 'RANGER':
      return '⌁';
    case 'MERCHANT':
      return '¤';
    case 'ENGINEER':
      return '⚙';
    default:
      return '★';
  }
}

function rollProbability(number: number) {
  return Math.max(0, 6 - Math.abs(7 - number));
}

function explorationHint(terrain: string) {
  const hints: Record<string, string> = {
    FOREST: 'Explore Forest: hidden trail, rare wood, beast sign, or shrine clue.',
    FIELD: 'Explore Field: local paths, food cache, rumor, or empty wilderness.',
    MOUNTAIN: 'Explore Mountain: ore vein, pass, hazard, or lair clue.',
    QUARRY: 'Explore Quarry: stone cache, old tunnel, tool marks, or hazard.',
    TRADE_LAND: 'Explore Trade Land: route, market contact, toll, or rumor.',
    VILLAGE: 'Explore Neutral Village: quest, alliance need, hidden problem, or unrest.',
    RUIN: 'Explore Ancient Ruin: relic, trap, lore, or seal progress.',
    MONSTER_LAIR: 'Explore Monster Lair: strength, weakness, reward clue, or spawn timer.',
    ANCIENT_CAPITAL: 'Explore Capital: major lore, seal clue, or strategic warning.',
  };
  return hints[terrain] ?? 'Explore Wilderness: trail, cache, encounter, or nothing obvious.';
}
