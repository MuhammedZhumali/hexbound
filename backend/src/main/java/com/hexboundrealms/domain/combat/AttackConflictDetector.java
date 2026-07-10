package com.hexboundrealms.domain.combat;

import java.util.*;

public final class AttackConflictDetector {
  public List<CombatConflict> detect(List<AttackPlan> plans) {
    List<CombatConflict> conflicts = new ArrayList<>();
    Set<AttackPlan> assigned = new HashSet<>();

    for (int i = 0; i < plans.size(); i++) {
      AttackPlan left = plans.get(i);
      if (assigned.contains(left)) continue;
      for (int j = i + 1; j < plans.size(); j++) {
        AttackPlan right = plans.get(j);
        if (assigned.contains(right)) continue;
        if (left.source().equals(right.target()) && left.target().equals(right.source())) {
          boolean duel =
              left.heroParticipates()
                  && right.heroParticipates()
                  && left.participatingUnitIds().isEmpty()
                  && right.participatingUnitIds().isEmpty();
          conflicts.add(
              new CombatConflict(
                  duel ? AttackConflictType.HERO_DUEL : AttackConflictType.RECIPROCAL_CLASH,
                  List.of(left, right),
                  left.target(),
                  duel ? "Hero-only reciprocal plans" : "Armies cross the same border"));
          assigned.add(left);
          assigned.add(right);
          break;
        }
      }
    }

    Map<Object, List<AttackPlan>> byTarget = new LinkedHashMap<>();
    plans.stream()
        .filter(plan -> !assigned.contains(plan))
        .forEach(
            plan ->
                byTarget.computeIfAbsent(plan.target(), ignored -> new ArrayList<>()).add(plan));
    for (List<AttackPlan> sameTarget : byTarget.values()) {
      if (sameTarget.size() > 1) {
        conflicts.add(
            new CombatConflict(
                AttackConflictType.MULTI_ATTACK,
                sameTarget,
                sameTarget.getFirst().target(),
                "Multiple attackers share one initial defense"));
        assigned.addAll(sameTarget);
      }
    }

    for (AttackPlan plan : plans) {
      if (assigned.contains(plan)) continue;
      boolean chain =
          plans.stream()
              .anyMatch(
                  other ->
                      other != plan
                          && plan.target().equals(other.source())
                          && !plan.source().equals(other.target()));
      conflicts.add(
          new CombatConflict(
              chain ? AttackConflictType.CHAIN_ATTACK : AttackConflictType.ONE_WAY_ASSAULT,
              List.of(plan),
              plan.target(),
              chain ? "Target launches another locked attack" : "Unopposed assault route"));
    }
    return List.copyOf(conflicts);
  }
}
