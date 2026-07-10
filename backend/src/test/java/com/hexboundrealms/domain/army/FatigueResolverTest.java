package com.hexboundrealms.domain.army;

import static org.assertj.core.api.Assertions.*;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FatigueResolverTest {
  private UnitState unit(FatigueState f) {
    return new UnitState(UUID.randomUUID(), UnitType.INFANTRY, f, false, false, 0);
  }

  @Test
  void advancesReadyToFatiguedToExhausted() {
    FatigueResolver r = new FatigueResolver();
    assertThat(r.afterBattle(unit(FatigueState.READY), false).fatigue())
        .isEqualTo(FatigueState.FATIGUED);
    assertThat(r.afterBattle(unit(FatigueState.FATIGUED), false).fatigue())
        .isEqualTo(FatigueState.EXHAUSTED);
  }

  @Test
  void naturalOneAddsConsequence() {
    assertThat(new FatigueResolver().afterBattle(unit(FatigueState.READY), true).fatigue())
        .isEqualTo(FatigueState.EXHAUSTED);
  }
}
