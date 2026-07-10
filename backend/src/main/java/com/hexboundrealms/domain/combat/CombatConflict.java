package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.List;

public record CombatConflict(
    AttackConflictType type,
    List<AttackPlan> plans,
    HexCoordinate destination,
    String explanation) {
  public CombatConflict {
    plans = List.copyOf(plans);
  }
}
