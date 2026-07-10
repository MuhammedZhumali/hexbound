import { useRef, useState } from 'react';
import type { Game, Hex } from '../../types/game';
import { useUi } from '../../store/ui';

const RADIUS = 48;
const HEIGHT = Math.sqrt(3) * RADIUS;

const terrainColors: Record<string, string> = {
  FOREST: '#52745a',
  FIELD: '#aaa45d',
  MOUNTAIN: '#7b8086',
  QUARRY: '#a08061',
  TRADE_LAND: '#9b804b',
  VILLAGE: '#98705a',
  RUIN: '#75678a',
  MONSTER_LAIR: '#70434d',
  ANCIENT_CAPITAL: '#bd9145',
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

export function Board({ game, legal = [] }: { game: Game; legal?: string[] }) {
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
              onSelect={() => select(hex.coordinate)}
              onTooltip={setTooltip}
            />
          ))}

          {game.players.flatMap((player) =>
            player.roads.map((road) => {
              const from = center(road.from.q, road.from.r);
              const to = center(road.to.q, road.to.r);
              return (
                <line
                  key={road.id}
                  x1={from.x}
                  y1={from.y}
                  x2={to.x}
                  y2={to.y}
                  stroke={player.color.toLowerCase()}
                  strokeWidth="9"
                  strokeLinecap="round"
                  className="road"
                />
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
                </g>
              );
            }),
          )}

          {game.monsters.map((monster) => {
            const position = center(monster.location.q, monster.location.r);
            return (
              <g key={monster.id} transform={`translate(${position.x} ${position.y})`}>
                <circle r="20" fill="#55303a" stroke="#ff8791" strokeWidth="3" />
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
          {tooltip.villageLoyalty != null && <span>Лояльность: {tooltip.villageLoyalty}</span>}
        </div>
      )}
    </div>
  );
}

function HexTile({
  hex,
  selected,
  legal,
  onSelect,
  onTooltip,
}: {
  hex: Hex;
  selected: boolean;
  legal: boolean;
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
        strokeWidth={selected || legal ? 5 : 2}
        filter="url(#shadow)"
      />
      <text x={position.x} y={position.y - 15}>
        {terrainIcons[hex.terrain] ?? ''}
      </text>
      {hex.productionNumber && (
        <g>
          <circle cx={position.x} cy={position.y + 11} r="14" fill="#f2e3bc" />
          <text x={position.x} y={position.y + 16} className="number">
            {hex.productionNumber}
          </text>
        </g>
      )}
      <title>
        {terrainNames[hex.terrain] ?? hex.terrain} ({hex.coordinate.q}, {hex.coordinate.r})
      </title>
    </g>
  );
}
