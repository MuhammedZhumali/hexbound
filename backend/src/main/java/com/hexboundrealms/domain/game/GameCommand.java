package com.hexboundrealms.domain.game;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.HexCoordinate;
import com.hexboundrealms.domain.map.ResourceType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public sealed interface GameCommand
    permits GameCommand.Start,
        GameCommand.SelectHero,
        GameCommand.ConfirmHero,
        GameCommand.CancelHeroSelection,
        GameCommand.StartStartingPlacement,
        GameCommand.PlaceStartingOutpost,
        GameCommand.PlaceStartingRoad,
        GameCommand.ProposeHeroSwap,
        GameCommand.AcceptHeroSwap,
        GameCommand.RollWorld,
        GameCommand.BuyMarketCard,
        GameCommand.SelectAction,
        GameCommand.BuildRoad,
        GameCommand.BuildOutpost,
        GameCommand.Recruit,
        GameCommand.Trade,
        GameCommand.BankTrade,
        GameCommand.ProposeTrade,
        GameCommand.AcceptTrade,
        GameCommand.RejectTrade,
        GameCommand.CancelTrade,
        GameCommand.Explore,
        GameCommand.MoveHero,
        GameCommand.Attack,
        GameCommand.LockAttackPlan,
        GameCommand.ResolveAttackBatch,
        GameCommand.Fortify,
        GameCommand.ResolveAction,
        GameCommand.EndRound,
        GameCommand.Debug {

  record Start() implements GameCommand {}

  record SelectHero(HeroClass heroClass) implements GameCommand {}

  record ConfirmHero() implements GameCommand {}

  record CancelHeroSelection() implements GameCommand {}

  record StartStartingPlacement() implements GameCommand {}

  record PlaceStartingOutpost(HexCoordinate at) implements GameCommand {}

  record PlaceStartingRoad(HexCoordinate to) implements GameCommand {}

  record ProposeHeroSwap(UUID targetPlayerId) implements GameCommand {}

  record AcceptHeroSwap(UUID proposalId) implements GameCommand {}

  record RollWorld(Integer forced) implements GameCommand {}

  record BuyMarketCard(UUID cardId) implements GameCommand {}

  record SelectAction(ActionType action) implements GameCommand {}

  record BuildRoad(HexCoordinate from, HexCoordinate to) implements GameCommand {}

  record BuildOutpost(HexCoordinate at) implements GameCommand {}

  record Recruit(UnitType unitType) implements GameCommand {}

  record Trade(ResourceType give, ResourceType receive) implements GameCommand {}

  record BankTrade(ResourceType give, ResourceType receive) implements GameCommand {}

  record ProposeTrade(
      UUID targetPlayerId,
      Resources offeredResources,
      Resources requestedResources,
      int offeredGold,
      int requestedGold) implements GameCommand {}

  record AcceptTrade(UUID proposalId) implements GameCommand {}

  record RejectTrade(UUID proposalId) implements GameCommand {}

  record CancelTrade(UUID proposalId) implements GameCommand {}

  record Explore(HexCoordinate target) implements GameCommand {}

  record MoveHero(HexCoordinate to) implements GameCommand {}

  record Attack(HexCoordinate target, ReactionType reaction) implements GameCommand {}

  record LockAttackPlan(
      HexCoordinate source,
      HexCoordinate target,
      Set<UUID> participatingUnitIds,
      boolean heroParticipates,
      Optional<UUID> selectedTacticCardId)
      implements GameCommand {}

  record ResolveAttackBatch() implements GameCommand {}

  record Fortify() implements GameCommand {}

  record ResolveAction() implements GameCommand {}

  record EndRound() implements GameCommand {}

  record Debug(String operation, Integer value) implements GameCommand {}
}
