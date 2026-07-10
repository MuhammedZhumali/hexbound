package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.List;
import java.util.UUID;

public final class AttackGraphBuilder {
  public record AttackEdge(UUID playerId, HexCoordinate source, HexCoordinate target) {}

  public List<AttackEdge> build(List<AttackPlan> plans) {
    return plans.stream()
        .map(plan -> new AttackEdge(plan.playerId(), plan.source(), plan.target()))
        .toList();
  }
}
