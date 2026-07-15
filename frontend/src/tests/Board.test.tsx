import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Board } from '../components/board/Board';
import type { Game } from '../types/game';
const map = Array.from({ length: 37 }, (_, i) => ({
  coordinate: { q: (i % 7) - 3, r: Math.floor(i / 7) - 2 },
  terrain: 'FOREST',
  resource: 'WOOD',
  productionNumber: 5,
}));
const game = {
  id: 'g',
  name: 'test',
  seed: 1,
  debugMode: false,
  gameMode: 'STANDARD',
  status: 'ACTIVE',
  phase: 'WORLD',
  roundNumber: 1,
  version: 1,
  map,
  players: [],
  monsters: [],
  market: [],
  eventLog: [],
  winners: [],
  revealedAttackPlans: [],
  combatReport: [],
} as Game;
describe('Board', () => {
  it('renders exactly 37 SVG hexes', () => {
    render(
      <div style={{ width: 700, height: 600 }}>
        <Board game={game} />
      </div>,
    );
    expect(screen.getAllByTestId('hex')).toHaveLength(37);
  });
  it('shows terrain tooltip', () => {
    render(<Board game={game} />);
    fireEvent.mouseEnter(screen.getAllByTestId('hex')[0]);
    expect(screen.getByText('Лес')).toBeInTheDocument();
  });
});
