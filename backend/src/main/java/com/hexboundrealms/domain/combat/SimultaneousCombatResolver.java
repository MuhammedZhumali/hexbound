package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.army.ArmyRules;
import com.hexboundrealms.domain.army.FatigueResolver;
import com.hexboundrealms.domain.combat.CombatResolutionBatch.*;
import com.hexboundrealms.domain.game.GameModel.*;
import java.util.*;
import java.util.function.IntSupplier;

public final class SimultaneousCombatResolver {
  private final AttackConflictDetector detector = new AttackConflictDetector();
  private final ArmyRules armies = new ArmyRules();

  public CombatResolutionBatch calculate(GameState snapshot, IntSupplier d20) {
    List<AttackPlan> plans =
        snapshot.players.stream()
            .map(player -> player.attackPlan)
            .filter(Objects::nonNull)
            .toList();
    List<CombatConflict> conflicts = detector.detect(plans);
    List<PendingDamage> damage = new ArrayList<>();
    List<PendingMonsterDamage> monsterDamage = new ArrayList<>();
    List<PendingFatigue> fatigue = new ArrayList<>();
    List<PendingRetreat> retreats = new ArrayList<>();
    List<CombatRollReport> reports = new ArrayList<>();
    Set<UUID> allCommitted =
        plans.stream()
            .flatMap(plan -> plan.participatingUnitIds().stream())
            .collect(java.util.stream.Collectors.toSet());

    for (CombatConflict conflict : conflicts) {
      if (conflict.type() == AttackConflictType.RECIPROCAL_CLASH
          || conflict.type() == AttackConflictType.HERO_DUEL) {
        resolveClash(snapshot, conflict, d20, damage, fatigue, retreats, reports);
      } else {
        for (AttackPlan plan : conflict.plans()) {
          PlayerState attacker = player(snapshot, plan.playerId());
          PlayerState defender = ownerAt(snapshot, plan.target());
          MonsterState monster =
              snapshot.monsters.stream()
                  .filter(item -> item.location().equals(plan.target()))
                  .findFirst()
                  .orElse(null);
          int roll = d20.getAsInt();
          int attackPower =
              armies.armyPower(
                  attacker.units.stream()
                      .filter(unit -> plan.participatingUnitIds().contains(unit.id()))
                      .toList(),
                  true,
                  false);
          int attackTotal =
              roll
                  + armies.calculateArmyBonus(attackPower)
                  + (plan.heroParticipates() && attacker.hero.heroClass() == HeroClass.KNIGHT
                      ? 2
                      : 0);
          int defensePower =
              defender == null
                  ? 0
                  : armies.armyPower(
                      defender.units.stream()
                          .filter(UnitState::garrison)
                          .filter(unit -> !allCommitted.contains(unit.id()))
                          .toList(),
                      false,
                      true);
          int defenseTotal =
              monster != null ? monster.strength() : 10 + armies.calculateArmyBonus(defensePower);
          int margin = attackTotal - defenseTotal;
          int inflicted = roll == 1 || margin < 0 ? 0 : margin < 5 ? 1 : margin < 10 ? 2 : 3;
          if (roll == 20) inflicted++;
          reports.add(
              new CombatRollReport(
                  conflict.type(), attacker.id, roll, attackTotal, defenseTotal, inflicted));
          fatigue.add(
              new PendingFatigue(attacker.id, List.copyOf(plan.participatingUnitIds()), roll == 1));
          if (defender != null && inflicted > 0) {
            List<UUID> defendingUnits =
                defender.units.stream()
                    .filter(UnitState::garrison)
                    .filter(unit -> !allCommitted.contains(unit.id()))
                    .map(UnitState::id)
                    .toList();
            damage.add(new PendingDamage(defender.id, defendingUnits, plan.target(), inflicted));
          } else if (monster != null && inflicted > 0) {
            monsterDamage.add(new PendingMonsterDamage(monster.id(), inflicted));
          }
        }
      }
    }
    return new CombatResolutionBatch(
        conflicts, damage, monsterDamage, fatigue, retreats, List.of(), reports);
  }

