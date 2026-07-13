import { fireEvent, render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { AvailableActionsPanel, PhaseControls } from '../features/game/GamePage';
import type { Game, LegalAction, PrivateView } from '../types/game';

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
      legalActions={['EXPLORE', 'TRADE', 'BUILD', 'RECRUIT', 'FORTIFY']}
      legalBuildTargets={[]}
      send={send}
      busy={false}
      canResolve
    />,
  );

  fireEvent.click(screen.getByRole('button', { name: /Fortify/ }));
  expect(send).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: 'Подтвердить тайное действие' }));
  expect(send).toHaveBeenCalledWith('SELECT_ACTION', { action: 'FORTIFY' });
  expect(screen.getByRole('button', { name: /Attack/ })).toBeDisabled();
});

it('renders backend legal actions and enters targeting mode for Explore', () => {
  const send = vi.fn();
  const setTargetingMode = vi.fn();
  const actions: LegalAction[] = [
    {
      actionType: 'EXPLORE_HEX',
      label: 'Explore Hex',
      description: 'Choose a nearby hex to explore.',
      apCost: 0,
      resourceCost: {},
      available: true,
      requiresTarget: true,
      targetType: 'HEX',
      validTargetHexIds: ['0,0', '1,0'],
      validTargetUnitIds: [],
      validTargetPlayerIds: [],
    },
    {
      actionType: 'FULL_ATTACK',
      label: 'Full Attack',
      description: 'Requires ATTACK card.',
      apCost: 2,
      resourceCost: {},
      available: false,
      disabledReason: 'Requires ATTACK action card.',
      requiresTarget: true,
      targetType: 'ENEMY_HEX',
      validTargetHexIds: [],
      validTargetUnitIds: [],
      validTargetPlayerIds: [],
    },
  ];
  const view = {
    playerId: 'p1',
    basicActionPoints: 3,
    resources: { wood: 1, food: 1, ore: 1, stone: 1, gold: 1 },
    roads: [],
    settlements: [],
  } as unknown as PrivateView;
  const game = { market: [] } as unknown as Game;

  render(
    <AvailableActionsPanel
      actions={actions}
      activeCard="EXPLORE"
      busy={false}
      currentName="Blue"
      offerType="wood"
      requestType="food"
      setOfferType={vi.fn()}
      setRequestType={vi.fn()}
      setTargetingMode={setTargetingMode}
      view={view}
      game={game}
      send={send}
    />,
  );

  expect(screen.getByText(/Action Points: 3 \/ 3/)).toBeInTheDocument();
  expect(screen.getByText(/Disabled: Requires ATTACK action card/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: /Explore Hex/ }));
  expect(setTargetingMode).toHaveBeenCalledWith(
    expect.objectContaining({ actionType: 'EXPLORE_HEX', validTargetHexIds: ['0,0', '1,0'] }),
  );
  expect(send).not.toHaveBeenCalled();
});
