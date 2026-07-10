package com.hexboundrealms.domain.combat;

import com.hexboundrealms.domain.game.GameModel.ReactionType;

public final class CombatResolver {
  public record CombatInput(
      int d20,
      int heroAttack,
      int armyAttack,
      int tactical,
      int fatiguePenalty,
      int heroDefense,
      int armyDefense,
      int walls,
      int terrain,
      ReactionType reaction,
      int fortificationTokens) {}

  public record CombatResult(
      int roll,
      int attackTotal,
      int defenseTotal,
      int damage,
      boolean success,
      boolean counterDamage,
      boolean strongRetaliation) {}

  public CombatResult resolve(CombatInput in) {
    int reactionDefense =
        in.reaction() == ReactionType.SHIELD
            ? 4
            : in.reaction() == ReactionType.COUNTERATTACK ? 1 : 0;
    int attack = in.d20() + in.heroAttack() + in.armyAttack() + in.tactical() - in.fatiguePenalty();
    int defense =
        10
            + in.heroDefense()
            + in.armyDefense()
            + in.walls()
            + in.terrain()
            + reactionDefense
            + in.fortificationTokens() * 2;
    if (in.d20() == 1) return new CombatResult(1, attack, defense, 0, false, false, false);
    int margin = attack - defense;
    int damage = margin < 0 ? 0 : margin < 5 ? 1 : margin < 10 ? 2 : 3;
    if (in.d20() == 20) damage++;
    int failure = defense - attack;
    boolean counter = in.reaction() == ReactionType.COUNTERATTACK && failure >= 3;
    boolean strong =
        (in.reaction() == ReactionType.COUNTERATTACK && failure >= 6)
            || (in.reaction() == ReactionType.AMBUSH && failure >= 5);
    return new CombatResult(in.d20(), attack, defense, damage, damage > 0, counter, strong);
  }
}
