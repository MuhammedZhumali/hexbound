package com.hexboundrealms.api.dto;

import com.hexboundrealms.domain.combat.AttackPlan;
import com.hexboundrealms.domain.game.DomainEvent;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.HexCoordinate;
import com.hexboundrealms.domain.map.MapHex;
import java.util.*;

public final class Views {
  private Views() {}

  public record PlayerSummary(
      UUID id,
      String displayName,
      PlayerColor color,
      HeroClass heroClass,
      int glory,
      int reputation,
      boolean hasSelectedAction,
      boolean hasLockedAttackPlan,
      ActionType revealedAction,
      List<SettlementState> settlements,
      List<RoadState> roads,
      int unitCount,
      HeroState hero) {}

  public record PublicGameView(
      UUID id,
      String name,
      long seed,
      boolean debugMode,
      GameStatus status,
      GamePhase phase,
      int roundNumber,
      long version,
      Integer lastRoll,
      UUID firstPlayerId,
      List<MapHex> map,
      List<PlayerSummary> players,
      List<MonsterState> monsters,
      List<Card> market,
      List<String> eventLog,
      List<UUID> winners,
      List<AttackPlanView> revealedAttackPlans,
      List<CombatReportEntry> combatReport) {}

  public record PrivatePlayerView(
      PublicGameView game,
      UUID playerId,
      String accessToken,
      Resources resources,
      HeroState hero,
      GloryState glory,
      int reputation,
      ActionType selectedAction,
      ActionType previousAction,
      int fortificationTokens,
      List<SettlementState> settlements,
      List<RoadState> roads,
      List<UnitState> units,
      List<Card> hand,
      Set<PathSeal> activeSeals,
      HeroClass temporaryHeroClass,
      AttackPlan attackPlan) {}

  public record AttackPlanView(
      UUID playerId,
      HexCoordinate source,
      HexCoordinate target,
      int participatingUnitCount,
      boolean heroParticipates) {}

  public record HeroDraftView(
      GamePhase phase,
      List<UUID> initialTurnOrder,
      List<UUID> draftOrder,
      UUID currentDraftPlayerId,
      List<HeroClass> availableHeroClasses,
      Map<UUID, HeroClass> confirmedSelections,
      boolean allowDuplicateHeroClasses) {}

  public record JoinResult(UUID playerId, String accessToken, long version) {}

  public record ServerEvent(
      UUID gameId,
      long version,
      long eventSequence,
      List<DomainEvent> events,
      PublicGameView state) {}

  public record LegalActions(
      List<String> actions,
      List<String> movementTargets,
      List<String> buildTargets,
      List<String> attackTargets) {}
}
