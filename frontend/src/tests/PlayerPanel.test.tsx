import { render, screen } from '@testing-library/react';
import { expect, it } from 'vitest';
import { PlayerPanel } from '../components/panels/Panels';
import type { PrivateView } from '../types/game';
it('shows mana and fatigue in private player view', () => {
  const view = {
    resources: { wood: 1, food: 1, ore: 0, stone: 1, gold: 1 },
    hero: { heroClass: 'MAGE', hp: 3, mana: 2, grace: 0, defeated: false },
    glory: { construction: 0 },
    reputation: 0,
    fortificationTokens: 0,
    fortifyTokenStockpile: 0,
    temporaryFortifyTokens: 0,
    freeFortifyAssignmentsRemaining: 0,
    assignedFortifyTokens: {},
    temporaryAssignedFortifyTokens: {},
    activeSeals: [],
    hand: [],
    units: [
      {
        id: 'u',
        type: 'MILITIA',
        fatigue: 'FATIGUED',
        wounded: false,
        garrison: true,
        contractUntilRound: 0,
      },
    ],
  } as unknown as PrivateView;
  render(<PlayerPanel view={view} />);
  expect(screen.getByText(/2\/3 маны/)).toBeInTheDocument();
  expect(screen.getByText(/FATIGUED/)).toBeInTheDocument();
});
