import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PhaseControls } from '../features/game/GamePage';
import type { Game, HeroDraft, PrivateView } from '../types/game';

describe('private setup flows', () => {
  it('confirms only the current player private Hero choice', () => {
    const send = vi.fn();
    const game = {
      phase: 'HERO_DRAFT',
      players: [{ id: 'p1', displayName: 'Игрок 1' }],
    } as unknown as Game;
    const view = { playerId: 'p1', temporaryHeroClass: 'MAGE' } as unknown as PrivateView;
    const draft = {
      phase: 'HERO_DRAFT',
      initialTurnOrder: ['p1'],
      draftOrder: ['p1'],
      currentDraftPlayerId: 'p1',
      availableHeroClasses: ['MAGE'],
      confirmedSelections: {},
      allowDuplicateHeroClasses: false,
    } as HeroDraft;

    render(
      <PhaseControls
        game={game}
        view={view}
        heroDraft={draft}
        legalBuildTargets={[]}
        send={send}
        busy={false}
        reaction="SHIELD"
        setReaction={vi.fn()}
        canResolve
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Подтвердить героя' }));
    expect(send).toHaveBeenCalledWith('CONFIRM_HERO');
  });

  it('locks a complete attack plan instead of resolving an attack immediately', () => {
    const send = vi.fn();
    const game = { phase: 'RESOLUTION' } as unknown as Game;
    const view = {
      playerId: 'p1',
      selectedAction: 'ATTACK',
      attackPlan: undefined,
      settlements: [{ id: 's1', location: { q: 0, r: 0 }, level: 'OUTPOST', durability: 2 }],
      units: [
        {
          id: 'u1',
          type: 'INFANTRY',
          fatigue: 'READY',
          wounded: false,
          garrison: true,
          contractUntilRound: 0,
        },
      ],
      resources: { wood: 1, food: 1, ore: 1, stone: 1, gold: 1 },
    } as unknown as PrivateView;

    render(
      <PhaseControls
        game={game}
        view={view}
        selected={{ q: 1, r: 0 }}
        legalBuildTargets={[]}
        send={send}
        busy={false}
        reaction="SHIELD"
        setReaction={vi.fn()}
        canResolve
      />,
    );

    fireEvent.click(screen.getByLabelText(/INFANTRY/));
    fireEvent.click(screen.getByRole('button', { name: 'Зафиксировать план атаки' }));
    expect(send).toHaveBeenCalledWith('LOCK_ATTACK_PLAN', {
      source: { q: 0, r: 0 },
      target: { q: 1, r: 0 },
      participatingUnitIds: ['u1'],
      heroParticipates: false,
    });
  });

  it('buys an affordable market card with gold', () => {
    const send = vi.fn();
    const game = {
      phase: 'MARKET',
      market: [
        {
          id: 'card-1',
          name: 'Trade Charter',
          category: 'UPGRADE',
          cost: { wood: 0, food: 0, ore: 0, stone: 0, gold: 2 },
          effect: 'Improve a settlement or army capability.',
          timing: 'Market',
        },
      ],
    } as unknown as Game;
    const view = {
      playerId: 'p1',
      resources: { wood: 0, food: 0, ore: 0, stone: 0, gold: 2 },
    } as unknown as PrivateView;

    render(
      <PhaseControls
        game={game}
        view={view}
        legalBuildTargets={[]}
        send={send}
        busy={false}
        reaction="SHIELD"
        setReaction={vi.fn()}
        canResolve
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /Trade Charter/ }));
    expect(send).toHaveBeenCalledWith('BUY_MARKET_CARD', { cardId: 'card-1' });
  });
});
