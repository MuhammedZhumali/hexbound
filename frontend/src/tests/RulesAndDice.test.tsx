import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { Board } from '../components/board/Board';
import { RulesModal } from '../components/dialogs/RulesModal';
import { RealmPanel } from '../components/panels/Panels';
import type { Game } from '../types/game';

const game = {
  id: 'game',
  name: 'Four Crowns',
  seed: 42,
  debugMode: false,
  gameMode: 'STANDARD',
  status: 'ACTIVE',
  phase: 'WORLD',
  roundNumber: 1,
  version: 1,
  lastRoll: 8,
  firstPlayerId: 'p1',
  map: [],
  players: [
    {
      id: 'p1',
      displayName: 'Player 1',
      color: 'BLUE',
      heroClass: 'KNIGHT',
      glory: 0,
      reputation: 0,
      hasSelectedAction: false,
      hasLockedAttackPlan: false,
      settlements: [],
      roads: [],
      unitCount: 1,
      hero: { heroClass: 'KNIGHT', hp: 3, mana: 0, grace: 0, defeated: false },
    },
  ],
  monsters: [],
  market: [],
  eventLog: [],
  winners: [],
  revealedAttackPlans: [],
  combatReport: [],
} as Game;

describe('rules and dice assistance', () => {
  it('shows the structured rulebook and glossary', () => {
    render(<RulesModal onClose={vi.fn()} />);
    expect(screen.getByRole('heading', { name: /Hexbound Realms Rulebook/ })).toBeInTheDocument();
    expect(screen.getByText(/Reach 10 Glory/)).toBeInTheDocument();
    expect(screen.getByText('Action Card')).toBeInTheDocument();
  });

  it('shows animated dice on the board and phase guidance below the player roster', () => {
    const { container } = render(
      <>
        <RealmPanel game={game} guidanceLevel="BEGINNER" />
        <Board game={game} rolling />
      </>,
    );
    expect(screen.getByLabelText('Phase guide')).toBeInTheDocument();
    expect(container.querySelector('.board-dice-overlay')).toBeInTheDocument();
    expect(container.querySelector('.d6-ui.rolling')).toBeInTheDocument();
  });
});
