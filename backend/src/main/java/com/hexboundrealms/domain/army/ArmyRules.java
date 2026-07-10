package com.hexboundrealms.domain.army;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.List;

public final class ArmyRules {
  public int calculateArmyBonus(int power) {
    if (power <= 0) return 0;
    if (power <= 2) return 1;
    if (power <= 5) return 2;
    if (power <= 8) return 3;
    if (power <= 12) return 4;
    if (power <= 16) return 5;
    return 6;
  }

  public int power(UnitState unit, boolean attack, boolean inSettlement) {
    int base =
        switch (unit.type()) {
          case MILITIA -> 1;
          case INFANTRY -> attack ? 2 : 3;
          case ARCHER -> !attack && inSettlement ? 3 : 2;
          case CAVALRY -> attack ? 3 : 1;
          case MERCENARY -> 2;
        };
    int penalty =
        switch (unit.fatigue()) {
          case READY -> 0;
          case FATIGUED -> 1;
          case EXHAUSTED -> 2;
        };
    return Math.max(0, base - penalty);
  }

  public int armyPower(List<UnitState> units, boolean attack, boolean settlement) {
    return units.stream()
        .filter(u -> !attack || u.fatigue() != FatigueState.EXHAUSTED)
        .mapToInt(u -> power(u, attack, settlement))
        .sum();
  }
}
