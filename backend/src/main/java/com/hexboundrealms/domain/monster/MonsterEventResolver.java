package com.hexboundrealms.domain.monster;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import java.util.*;

public final class MonsterEventResolver {
  public int strength(int round) {
    return Math.min(22, 12 + Math.floorDiv(round, 3) * 2);
  }

  public MonsterState spawn(GameState game, PlayerState target) {
    Set<HexCoordinate> occupied = new HashSet<>();
    game.monsters.forEach(m -> occupied.add(m.location()));
    game.players.forEach(p -> p.settlements.forEach(s -> occupied.add(s.location())));
    HexCoordinate origin =
        !target.settlements.isEmpty()
            ? target.settlements.getFirst().location()
            : target.hero != null && target.hero.location() != null
                ? target.hero.location()
                : game.map.getFirst().coordinate();
    MapHex hex =
        game.map.stream()
            .filter(
                h ->
                    !occupied.contains(h.coordinate())
                        && h.terrain() != TerrainType.ANCIENT_CAPITAL
                        && h.terrain() != TerrainType.VILLAGE)
            .min(Comparator.comparingInt(h -> h.coordinate().distanceTo(origin)))
            .orElseThrow();
    int value = strength(game.roundNumber);
    String tier = value >= 20 ? "ELITE" : value >= 16 ? "STRONG" : "MINOR";
    MonsterState m =
        new MonsterState(
            UUID.randomUUID(),
            tier.equals("MINOR")
                ? "Bog Wyrm"
                : tier.equals("STRONG") ? "Ash Drake" : "Void Colossus",
            hex.coordinate(),
            value,
            tier.equals("MINOR") ? 2 : tier.equals("STRONG") ? 3 : 4,
            target.id,
            tier);
    game.monsters.add(m);
    return m;
  }
}
