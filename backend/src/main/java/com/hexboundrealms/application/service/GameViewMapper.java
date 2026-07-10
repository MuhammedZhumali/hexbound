package com.hexboundrealms.application.service;

import com.hexboundrealms.api.dto.Views.*;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.victory.SealEvaluator;
import java.util.*;

public final class GameViewMapper {
  private final SealEvaluator seals = new SealEvaluator();

  public PublicGameView publicView(GameState g) {
    boolean reveal = g.phase == GamePhase.REVEAL || g.phase == GamePhase.RESOLUTION;
    List<PlayerSummary> players =
        g.players.stream()
            .map(
                p ->
                    new PlayerSummary(
                        p.id,
                        p.displayName,
                        p.color,
                        p.heroConfirmed && p.hero != null ? p.hero.heroClass() : null,
                        p.glory.total(),
                        p.reputation,
                        p.actionLocked,
                        p.attackPlan != null,
                        reveal ? p.selectedAction : null,
                        List.copyOf(p.settlements),
                        List.copyOf(p.roads),
                        p.units.size(),
                        p.heroConfirmed ? p.hero : null))
            .toList();
    UUID first = g.players.isEmpty() ? null : g.players.get(g.firstPlayerIndex).id;
    boolean allAttackPlansLocked =
        g.phase == GamePhase.RESOLUTION
            && g.players.stream()
                .filter(p -> p.actionLocked && p.selectedAction == ActionType.ATTACK)
                .allMatch(p -> p.attackPlan != null);
    List<AttackPlanView> attackPlans =
        allAttackPlansLocked
            ? g.players.stream()
                .filter(p -> p.attackPlan != null)
                .map(
                    p ->
                        new AttackPlanView(
                            p.id,
                            p.attackPlan.source(),
                            p.attackPlan.target(),
                            p.attackPlan.participatingUnitIds().size(),
                            p.attackPlan.heroParticipates()))
                .toList()
            : List.of();
    return new PublicGameView(
        g.id,
        g.name,
        g.seed,
        g.debugMode,
        g.status,
        g.phase,
        g.roundNumber,
        g.version,
        g.lastRoll,
        first,
        List.copyOf(g.map),
        players,
        List.copyOf(g.monsters),
        List.copyOf(g.market),
        List.copyOf(g.eventLog),
        List.copyOf(g.winners),
        attackPlans,
        g.lastCombatReport == null ? List.of() : List.copyOf(g.lastCombatReport));
  }

  public PrivatePlayerView privateView(GameState g, PlayerState p) {
    return new PrivatePlayerView(
        publicView(g),
        p.id,
        p.accessToken,
        p.resources,
        p.hero,
        p.glory,
        p.reputation,
        p.selectedAction,
        p.previousAction,
        p.fortificationTokens,
        List.copyOf(p.settlements),
        List.copyOf(p.roads),
        List.copyOf(p.units),
        List.copyOf(p.hand),
        seals.active(g, p),
        p.temporaryHeroClass,
        p.attackPlan);
  }
}
