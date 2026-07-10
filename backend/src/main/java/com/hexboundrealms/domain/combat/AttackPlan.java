package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record AttackPlan(
    UUID playerId,
    HexCoordinate source,
    HexCoordinate target,
    Set<UUID> participatingUnitIds,
    boolean heroParticipates,
    Optional<UUID> selectedTacticCardId) {
  public AttackPlan {
    participatingUnitIds = Set.copyOf(participatingUnitIds);
    selectedTacticCardId = selectedTacticCardId == null ? Optional.empty() : selectedTacticCardId;
  }
}
