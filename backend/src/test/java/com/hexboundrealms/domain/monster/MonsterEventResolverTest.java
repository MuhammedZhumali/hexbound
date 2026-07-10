package com.hexboundrealms.domain.monster;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonsterEventResolverTest {
  @ParameterizedTest
  @CsvSource({"1,12", "3,14", "6,16", "15,22", "99,22"})
  void scalesAndCaps(int round, int expected) {
    assertThat(new MonsterEventResolver().strength(round)).isEqualTo(expected);
  }
}
