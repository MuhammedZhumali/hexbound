package com.hexboundrealms.domain.map;

import java.util.List;

public record HexCoordinate(int q, int r) {
  private static final int[][] DIRECTIONS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

  public List<HexCoordinate> neighbors() {
    return java.util.Arrays.stream(DIRECTIONS)
        .map(d -> new HexCoordinate(q + d[0], r + d[1]))
        .toList();
  }

  public int distanceTo(HexCoordinate other) {
    int ds = (-q - r) - (-other.q - other.r);
    return (Math.abs(q - other.q) + Math.abs(r - other.r) + Math.abs(ds)) / 2;
  }
}
