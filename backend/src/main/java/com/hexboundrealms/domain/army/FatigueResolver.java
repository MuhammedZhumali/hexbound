package com.hexboundrealms.domain.army;

import com.hexboundrealms.domain.game.GameModel.*;

public final class FatigueResolver {
  public UnitState afterBattle(UnitState unit, boolean naturalOne) {
    FatigueState f = step(unit.fatigue());
    if (naturalOne) f = step(f);
    return new UnitState(
        unit.id(), unit.type(), f, unit.wounded(), unit.garrison(), unit.contractUntilRound());
  }

  public UnitState recover(UnitState unit) {
    FatigueState f =
        switch (unit.fatigue()) {
          case EXHAUSTED -> FatigueState.FATIGUED;
          case FATIGUED -> FatigueState.READY;
          case READY -> FatigueState.READY;
        };
    return new UnitState(
        unit.id(), unit.type(), f, unit.wounded(), unit.garrison(), unit.contractUntilRound());
  }

  private FatigueState step(FatigueState f) {
    return switch (f) {
      case READY -> FatigueState.FATIGUED;
      case FATIGUED, EXHAUSTED -> FatigueState.EXHAUSTED;
    };
  }
}
