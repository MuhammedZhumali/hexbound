package com.hexboundrealms.domain.game;

import static org.assertj.core.api.Assertions.*;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.MapGenerator;
import java.util.*;
import org.junit.jupiter.api.Test;

class HeroDraftTest {
  private final DefaultGameEngine engine = new DefaultGameEngine();

  @Test
  void heroesAreNotAssignedAndDraftOrderReversesSeededTurnOrder() {
    GameState game = lobby(false);
    engine.execute(game, game.players.getFirst().id, new GameCommand.Start());

    assertThat(game.players).allMatch(player -> player.hero == null);
    assertThat(game.order.heroDraftOrder())
        .containsExactlyElementsOf(game.order.initialTurnOrder().reversed());
    assertThat(game.phase).isEqualTo(GamePhase.HERO_SELECTION);
    assertThat(game.status).isEqualTo(GameStatus.ACTIVE);
  }

  @Test
  void heroSelectionIsSimultaneousAndTemporaryChoiceMayChange() {
    GameState game = startedDraft(false);
    PlayerState current = player(game, game.order.heroDraftOrder().getFirst());
    PlayerState other =
        game.players.stream().filter(player -> player != current).findFirst().orElseThrow();

    assertThatCode(() -> engine.execute(game, other.id, new GameCommand.SelectHero(HeroClass.MAGE)))
        .doesNotThrowAnyException();

    engine.execute(game, current.id, new GameCommand.SelectHero(HeroClass.KNIGHT));
    engine.execute(game, current.id, new GameCommand.SelectHero(HeroClass.ENGINEER));
    assertThat(current.temporaryHeroClass).isEqualTo(HeroClass.ENGINEER);
    assertThat(current.hero).isNull();
  }

