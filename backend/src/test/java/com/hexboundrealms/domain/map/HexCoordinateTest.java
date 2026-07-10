package com.hexboundrealms.domain.map;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HexCoordinateTest {
  @Test
  void returnsSixNeighbors() {
    assertThat(new HexCoordinate(0, 0).neighbors())
        .hasSize(6)
        .contains(new HexCoordinate(1, 0), new HexCoordinate(-1, 1));
  }

  @Test
  void calculatesAxialDistance() {
    assertThat(new HexCoordinate(-2, 1).distanceTo(new HexCoordinate(2, -1))).isEqualTo(4);
  }
}
