package com.hexboundrealms.domain.settlement;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import java.util.*;

public final class BuildingValidator {
  public boolean roadLegal(GameState game, PlayerState p, HexCoordinate from, HexCoordinate to) {
    if (from.distanceTo(to) != 1) return false;
    if (game.map.stream().noneMatch(h -> h.coordinate().equals(from))
        || game.map.stream().noneMatch(h -> h.coordinate().equals(to))) return false;
    if (game.players.stream().flatMap(x -> x.roads.stream()).anyMatch(r -> sameEdge(r, from, to)))
      return false;
    return p.settlements.stream()
            .anyMatch(s -> s.location().equals(from) || s.location().equals(to))
        || p.roads.stream()
            .anyMatch(
                r ->
                    r.from().equals(from)
                        || r.to().equals(from)
                        || r.from().equals(to)
                        || r.to().equals(to));
  }

  public boolean outpostLegal(GameState game, PlayerState p, HexCoordinate at) {
    if (game.players.stream()
        .flatMap(x -> x.settlements.stream())
        .anyMatch(s -> s.location().equals(at))) return false;
    if (game.players.stream()
        .filter(x -> !x.id.equals(p.id))
        .flatMap(x -> x.settlements.stream())
        .anyMatch(s -> s.location().distanceTo(at) <= 1)) return false;
    if (game.monsters.stream().anyMatch(m -> m.location().equals(at))) return false;
    if (game.map.stream()
        .noneMatch(
            h ->
                h.coordinate().equals(at)
                    && h.terrain() != TerrainType.VILLAGE
                    && h.terrain() != TerrainType.ANCIENT_CAPITAL)) return false;
    return p.roads.stream().anyMatch(r -> r.from().equals(at) || r.to().equals(at));
  }

  private boolean sameEdge(RoadState r, HexCoordinate a, HexCoordinate b) {
    return (r.from().equals(a) && r.to().equals(b)) || (r.from().equals(b) && r.to().equals(a));
  }
}