  public void apply(GameState state, CombatResolutionBatch batch) {
    for (PendingDamage pending : batch.pendingDamage()) {
      PlayerState player = player(state, pending.playerId());
      int remaining = pending.amount();
      List<UnitState> changed = new ArrayList<>(player.units);
      for (UUID unitId : pending.unitIds()) {
        if (remaining == 0) break;
        int index = indexOf(changed, unitId);
        if (index < 0) continue;
        UnitState unit = changed.get(index);
        if (unit.wounded()) changed.remove(index);
        else
          changed.set(
              index,
              new UnitState(
                  unit.id(),
                  unit.type(),
                  unit.fatigue(),
                  true,
                  unit.garrison(),
                  unit.contractUntilRound()));
        remaining--;
      }
      if (remaining > 0 && pending.settlement() != null) {
        int finalRemaining = remaining;
        player.settlements =
            player.settlements.stream()
                .map(
                    settlement ->
                        settlement.location().equals(pending.settlement())
                            ? new SettlementState(
                                settlement.id(),
                                settlement.location(),
                                settlement.level(),
                                Math.max(0, settlement.durability() - finalRemaining))
                            : settlement)
                .toList();
      }
      player.units = changed;
    }

    for (PendingMonsterDamage pending : batch.pendingMonsterDamage()) {
      state.monsters =
          state.monsters.stream()
              .map(
                  monster ->
                      monster.id().equals(pending.monsterId())
                          ? new MonsterState(
                              monster.id(),
                              monster.type(),
                              monster.location(),
                              monster.strength(),
                              Math.max(0, monster.hp() - pending.amount()),
                              monster.targetPlayerId(),
                              monster.tier())
                          : monster)
              .filter(monster -> monster.hp() > 0)
              .toList();
    }

    FatigueResolver fatigueResolver = new FatigueResolver();
    for (PendingFatigue pending : batch.pendingFatigue()) {
      PlayerState player = player(state, pending.playerId());
      player.units =
          player.units.stream()
              .map(
                  unit ->
                      pending.unitIds().contains(unit.id())
                          ? fatigueResolver.afterBattle(unit, pending.naturalOne())
                          : unit)
              .toList();
    }
  }

  private void resolveClash(
      GameState state,
      CombatConflict conflict,
      IntSupplier d20,
      List<PendingDamage> damage,
      List<PendingFatigue> fatigue,
      List<PendingRetreat> retreats,
      List<CombatRollReport> reports) {
    AttackPlan left = conflict.plans().get(0);
    AttackPlan right = conflict.plans().get(1);
    PlayerState leftPlayer = player(state, left.playerId());
    PlayerState rightPlayer = player(state, right.playerId());
    int leftRoll = d20.getAsInt();
    int rightRoll = d20.getAsInt();
    int leftTotal = clashTotal(leftPlayer, left, leftRoll, conflict.type());
    int rightTotal = clashTotal(rightPlayer, right, rightRoll, conflict.type());
    int difference = Math.abs(leftTotal - rightTotal);
    int inflicted = difference == 0 ? 0 : difference < 5 ? 1 : difference < 10 ? 2 : 3;
    AttackPlan loser = leftTotal == rightTotal ? null : leftTotal < rightTotal ? left : right;
    reports.add(
        new CombatRollReport(
            conflict.type(),
            left.playerId(),
            leftRoll,
            leftTotal,
            rightTotal,
            loser == right ? inflicted : 0));
    reports.add(
        new CombatRollReport(
            conflict.type(),
            right.playerId(),
            rightRoll,
            rightTotal,
            leftTotal,
            loser == left ? inflicted : 0));
    fatigue.add(
        new PendingFatigue(
            left.playerId(), List.copyOf(left.participatingUnitIds()), leftRoll == 1));
    fatigue.add(
        new PendingFatigue(
            right.playerId(), List.copyOf(right.participatingUnitIds()), rightRoll == 1));
    if (loser != null && inflicted > 0) {
      damage.add(
          new PendingDamage(
              loser.playerId(), List.copyOf(loser.participatingUnitIds()), null, inflicted));
      retreats.add(new PendingRetreat(loser.playerId(), loser.source(), false));
    } else {
      retreats.add(new PendingRetreat(left.playerId(), left.source(), false));
      retreats.add(new PendingRetreat(right.playerId(), right.source(), false));
    }
  }

  private int clashTotal(
      PlayerState player, AttackPlan plan, int roll, AttackConflictType conflictType) {
    if (conflictType == AttackConflictType.HERO_DUEL) {
      return roll + (player.hero.heroClass() == HeroClass.KNIGHT ? 2 : 0);
    }
    int power =
        armies.armyPower(
            player.units.stream()
                .filter(unit -> plan.participatingUnitIds().contains(unit.id()))
                .toList(),
            true,
            false);
    return roll
        + armies.calculateArmyBonus(power)
        + (plan.heroParticipates() && player.hero.heroClass() == HeroClass.KNIGHT ? 2 : 0);
  }

  private PlayerState ownerAt(GameState state, com.hexboundrealms.domain.map.HexCoordinate target) {
    return state.players.stream()
        .filter(player -> player.settlements.stream().anyMatch(s -> s.location().equals(target)))
        .findFirst()
        .orElse(null);
  }

  private PlayerState player(GameState state, UUID id) {
    return state.players.stream().filter(player -> player.id.equals(id)).findFirst().orElseThrow();
  }

  private int indexOf(List<UnitState> units, UUID id) {
    for (int i = 0; i < units.size(); i++) if (units.get(i).id().equals(id)) return i;
    return -1;
  }
}
