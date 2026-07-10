package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.List;
import java.util.UUID;

public record CombatResolutionBatch(
    List<CombatConflict> conflicts,
    List<PendingDamage> pendingDamage,
    List<PendingMonsterDamage> pendingMonsterDamage,
    List<PendingFatigue> pendingFatigue,
    List<PendingRetreat> pendingRetreats,
    List<PendingOccupation> pendingOccupations,
    List<CombatRollReport> reports) {
  public record PendingDamage(
      UUID playerId, List<UUID> unitIds, HexCoordinate settlement, int amount) {}

  public record PendingMonsterDamage(UUID monsterId, int amount) {}

  public record PendingFatigue(UUID playerId, List<UUID> unitIds, boolean naturalOne) {}

  public record PendingRetreat(UUID playerId, HexCoordinate source, boolean isolated) {}

  public record PendingOccupation(UUID playerId, HexCoordinate target, boolean eligible) {}

  public record CombatRollReport(
      AttackConflictType type, UUID playerId, int roll, int total, int opposingTotal, int damage) {}
}
