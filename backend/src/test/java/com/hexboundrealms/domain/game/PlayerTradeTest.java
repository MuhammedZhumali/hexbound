package com.hexboundrealms.domain.game;

import static org.assertj.core.api.Assertions.*;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerTradeTest {
  private final DefaultGameEngine engine = new DefaultGameEngine();

  @Test
  void targetAcceptanceExecutesAtomically() {
    GameState game = tradeGame();
    PlayerState proposer = game.players.get(0);
    PlayerState target = game.players.get(1);
    proposer.resources = new Resources(2, 0, 0, 0, 1);
    target.resources = new Resources(0, 2, 0, 0, 2);

    engine.execute(game, proposer.id, new GameCommand.ProposeTrade(
        target.id, new Resources(2, 0, 0, 0, 0), new Resources(0, 1, 0, 0, 0), 1, 0));
    TradeProposal proposal = game.tradeProposals.getFirst();
    assertThat(proposal.status()).isEqualTo(TradeStatus.PENDING);

    engine.execute(game, target.id, new GameCommand.AcceptTrade(proposal.proposalId()));

    assertThat(game.tradeProposals.getFirst().status()).isEqualTo(TradeStatus.ACCEPTED);
    assertThat(proposer.resources).isEqualTo(new Resources(0, 1, 0, 0, 0));
    assertThat(target.resources).isEqualTo(new Resources(2, 1, 0, 0, 3));
  }

  @Test
  void acceptanceRechecksBothInventories() {
    GameState game = tradeGame();
    PlayerState proposer = game.players.get(0);
    PlayerState target = game.players.get(1);
    proposer.resources = new Resources(1, 0, 0, 0, 0);
    target.resources = new Resources(0, 1, 0, 0, 0);
    engine.execute(game, proposer.id, new GameCommand.ProposeTrade(
        target.id, new Resources(1, 0, 0, 0, 0), new Resources(0, 1, 0, 0, 0), 0, 0));
    proposer.resources = Resources.none();

    assertThatThrownBy(() -> engine.execute(game, target.id,
        new GameCommand.AcceptTrade(game.tradeProposals.getFirst().proposalId())))
        .isInstanceOf(DomainException.class).hasMessageContaining("holdings changed");
    assertThat(game.tradeProposals.getFirst().status()).isEqualTo(TradeStatus.PENDING);
  }

  private GameState tradeGame() {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.TRADE_NEGOTIATION;
    game.players.add(new PlayerState(UUID.randomUUID(), "A", PlayerColor.BLUE, "a", HeroClass.KNIGHT));
    game.players.add(new PlayerState(UUID.randomUUID(), "B", PlayerColor.RED, "b", HeroClass.MERCHANT));
    return game;
  }
}
