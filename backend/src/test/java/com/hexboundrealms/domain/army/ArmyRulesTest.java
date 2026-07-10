package com.hexboundrealms.domain.army;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArmyRulesTest {
  @ParameterizedTest
  @CsvSource({
    "0,0", "1,1", "2,1", "3,2", "5,2", "6,3", "8,3", "9,4", "12,4", "13,5", "16,5", "17,6", "99,6"
  })
  void boundedPower(int power, int expected) {
    assertThat(new ArmyRules().calculateArmyBonus(power)).isEqualTo(expected);
  }
}
