import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { PhaseControls } from '../features/game/GamePage';
import type { Game, PrivateView } from '../types/game';

it('selects an action card locally and sends it only after confirmation', () => {
  const send = vi.fn();
  const game = {
    phase: 'PLANNING',
    players: [],
    market: [],
    winners: [],
  } as unknown as Game;
  const view = {
    playerId: 'p1',
    previousAction: 'ATTACK',
    resources: { wood: 1, food: 1, ore: 1, stone: 1, gold: 1 },
  } as unknown as PrivateView;

  render(
    <PhaseControls
      game={game}
      view={view}
      legalActions={['FORTIFY', 'TRADE', 'RECRUIT', 'BUILD', 'EXPLORE']}
      legalBuildTargets={[]}
      send={send}
      busy={false}
      reaction="SHIELD"
      setReaction={vi.fn()}
      canResolve
    />,
  );

  fireEvent.click(screen.getByRole('button', { name: /Укрепиться/ }));
  expect(send).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Подтвердить тайное действие' }));
  expect(send).toHaveBeenCalledWith('SELECT_ACTION', { action: 'FORTIFY' });
  expect(screen.getByRole('button', { name: /Атаковать/ })).toBeDisabled();
});
