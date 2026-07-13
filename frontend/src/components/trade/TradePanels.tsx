import type { Game, Resources, TradeProposal } from '../../types/game';

const resourceLabels: Record<keyof Resources, string> = {
  wood: 'Wood',
  food: 'Food',
  ore: 'Ore',
  stone: 'Stone',
  gold: 'Gold',
};

export function PendingTradesPanel({
  game,
  playerId,
  busy,
  send,
}: {
  game: Game;
  playerId: string;
  busy: boolean;
  send: (type: string, payload?: unknown) => void;
}) {
  const proposals = game.tradeProposals ?? [];
  const pending = proposals.filter((proposal) => proposal.status === 'PENDING');

  return (
    <section className="trade-panel" aria-label="Pending trade offers">
      <div className="panel-title-row">
        <div>
          <p className="eyebrow">Player Trade</p>
          <h3>Pending Offers</h3>
        </div>
        <span>{pending.length} pending</span>
      </div>
      {pending.length === 0 && <p className="muted">No player-to-player offers yet.</p>}
      <div className="trade-offer-list">
        {pending.map((proposal) => (
          <TradeOfferCard
            key={proposal.proposalId}
            game={game}
            playerId={playerId}
            proposal={proposal}
            busy={busy}
            send={send}
          />
        ))}
      </div>
    </section>
  );
}

function TradeOfferCard({
  game,
  playerId,
  proposal,
  busy,
  send,
}: {
  game: Game;
  playerId: string;
  proposal: TradeProposal;
  busy: boolean;
  send: (type: string, payload?: unknown) => void;
}) {
  const proposer = game.players.find((player) => player.id === proposal.proposerPlayerId);
  const target = game.players.find((player) => player.id === proposal.targetPlayerId);
  const canAccept = proposal.targetPlayerId === playerId;
  const canCancel = proposal.proposerPlayerId === playerId;

  return (
    <article className="trade-offer-card">
      <header>
        <b>
          {proposer?.displayName ?? 'Player'} offers {target?.displayName ?? 'Player'}
        </b>
        <span className={`trade-status ${proposal.status.toLowerCase()}`}>{proposal.status}</span>
      </header>
      <div className="trade-columns">
        <TradeSide title="Offers" resources={proposal.offeredResources} gold={proposal.offeredGold} />
        <TradeSide
          title="Requests"
          resources={proposal.requestedResources}
          gold={proposal.requestedGold}
        />
      </div>
      {(canAccept || canCancel) && (
        <footer>
          {canAccept && (
            <>
              <button
                className="primary"
                disabled={busy}
                onClick={() => send('ACCEPT_TRADE', { proposalId: proposal.proposalId })}
              >
                Accept
              </button>
              <button
                disabled={busy}
                onClick={() => send('REJECT_TRADE', { proposalId: proposal.proposalId })}
              >
                Reject
              </button>
            </>
          )}
          {canCancel && (
            <button
              disabled={busy}
              onClick={() => send('CANCEL_TRADE', { proposalId: proposal.proposalId })}
            >
              Cancel
            </button>
          )}
        </footer>
      )}
    </article>
  );
}

function TradeSide({
  title,
  resources,
  gold,
}: {
  title: string;
  resources: Resources;
  gold: number;
}) {
  const entries = (Object.entries(resources) as [keyof Resources, number][]).filter(
    ([name, amount]) => amount > 0 && name !== 'gold',
  );
  return (
    <div>
      <small>{title}</small>
      {entries.length === 0 && gold === 0 ? (
        <em>Nothing</em>
      ) : (
        <>
          {entries.map(([name, amount]) => (
            <span key={name}>
              {amount} {resourceLabels[name]}
            </span>
          ))}
          {gold > 0 && <span>{gold} Gold</span>}
        </>
      )}
    </div>
  );
}

export function BankTradePanel({
  rate,
  tip,
}: {
  rate: string;
  tip?: string;
}) {
  return (
    <section className="bank-trade-note" aria-label="Bank trade">
      <p className="eyebrow">Bank Trade</p>
      <b>{rate}</b>
      {tip && <span>{tip}</span>}
    </section>
  );
}
