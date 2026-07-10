package com.hexboundrealms.domain.map;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.*;

class MapGeneratorTest {
  private final List<MapHex> map = new MapGenerator().generate(123456L);

  @Test
  void generatesExactly37Hexes() {
    assertThat(map).hasSize(37);
  }

  @Test
  void hasSpecifiedTerrainCounts() {
    assertThat(count(TerrainType.ANCIENT_CAPITAL)).isEqualTo(1);
    assertThat(count(TerrainType.MONSTER_LAIR)).isEqualTo(1);
    assertThat(count(TerrainType.FOREST)).isEqualTo(7);
    assertThat(count(TerrainType.FIELD)).isEqualTo(7);
    assertThat(count(TerrainType.MOUNTAIN)).isEqualTo(6);
    assertThat(count(TerrainType.QUARRY)).isEqualTo(5);
    assertThat(count(TerrainType.TRADE_LAND)).isEqualTo(5);
    assertThat(count(TerrainType.VILLAGE)).isEqualTo(3);
    assertThat(count(TerrainType.RUIN)).isEqualTo(2);
  }

  @Test
  void numberDistributionIsExact() {
    Map<Integer, Long> counts = new HashMap<>();
    map.stream()
        .filter(h -> h.productionNumber() != null)
        .forEach(h -> counts.merge(h.productionNumber(), 1L, Long::sum));
    assertThat(counts)
        .containsEntry(2, 2L)
        .containsEntry(3, 2L)
        .containsEntry(4, 3L)
        .containsEntry(5, 4L)
        .containsEntry(6, 4L)
        .containsEntry(8, 4L)
        .containsEntry(9, 4L)
        .containsEntry(10, 3L)
        .containsEntry(11, 2L)
        .containsEntry(12, 2L);
  }

  @Test
  void sixAndEightNeverTouch() {
    for (MapHex h : map)
      if (h.productionNumber() != null && (h.productionNumber() == 6 || h.productionNumber() == 8))
        assertThat(h.coordinate().neighbors())
            .noneMatch(
                n ->
                    map.stream()
                        .anyMatch(
                            o ->
                                o.coordinate().equals(n)
                                    && o.productionNumber() != null
                                    && (o.productionNumber() == 6 || o.productionNumber() == 8)));
  }

  @Test
  void mapIsConnected() {
    Set<HexCoordinate> seen = new HashSet<>();
    Deque<HexCoordinate> todo = new ArrayDeque<>();
    todo.add(map.getFirst().coordinate());
    while (!todo.isEmpty()) {
      HexCoordinate c = todo.remove();
      if (seen.add(c))
        c.neighbors().stream()
            .filter(n -> map.stream().anyMatch(h -> h.coordinate().equals(n)))
            .forEach(todo::add);
    }
    assertThat(seen).hasSize(37);
  }

  @Test
  void sameSeedIsDeterministic() {
    assertThat(new MapGenerator().generate(123456L)).isEqualTo(map);
  }

  private long count(TerrainType t) {
    return map.stream().filter(h -> h.terrain() == t).count();
  }
}
