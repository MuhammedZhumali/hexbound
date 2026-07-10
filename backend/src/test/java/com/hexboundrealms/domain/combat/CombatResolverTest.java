package com.hexboundrealms.domain.combat;

import static org.assertj.core.api.Assertions.*;

import com.hexboundrealms.domain.game.GameModel.ReactionType;
import org.junit.jupiter.api.Test;

class CombatResolverTest {
  private CombatResolver.CombatResult at(int roll, int defense, ReactionType reaction) {
    return new CombatResolver()
        .resolve(new CombatResolver.CombatInput(roll, 0, 0, 0, 0, 0, 0, defense, 0, reaction, 0));
  }

  @Test
  void naturalOneAlwaysFails() {
    assertThat(at(1, 0, null).damage()).isZero();
  }

  @Test
  void naturalTwentyAddsDamage() {
    assertThat(at(20, 0, null).damage()).isEqualTo(4);
  }

  @Test
  void counterattackThresholds() {
    assertThat(at(8, 1, ReactionType.COUNTERATTACK).counterDamage()).isTrue();
    assertThat(at(5, 1, ReactionType.COUNTERATTACK).strongRetaliation()).isTrue();
  }

  @Test
  void ambushRequiresFailureByFive() {
    assertThat(at(5, 0, ReactionType.AMBUSH).strongRetaliation()).isTrue();
  }
}
