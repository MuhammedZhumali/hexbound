package com.hexboundrealms.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexboundrealms.domain.combat.AttackPlan;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.*;
import org.junit.jupiter.api.Test;

class GameViewMapperPrivacyTest {
  private final GameViewMapper mapper = new GameViewMapper();

  @Test
  void temporaryHeroSelectionIsPrivateAndConfirmedSelectionIsPublic() {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.HERO_DRAFT;
    PlayerState player = new PlayerState(UUID.randomUUID(), "A", PlayerColor.BLUE, "secret");
    player.temporaryHeroClass = HeroClass.MAGE;
    game.players.add(player);

    assertThat(mapper.publicView(game).players().getFirst().heroClass()).isNull();
    assertThat(mapper.privateView(game, player).temporaryHeroClass()).isEqualTo(HeroClass.MAGE);

    player.hero = HeroState.create(HeroClass.MAGE, null);
    player.heroConfirmed = true;
    player.temporaryHeroClass = null;
    assertThat(mapper.publicView(game).players().getFirst().heroClass()).isEqualTo(HeroClass.MAGE);
  }

  @Test
  void attackTargetsStayHiddenUntilEveryAttackerLocksAPlan() {
    GameState game = new GameState();
    game.id = UUID.randomUUID();
    game.phase = GamePhase.RESOLUTION;
    PlayerState a = attacker("A", PlayerColor.BLUE);
    PlayerState b = attacker("B", PlayerColor.RED);
    game.players.addAll(List.of(a, b));
    a.attackPlan =
        new AttackPlan(
            a.id,
            new HexCoordinate(0, 0),
            new HexCoordinate(1, 0),
            Set.of(),
            true,
            Optional.empty());

    assertThat(mapper.publicView(game).revealedAttackPlans()).isEmpty();
    assertThat(mapper.privateView(game, a).attackPlan()).isEqualTo(a.attackPlan);

    b.attackPlan =
        new AttackPlan(
            b.id,
            new HexCoordinate(1, 0),
            new HexCoordinate(0, 0),
            Set.of(),
            true,
            Optional.empty());
    assertThat(mapper.publicView(game).revealedAttackPlans()).hasSize(2);
  }

  private PlayerState attacker(String name, PlayerColor color) {
    PlayerState player = new PlayerState(UUID.randomUUID(), name, color, "token", HeroClass.KNIGHT);
    player.actionLocked = true;
    player.selectedAction = ActionType.ATTACK;
    return player;
  }
}