  @Test
  void confirmedHeroClassMayRepeatButCannotBeChangedDirectly() {
    GameState game = startedDraft(false);
    PlayerState first = player(game, game.order.heroDraftOrder().getFirst());
    engine.execute(game, first.id, new GameCommand.SelectHero(HeroClass.PRIEST));
    engine.execute(game, first.id, new GameCommand.ConfirmHero());
    PlayerState second = player(game, game.order.heroDraftOrder().get(1));

    assertThatCode(
            () -> engine.execute(game, second.id, new GameCommand.SelectHero(HeroClass.PRIEST)))
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () -> engine.execute(game, first.id, new GameCommand.SelectHero(HeroClass.MAGE)))
        .isInstanceOf(DomainException.class);
  }

  @Test
  void duplicatesCanBeEnabledByConfiguration() {
    GameState game = startedDraft(true);
    PlayerState first = player(game, game.order.heroDraftOrder().getFirst());
    engine.execute(game, first.id, new GameCommand.SelectHero(HeroClass.RANGER));
    engine.execute(game, first.id, new GameCommand.ConfirmHero());
    PlayerState second = player(game, game.order.heroDraftOrder().get(1));

    assertThatCode(
            () -> engine.execute(game, second.id, new GameCommand.SelectHero(HeroClass.RANGER)))
        .doesNotThrowAnyException();
  }

  @Test
  void everyHeroMustBeConfirmedBeforeSnakePlacement() {
    GameState game = startedDraft(false);
    HeroClass[] heroes = HeroClass.values();
    for (int i = 0; i < game.order.heroDraftOrder().size(); i++) {
      PlayerState player = player(game, game.order.heroDraftOrder().get(i));
      engine.execute(game, player.id, new GameCommand.SelectHero(heroes[i]));
      engine.execute(game, player.id, new GameCommand.ConfirmHero());
    }
    assertThat(game.phase).isEqualTo(GamePhase.HERO_REVEAL);

    engine.execute(game, game.players.getFirst().id, new GameCommand.StartStartingPlacement());

    assertThat(game.phase).isEqualTo(GamePhase.STARTING_PLACEMENT);
    assertThat(game.players).allSatisfy(player -> assertThat(player.settlements).isEmpty());

    completeManualPlacement(game);

    assertThat(game.phase).isEqualTo(GamePhase.WORLD_ROLL);
    assertThat(game.players)
        .allSatisfy(
            player -> {
              assertThat(player.settlements).hasSize(1);
              assertThat(player.roads).hasSize(1);
              assertThat(player.units).hasSize(1);
              assertThat(player.resources).isEqualTo(Resources.starting());
            });
  }

  @Test
  void heroSwapRequiresTargetConsentAndPreservesColorAndOrder() {
    GameState game = startedDraft(false);
    HeroClass[] heroes = HeroClass.values();
    for (int i = 0; i < game.order.heroDraftOrder().size(); i++) {
      PlayerState player = player(game, game.order.heroDraftOrder().get(i));
      engine.execute(game, player.id, new GameCommand.SelectHero(heroes[i]));
      engine.execute(game, player.id, new GameCommand.ConfirmHero());
    }
    PlayerState requester = game.players.getFirst();
    PlayerState target = game.players.get(1);
    HeroClass requesterHero = requester.hero.heroClass();
    HeroClass targetHero = target.hero.heroClass();
    PlayerColor requesterColor = requester.color;
    List<UUID> initialOrder = game.order.initialTurnOrder();

    engine.execute(game, requester.id, new GameCommand.ProposeHeroSwap(target.id));
    UUID proposalId = game.heroSwapProposals.getFirst().proposalId();
    assertThatThrownBy(
            () -> engine.execute(game, requester.id, new GameCommand.AcceptHeroSwap(proposalId)))
        .isInstanceOf(DomainException.class);
    engine.execute(game, target.id, new GameCommand.AcceptHeroSwap(proposalId));

    assertThat(requester.hero.heroClass()).isEqualTo(targetHero);
    assertThat(target.hero.heroClass()).isEqualTo(requesterHero);
    assertThat(requester.color).isEqualTo(requesterColor);
    assertThat(game.order.initialTurnOrder()).isEqualTo(initialOrder);

    engine.execute(game, requester.id, new GameCommand.StartStartingPlacement());
    assertThatThrownBy(
            () -> engine.execute(game, requester.id, new GameCommand.ProposeHeroSwap(target.id)))
        .isInstanceOf(DomainException.class);
  }

  private GameState startedDraft(boolean duplicates) {
    GameState game = lobby(duplicates);
    engine.execute(game, game.players.getFirst().id, new GameCommand.Start());
    return game;
  }

  private GameState lobby(boolean duplicates) {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.seed = 481516L;
    game.maxPlayers = 4;
    game.heroDraftSettings = new HeroDraftSettings(duplicates);
    game.map = new ArrayList<>(new MapGenerator().generate(game.seed));
    for (int i = 0; i < 4; i++) {
      game.players.add(
          new PlayerState(
              UUID.randomUUID(), "Player " + (i + 1), PlayerColor.values()[i], "token-" + i));
    }
    return game;
  }

  private PlayerState player(GameState game, UUID id) {
    return game.players.stream().filter(player -> player.id.equals(id)).findFirst().orElseThrow();
  }

  private void completeManualPlacement(GameState game) {
    while (game.startingPlacementStep == StartingPlacementStep.OUTPOST) {
      PlayerState current = player(game, placementPlayerId(game));
      boolean placed = false;
      for (var hex : game.map) {
        try {
          engine.execute(game, current.id, new GameCommand.PlaceStartingOutpost(hex.coordinate()));
          placed = true;
          break;
        } catch (DomainException ignored) {
          // Try the next server-validated target.
        }
      }
      assertThat(placed).isTrue();
    }
    while (game.startingPlacementStep == StartingPlacementStep.ROAD) {
      PlayerState current = player(game, placementPlayerId(game));
      boolean placed = false;
      for (var target : current.settlements.getFirst().location().neighbors()) {
        try {
          engine.execute(game, current.id, new GameCommand.PlaceStartingRoad(target));
          placed = true;
          break;
        } catch (DomainException ignored) {
          // Try the next adjacent target.
        }
      }
      assertThat(placed).isTrue();
    }
  }

  private UUID placementPlayerId(GameState game) {
    int index = game.startingPlacementStep == StartingPlacementStep.ROAD
        ? game.players.size() - 1 - game.currentStartingPlacementIndex
        : game.currentStartingPlacementIndex;
    return game.order.initialTurnOrder().get(index);
  }
}
