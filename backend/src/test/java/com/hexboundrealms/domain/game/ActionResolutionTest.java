package com.hexboundrealms.domain.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import com.hexboundrealms.application.service.GameViewMapper;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionResolutionTest {
  private final DefaultGameEngine engine = new DefaultGameEngine();

  @Test
  void tradeConsumesAndProducesSelectedResources() {
    GameState game = resolutionGame(ActionType.TRADE);
    PlayerState player = game.players.getFirst();
    player.resources = new Resources(3, 0, 0, 0, 1);

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
  void explorationWorksBeyondRuins() {
    GameState game = resolutionGame(ActionType.EXPLORE);
    PlayerState player = game.players.getFirst();
    HexCoordinate origin = new HexCoordinate(0, 0);
    HexCoordinate forest = new HexCoordinate(1, 0);
    player.hero = HeroState.create(HeroClass.KNIGHT, origin);
    game.map = new ArrayList<>(java.util.List.of(
        new MapHex(origin, TerrainType.FIELD, ResourceType.FOOD, 5, null, null, null),
        new MapHex(forest, TerrainType.FOREST, ResourceType.WOOD, 6, null, null, null)));

    engine.execute(game, player.id, new GameCommand.Explore(forest));

    assertThat(player.exploredHexes).containsExactly(forest);
    assertThat(player.resources.wood()).isEqualTo(2);
    assertThat(game.explorationResults.getFirst().type())
        .isEqualTo(ExplorationResultType.BONUS_RESOURCE);
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

  @Test
  void playerTurnsCanSkipMainActionAndExposeNextCurrentPlayer() {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.PLAYER_TURNS;
    game.status = GameStatus.ACTIVE;
    game.hybridTurnMode = true;
    game.currentTurnIndex = 0;
    PlayerState first =
        new PlayerState(UUID.randomUUID(), "First", PlayerColor.BLUE, "token", HeroClass.KNIGHT);
    PlayerState second =
        new PlayerState(UUID.randomUUID(), "Second", PlayerColor.RED, "token", HeroClass.MAGE);
    first.basicActionPoints = 1;
    second.basicActionPoints = 2;
    game.players.add(first);
    game.players.add(second);

    engine.execute(game, first.id, new GameCommand.ResolveAction());

    assertThat(first.mainActionCompletedThisRound).isTrue();
    assertThat(first.basicActionPoints).isZero();
    assertThat(game.phase).isEqualTo(GamePhase.PLAYER_TURNS);
    assertThat(game.currentTurnIndex).isEqualTo(1);
    assertThat(new GameViewMapper().publicView(game).currentTurnPlayerId()).isEqualTo(second.id);
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
