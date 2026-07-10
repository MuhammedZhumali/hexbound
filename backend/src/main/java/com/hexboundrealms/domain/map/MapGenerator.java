package com.hexboundrealms.domain.map;

import java.util.*;

public final class MapGenerator {
  private static final List<Integer> TOKENS =
      List.of(
          2, 2, 3, 3, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 6, 8, 8, 8, 8, 9, 9, 9, 9, 10, 10, 10, 11, 11,
          12, 12);

  public List<MapHex> generate(long seed) {
    List<HexCoordinate> coordinates = coordinates();
    for (int attempt = 0; attempt < 2000; attempt++) {
      Random random = new Random(seed + attempt * 7919L);
      List<TerrainType> terrain = new ArrayList<>();
      add(terrain, TerrainType.FOREST, 7);
      add(terrain, TerrainType.FIELD, 7);
      add(terrain, TerrainType.MOUNTAIN, 6);
      add(terrain, TerrainType.QUARRY, 5);
      add(terrain, TerrainType.TRADE_LAND, 5);
      add(terrain, TerrainType.VILLAGE, 3);
      add(terrain, TerrainType.RUIN, 2);
      add(terrain, TerrainType.MONSTER_LAIR, 1);
      Collections.shuffle(terrain, random);
      List<MapHex> map = new ArrayList<>();
      int index = 0;
      for (HexCoordinate c : coordinates) {
        if (c.q() == 0 && c.r() == 0)
          map.add(new MapHex(c, TerrainType.ANCIENT_CAPITAL, null, null, null, null, null));
        else {
          TerrainType t = terrain.get(index++);
          map.add(
              new MapHex(c, t, resource(t), null, null, t == TerrainType.VILLAGE ? 2 : null, null));
        }
      }
      if (!specialPlacementValid(map)) continue;
      List<MapHex> resources = map.stream().filter(h -> h.resource() != null).toList();
      List<Integer> tokens = new ArrayList<>(TOKENS);
      Collections.shuffle(tokens, random);
      Map<HexCoordinate, Integer> assigned = new HashMap<>();
      boolean valid = true;
      for (int i = 0; i < resources.size(); i++) {
        assigned.put(resources.get(i).coordinate(), tokens.get(i));
      }
      for (var e : assigned.entrySet())
        if ((e.getValue() == 6 || e.getValue() == 8)
            && e.getKey().neighbors().stream()
                .anyMatch(
                    n ->
                        assigned.get(n) != null
                            && (assigned.get(n) == 6 || assigned.get(n) == 8))) {
          valid = false;
          break;
        }
      if (!valid) continue;
      return map.stream()
          .map(
              h ->
                  new MapHex(
                      h.coordinate(),
                      h.terrain(),
                      h.resource(),
                      assigned.get(h.coordinate()),
                      h.ownerId(),
                      h.villageLoyalty(),
                      h.monster()))
          .toList();
    }
    throw new IllegalStateException("Unable to generate a valid map for seed " + seed);
  }

  public List<HexCoordinate> coordinates() {
    List<HexCoordinate> result = new ArrayList<>();
    for (int r = -3; r <= 3; r++)
      for (int q = Math.max(-3, -r - 3); q <= Math.min(3, -r + 3); q++)
        result.add(new HexCoordinate(q, r));
    return result;
  }

  private boolean specialPlacementValid(List<MapHex> map) {
    List<MapHex> ruins = map.stream().filter(h -> h.terrain() == TerrainType.RUIN).toList();
    if (ruins.get(0).coordinate().distanceTo(ruins.get(1).coordinate()) <= 1) return false;
    MapHex lair =
        map.stream().filter(h -> h.terrain() == TerrainType.MONSTER_LAIR).findFirst().orElseThrow();
    if (lair.coordinate().distanceTo(new HexCoordinate(0, 0)) > 2) return false;
    List<MapHex> villages = map.stream().filter(h -> h.terrain() == TerrainType.VILLAGE).toList();
    return villages.stream()
        .allMatch(
            v ->
                villages.stream()
                    .filter(o -> o != v)
                    .anyMatch(o -> v.coordinate().distanceTo(o.coordinate()) >= 3));
  }

  private static ResourceType resource(TerrainType t) {
    return switch (t) {
      case FOREST -> ResourceType.WOOD;
      case FIELD -> ResourceType.FOOD;
      case MOUNTAIN -> ResourceType.ORE;
      case QUARRY -> ResourceType.STONE;
      case TRADE_LAND -> ResourceType.GOLD;
      default -> null;
    };
  }

  private static void add(List<TerrainType> target, TerrainType type, int count) {
    for (int i = 0; i < count; i++) target.add(type);
  }
}
