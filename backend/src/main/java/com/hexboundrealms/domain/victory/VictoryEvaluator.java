package com.hexboundrealms.domain.victory;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.*;

public final class VictoryEvaluator {
  public List<PlayerState> qualified(GameState game) {
    return game.players.stream()
        .filter(p -> p.glory.total() >= 10)
        .sorted(
            Comparator.comparingInt((PlayerState p) -> p.glory.total())
                .reversed()
                .thenComparingInt(p -> p.reputation)
                .reversed())
        .toList();
  }
}
