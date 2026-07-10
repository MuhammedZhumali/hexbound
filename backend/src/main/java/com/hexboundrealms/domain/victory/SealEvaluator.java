package com.hexboundrealms.domain.victory;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.TerrainType;
import java.util.*;

public final class SealEvaluator {
  public Set<PathSeal> active(GameState game, PlayerState p) {
    Set<PathSeal> seals = new HashSet<>(p.sealProgress.permanent());
    long cities = p.settlements.stream().filter(s -> s.level() == SettlementLevel.CITY).count();
    List<Integer> loyalties =
        game.map.stream()
            .filter(h -> p.id.equals(h.ownerId()) && h.terrain() == TerrainType.VILLAGE)
            .map(h -> h.villageLoyalty() == null ? 0 : h.villageLoyalty())
            .toList();
    double avg = loyalties.isEmpty() ? 0 : loyalties.stream().mapToInt(i -> i).average().orElse(0);
    if (p.settlements.size() >= 3 && cities >= 1 && avg >= 2) seals.add(PathSeal.RULER);
    if (p.sealProgress.distinctMonsterTypes() >= 2 && p.sealProgress.heroicQuest())
      seals.add(PathSeal.HERO);
    if (p.sealProgress.tradesWithDifferentPlayers() >= 3 && p.sealProgress.tradeRoutes() >= 2)
      seals.add(PathSeal.PROSPERITY);
    if (p.sealProgress.peacefulAlliances() >= 2 && p.reputation >= 6) seals.add(PathSeal.INFLUENCE);
    if (p.sealProgress.ruinsExplored() >= 2 && p.sealProgress.artifacts() >= 1)
      seals.add(PathSeal.KNOWLEDGE);
    return seals;
  }
}
