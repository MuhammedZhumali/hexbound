package com.hexboundrealms.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hexboundrealms.api.dto.Views.LegalActionDto;
import com.hexboundrealms.api.dto.Views.LegalActions;
import com.hexboundrealms.domain.game.DefaultGameEngine;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import com.hexboundrealms.infrastructure.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class LegalActionsServiceTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void activeTurnLegalActionsExposeExploreDeepExploreAndAttacks() throws Exception {
    GameState game = activeTurn(HeroClass.RANGER, ActionType.EXPLORE);
    game.monsters.add(new MonsterState(UUID.randomUUID(), "Goblin", new HexCoordinate(1, 0), 10, 2,
        game.players.getFirst().id, "MINOR"));

    LegalActions legal = service(game).legal(game.id, game.players.getFirst().id);

    assertThat(action(legal, "EXPLORE_HEX").apCost()).isZero();
    assertThat(action(legal, "EXPLORE_HEX").validTargetHexIds()).contains("0,0", "1,0");
    assertThat(action(legal, "DEEP_EXPLORE").apCost()).isEqualTo(1);
    assertThat(action(legal, "SMALL_RAID").available()).isTrue();
    assertThat(action(legal, "FULL_ATTACK").available()).isFalse();
    assertThat(action(legal, "FULL_ATTACK").disabledReason()).contains("Requires ATTACK");
  }

  @Test
  void priestAndMageSkillsAppearWithResourceReasons() throws Exception {
    GameState priest = activeTurn(HeroClass.PRIEST, ActionType.FORTIFY);
    priest.players.getFirst().units.add(new UnitState(UUID.randomUUID(), UnitType.MILITIA,
        FatigueState.READY, true, false, 0));
    LegalActions priestLegal = service(priest).legal(priest.id, priest.players.getFirst().id);
    assertThat(action(priestLegal, "PRIEST_HEAL").available()).isTrue();
    assertThat(action(priestLegal, "PRIEST_SANCTUARY").resourceCost()).containsEntry("grace", 2);

    GameState mage = activeTurn(HeroClass.MAGE, ActionType.TRADE);
    mage.players.getFirst().hero = new HeroState(HeroClass.MAGE, 3, 0, 0, new HexCoordinate(0, 0), false);
    LegalActions mageLegal = service(mage).legal(mage.id, mage.players.getFirst().id);
    assertThat(action(mageLegal, "TRANSMUTE").available()).isFalse();
    assertThat(action(mageLegal, "TRANSMUTE").disabledReason()).contains("Mana");
  }

  @Test
  void buildRoadUsesRoadTargetsFromSettlementNetworkNotOutpostTargets() throws Exception {
    GameState game = activeTurn(HeroClass.ENGINEER, ActionType.BUILD);
    PlayerState player = game.players.getFirst();
    player.settlements.add(new SettlementState(UUID.randomUUID(), new HexCoordinate(0, 0),
        SettlementLevel.OUTPOST, 2));

    LegalActions legal = service(game).legal(game.id, player.id);

    assertThat(action(legal, "BUILD_ROAD").available()).isTrue();
    assertThat(action(legal, "BUILD_ROAD").validTargetHexIds()).containsExactlyInAnyOrder("1,0", "0,1");
    assertThat(action(legal, "QUICK_ROAD").validTargetHexIds()).containsExactlyInAnyOrder("1,0", "0,1");
    assertThat(action(legal, "BUILD_OUTPOST").validTargetHexIds()).isEmpty();
  }

  @Test
  void onlyDefenderGetsPrivateReactionActionsDuringPendingConflict() throws Exception {
    GameState game = activeTurn(HeroClass.KNIGHT, ActionType.ATTACK);
    PlayerState attacker = game.players.getFirst();
    PlayerState defender = new PlayerState(UUID.randomUUID(), "Red", PlayerColor.RED, "token-2", HeroClass.RANGER);
    defender.settlements.add(new SettlementState(UUID.randomUUID(), new HexCoordinate(1, 0),
        SettlementLevel.OUTPOST, 4));
    game.players.add(defender);
    game.phase = GamePhase.WAITING_FOR_DEFENDER_REACTION;
    game.pendingConflict = new PendingConflict(UUID.randomUUID(), attacker.id, defender.id,
        new HexCoordinate(1, 0), ConflictAttackType.FULL_ATTACK, ActionType.ATTACK, null, 1);

    LegalActions defenderLegal = service(game).legal(game.id, defender.id);
    LegalActions attackerLegal = service(game).legal(game.id, attacker.id);

    assertThat(defenderLegal.availableActions())
        .extracting(LegalActionDto::actionType)
        .containsExactly(
            "DEFENDER_REACTION_SHIELD",
            "DEFENDER_REACTION_COUNTERATTACK",
            "DEFENDER_REACTION_EVACUATION",
            "DEFENDER_REACTION_NONE");
    assertThat(attackerLegal.availableActions()).isEmpty();
  }

  private GameApplicationService service(GameState game) throws Exception {
    GameJpaRepository games = mock(GameJpaRepository.class);
    GameEventJpaRepository events = mock(GameEventJpaRepository.class);
    SimpMessagingTemplate websocket = mock(SimpMessagingTemplate.class);
    when(games.findById(game.id))
        .thenReturn(Optional.of(new GameEntity(game.id, game.seed, game.status.name(), game.phase.name(),
            game.roundNumber, json.writeValueAsString(game))));
    return new GameApplicationService(games, events, json, new DefaultGameEngine(), websocket);
  }

  private GameState activeTurn(HeroClass heroClass, ActionType selectedAction) {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.seed = 7;
    game.phase = GamePhase.PLAYER_TURNS;
    game.status = GameStatus.ACTIVE;
    PlayerState player = new PlayerState(UUID.randomUUID(), "Blue", PlayerColor.BLUE, "token", heroClass);
    player.hero = HeroState.create(heroClass, new HexCoordinate(0, 0));
    player.selectedAction = selectedAction;
    player.actionLocked = true;
    player.basicActionPoints = 3;
    game.players.add(player);
    game.currentTurnIndex = 0;
    game.actionTurnOrder.add(player.id);
    game.map = new ArrayList<>(List.of(
        new MapHex(new HexCoordinate(0, 0), TerrainType.FIELD, ResourceType.FOOD, 5, null, null, null),
        new MapHex(new HexCoordinate(1, 0), TerrainType.FOREST, ResourceType.WOOD, 6, null, null, null),
        new MapHex(new HexCoordinate(0, 1), TerrainType.MOUNTAIN, ResourceType.ORE, 8, null, null, null)));
    return game;
  }

  private LegalActionDto action(LegalActions legal, String type) {
    return legal.availableActions().stream()
        .filter(action -> action.actionType().equals(type))
        .findFirst()
        .orElseThrow();
  }
}
