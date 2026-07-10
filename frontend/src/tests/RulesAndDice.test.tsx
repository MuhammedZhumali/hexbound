import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { RulesModal } from '../components/dialogs/RulesModal';
import { RealmPanel } from '../components/panels/Panels';
import type { Game } from '../types/game';

const game = {
  id: 'game',
  name: 'Четыре короны',
  seed: 42,
  debugMode: false,
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
      displayName: 'Игрок 1',
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
  it('shows concise rules', () => {
    render(<RulesModal onClose={vi.fn()} />);
    expect(screen.getByRole('heading', { name: /Как играть/ })).toBeInTheDocument();
    expect(screen.getByText(/12 Славы/)).toBeInTheDocument();
  });

  it('shows animated dice area below the player roster', () => {
    const { container } = render(<RealmPanel game={game} rolling />);
    expect(screen.getByText('Кубики летят…')).toBeInTheDocument();
    expect(container.querySelector('.dice-tray.rolling')).toBeInTheDocument();
  });
});
