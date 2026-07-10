package com.hexboundrealms.domain.combat;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.HexCoordinate;
import java.util.*;
import org.junit.jupiter.api.Test;

class SimultaneousCombatTest {
  private final AttackConflictDetector detector = new AttackConflictDetector();

  @Test
  void reciprocalPlansCreateClashAndHeroOnlyPlansCreateDuel() {
    HexCoordinate a = new HexCoordinate(0, 0);
    HexCoordinate b = new HexCoordinate(1, 0);
    AttackPlan armyA = plan(UUID.randomUUID(), a, b, Set.of(UUID.randomUUID()), false);
    AttackPlan armyB = plan(UUID.randomUUID(), b, a, Set.of(UUID.randomUUID()), false);
    AttackPlan heroA = plan(UUID.randomUUID(), a, b, Set.of(), true);
    AttackPlan heroB = plan(UUID.randomUUID(), b, a, Set.of(), true);

    assertThat(detector.detect(List.of(armyA, armyB)).getFirst().type())
        .isEqualTo(AttackConflictType.RECIPROCAL_CLASH);
    assertThat(detector.detect(List.of(heroA, heroB)).getFirst().type())
        .isEqualTo(AttackConflictType.HERO_DUEL);
  }

  @Test
  void detectsChainAndMultiAttackWithoutSequentialCancellation() {
    HexCoordinate a = new HexCoordinate(0, 0);
    HexCoordinate b = new HexCoordinate(1, 0);
    HexCoordinate c = new HexCoordinate(2, 0);
    HexCoordinate d = new HexCoordinate(1, -1);
    AttackPlan ab = plan(UUID.randomUUID(), a, b, Set.of(UUID.randomUUID()), false);
    AttackPlan bc = plan(UUID.randomUUID(), b, c, Set.of(UUID.randomUUID()), false);
    assertThat(detector.detect(List.of(ab, bc)))
        .extracting(CombatConflict::type)
        .contains(AttackConflictType.CHAIN_ATTACK);

    AttackPlan db = plan(UUID.randomUUID(), d, b, Set.of(UUID.randomUUID()), false);
    assertThat(detector.detect(List.of(ab, db)).getFirst().type())
        .isEqualTo(AttackConflictType.MULTI_ATTACK);
  }

  @Test
  void committedUnitsDoNotDefendSourceAndAllAttackersGainFatigue() {
    GameState game = new GameState();
    PlayerState a = player("A", PlayerColor.BLUE, new HexCoordinate(0, 0));
    PlayerState b = player("B", PlayerColor.RED, new HexCoordinate(1, 0));
    PlayerState c = player("C", PlayerColor.GREEN, new HexCoordinate(2, 0));
    UnitState aUnit = unit(true);
    UnitState bUnit = unit(true);
    a.units.add(aUnit);
    b.units.add(bUnit);
    a.attackPlan =
        plan(a.id, new HexCoordinate(0, 0), new HexCoordinate(1, 0), Set.of(aUnit.id()), false);
    b.attackPlan =
        plan(b.id, new HexCoordinate(1, 0), new HexCoordinate(2, 0), Set.of(bUnit.id()), false);
    game.players.addAll(List.of(a, b, c));

    SimultaneousCombatResolver resolver = new SimultaneousCombatResolver();
    CombatResolutionBatch batch = resolver.calculate(game, () -> 12);
    resolver.apply(game, batch);

    CombatResolutionBatch.CombatRollReport attackAgainstB =
        batch.reports().stream()
            .filter(report -> report.playerId().equals(a.id))
            .findFirst()
            .orElseThrow();
    assertThat(attackAgainstB.opposingTotal()).isEqualTo(10);
    assertThat(a.units.getFirst().fatigue()).isEqualTo(FatigueState.FATIGUED);
    assertThat(b.units.getFirst().fatigue()).isEqualTo(FatigueState.FATIGUED);
  }

  private PlayerState player(String name, PlayerColor color, HexCoordinate location) {
    PlayerState player = new PlayerState(UUID.randomUUID(), name, color, "token", HeroClass.KNIGHT);
    player.settlements.add(
        new SettlementState(UUID.randomUUID(), location, SettlementLevel.OUTPOST, 2));
    player.hero = HeroState.create(HeroClass.KNIGHT, location);
    return player;
  }

  private UnitState unit(boolean garrison) {
    return new UnitState(
        UUID.randomUUID(), UnitType.INFANTRY, FatigueState.READY, false, garrison, 0);
  }

  private AttackPlan plan(
      UUID player, HexCoordinate source, HexCoordinate target, Set<UUID> units, boolean hero) {
    return new AttackPlan(player, source, target, units, hero, Optional.empty());
  }
}
