package com.hexboundrealms.domain.victory;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.*;

public final class VictoryEvaluator {
  private final SealEvaluator seals = new SealEvaluator();

  public List<PlayerState> qualified(GameState game) {
    return game.players.stream()
        .filter(p -> p.glory.total() >= 12 && seals.active(game, p).size() >= 3)
        .sorted(
            Comparator.comparingInt((PlayerState p) -> p.glory.total())
                .reversed()
                .thenComparingInt(p -> seals.active(game, p).size())
                .reversed()
                .thenComparingInt(p -> p.reputation)
                .reversed())
        .toList();
  }
}
