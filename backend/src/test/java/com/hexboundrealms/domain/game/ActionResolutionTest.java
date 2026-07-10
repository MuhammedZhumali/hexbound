package com.hexboundrealms.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionResolutionTest {
  private final DefaultGameEngine engine = new DefaultGameEngine();

  @Test
  void tradeConsumesAndProducesSelectedResources() {
    GameState game = resolutionGame(ActionType.TRADE);
    PlayerState player = game.players.getFirst();

    engine.execute(game, player.id, new GameCommand.Trade(ResourceType.WOOD, ResourceType.GOLD));

    assertThat(player.resources.wood()).isZero();
    assertThat(player.resources.gold()).isEqualTo(2);
    assertThat(game.phase).isEqualTo(GamePhase.END_ROUND);
  }

  @Test
  void exploringNearbyRuinGrantsRewardAndRecordsProgress() {
    GameState game = resolutionGame(ActionType.EXPLORE);
    PlayerState player = game.players.getFirst();
    HexCoordinate origin = new HexCoordinate(0, 0);
    HexCoordinate ruin = new HexCoordinate(1, 0);
    player.hero = HeroState.create(HeroClass.RANGER, origin);
    game.map =
        new ArrayList<>(
            java.util.List.of(
                new MapHex(origin, TerrainType.FIELD, ResourceType.FOOD, 5, null, null, null),
                new MapHex(ruin, TerrainType.RUIN, null, null, null, null, null)));

    engine.execute(game, player.id, new GameCommand.Explore(ruin));

    assertThat(player.resources.gold()).isEqualTo(2);
    assertThat(player.reputation).isEqualTo(1);
    assertThat(player.exploredRuins).containsExactly(ruin);
    assertThat(player.sealProgress.ruinsExplored()).isEqualTo(1);
  }

  @Test
  void buyingMarketCardSpendsGoldAndAddsCardToHand() {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.MARKET;
    PlayerState player =
        new PlayerState(UUID.randomUUID(), "Buyer", PlayerColor.BLUE, "token", HeroClass.MERCHANT);
    player.resources = new Resources(0, 0, 0, 0, 2);
    Card card =
        new Card(
            UUID.randomUUID(),
            "Trade Charter",
            CardType.UPGRADE,
            new Resources(0, 0, 0, 0, 2),
            "Improve a settlement or army capability.",
            "Market");
    Card refill =
        new Card(
            UUID.randomUUID(),
            "Scout Network",
            CardType.ALLY,
            new Resources(0, 0, 0, 0, 1),
            "Add one temporary support power.",
            "Market");
    game.players.add(player);
    game.market.add(card);
    game.deck.add(refill);

    engine.execute(game, player.id, new GameCommand.BuyMarketCard(card.id()));

    assertThat(player.resources.gold()).isZero();
    assertThat(player.hand).containsExactly(card);
    assertThat(game.market).containsExactly(refill);
  }

  private GameState resolutionGame(ActionType action) {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.RESOLUTION;
    game.status = GameStatus.ACTIVE;
    PlayerState player =
        new PlayerState(UUID.randomUUID(), "Tester", PlayerColor.BLUE, "token", HeroClass.RANGER);
    player.selectedAction = action;
    player.actionLocked = true;
    game.players.add(player);
    return game;
  }
}
