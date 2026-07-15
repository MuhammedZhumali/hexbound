package com.hexboundrealms.domain.game;

import com.hexboundrealms.domain.army.ArmyRules;
import com.hexboundrealms.domain.army.FatigueResolver;
import com.hexboundrealms.domain.combat.AttackPlan;
import com.hexboundrealms.domain.combat.CombatResolutionBatch;
import com.hexboundrealms.domain.combat.CombatResolver;
import com.hexboundrealms.domain.combat.SimultaneousCombatResolver;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import com.hexboundrealms.domain.monster.MonsterEventResolver;
import com.hexboundrealms.domain.production.ProductionResolver;
import com.hexboundrealms.domain.settlement.BuildingValidator;
import com.hexboundrealms.domain.victory.VictoryEvaluator;
import java.util.*;

public final class DefaultGameEngine implements GameEngine {
  private final ProductionResolver production = new ProductionResolver();
  private final MonsterEventResolver monsters = new MonsterEventResolver();
  private final BuildingValidator building = new BuildingValidator();
  private final VictoryEvaluator victory = new VictoryEvaluator();

  @Override
  public CommandResult execute(GameState game, UUID playerId, GameCommand command) {
    List<DomainEvent> events = new ArrayList<>();
    PlayerState player =
        playerId == null
            ? null
            : game.players.stream()
                .filter(p -> p.id.equals(playerId))
                .findFirst()
                .orElseThrow(() -> DomainException.of("INVALID_PLAYER", "Unknown player"));
    switch (command) {
      case GameCommand.Start ignored -> start(game, events);
      case GameCommand.SelectHero c -> selectHero(game, player, c, events);
      case GameCommand.ConfirmHero ignored -> confirmHero(game, player, events);
      case GameCommand.CancelHeroSelection ignored -> cancelHero(game, player, events);
      case GameCommand.StartStartingPlacement ignored -> startingPlacement(game, events);
      case GameCommand.PlaceStartingOutpost c -> placeStartingOutpost(game, player, c, events);
      case GameCommand.PlaceStartingRoad c -> placeStartingRoad(game, player, c, events);
      case GameCommand.ProposeHeroSwap c -> proposeHeroSwap(game, player, c, events);
      case GameCommand.AcceptHeroSwap c -> acceptHeroSwap(game, player, c, events);
      case GameCommand.RollWorld c -> roll(game, player, c, events);
      case GameCommand.BuyMarketCard c -> buyMarketCard(game, player, c, events);
      case GameCommand.SelectAction c -> select(game, player, c, events);
      case GameCommand.BuildRoad c -> buildRoad(game, player, c, events);
      case GameCommand.BuildOutpost c -> buildOutpost(game, player, c, events);
      case GameCommand.Recruit c -> recruit(game, player, c, events);
      case GameCommand.Trade c -> trade(game, player, c, events);
      case GameCommand.BankTrade c -> bankTrade(game, player, c, events);
      case GameCommand.MarketDeal c -> marketDeal(game, player, c, events);
      case GameCommand.ProposeTrade c -> proposeTrade(game, player, c, events);
      case GameCommand.AcceptTrade c -> acceptTrade(game, player, c, events);
      case GameCommand.RejectTrade c -> closeTrade(game, player, c.proposalId(), TradeStatus.REJECTED, events);
      case GameCommand.CancelTrade c -> closeTrade(game, player, c.proposalId(), TradeStatus.CANCELLED, events);
      case GameCommand.Explore c -> explore(game, player, c, events);
      case GameCommand.DeepExplore c -> deepExplore(game, player, c, events);
      case GameCommand.MoveHero c -> move(game, player, c, events);
      case GameCommand.SwiftMove c -> swiftMove(game, player, c, events);
      case GameCommand.Attack c -> attack(game, player, c, events);
      case GameCommand.SmallRaid c -> smallRaid(game, player, c, events);
      case GameCommand.DefenderReaction c -> defenderReaction(game, player, c, events);
      case GameCommand.PriestHeal c -> priestHeal(game, player, c, events);
      case GameCommand.PriestBless c -> priestBless(game, player, c, events);
      case GameCommand.PriestSanctuary c -> priestSanctuary(game, player, c, events);
      case GameCommand.ArcaneBolt c -> arcaneBolt(game, player, c, events);
      case GameCommand.MageWard c -> mageWard(game, player, c, events);
      case GameCommand.MageReveal c -> mageReveal(game, player, c, events);
      case GameCommand.Transmute c -> transmute(game, player, c, events);
      case GameCommand.Scout c -> scout(game, player, c, events);
      case GameCommand.QuickRoad c -> quickRoad(game, player, c, events);
      case GameCommand.Repair c -> repair(game, player, c, events);
      case GameCommand.LockAttackPlan c -> lockAttackPlan(game, player, c, events);
      case GameCommand.ResolveAttackBatch ignored -> resolveAttackBatch(game, player, events);
      case GameCommand.Fortify ignored -> fortify(game, player, events);
      case GameCommand.ResolveAction ignored -> resolveOrAdvance(game, player, events);
      case GameCommand.EndRound ignored -> endRound(game, events);
      case GameCommand.Debug c -> debug(game, player, c, events);
    }
    List<PlayerState> qualified = victory.qualified(game);
    if (!qualified.isEmpty() && game.phase == GamePhase.END_ROUND) {
      game.winners = qualified.stream().map(p -> p.id).toList();
      game.phase = GamePhase.GAME_OVER;
      game.status = GameStatus.FINISHED;
      events.add(event("GAME_OVER", "winners", game.winners));
    }
    game.eventLog.addAll(
        events.stream()
            .map(event -> publicLogMessage(game, event))
            .filter(message -> message != null && !message.isBlank())
            .toList());
    return new CommandResult(game, List.copyOf(events));
  }

  private void start(GameState g, List<DomainEvent> e) {
    phase(g, GamePhase.SETUP);
    if (g.players.isEmpty() || g.players.size() > 4)
      throw DomainException.of("INVALID_PLAYER_COUNT", "Hexbound Realms needs 1-4 players");
    List<UUID> initialOrder = new ArrayList<>(g.players.stream().map(p -> p.id).toList());
    Collections.shuffle(initialOrder, new Random(g.seed));
    List<UUID> draftOrder = new ArrayList<>(initialOrder);
    Collections.reverse(draftOrder);
    g.order = new GameOrder(List.copyOf(initialOrder), List.copyOf(draftOrder));
    g.players.sort(Comparator.comparingInt(p -> initialOrder.indexOf(p.id)));
    g.firstPlayerIndex = 0;
    g.currentHeroDraftIndex = 0;
    g.status = GameStatus.ACTIVE;
    g.phase = GamePhase.HERO_SELECTION;
    e.add(new DomainEvent("HERO_SELECTION_STARTED", Map.of("turnOrder", initialOrder)));
  }

  private void selectHero(
      GameState g, PlayerState p, GameCommand.SelectHero c, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_SELECTION);
    if (p.heroConfirmed) {
      throw DomainException.of(
          "HERO_ALREADY_CONFIRMED", "Confirmed Hero cannot be changed directly");
    }
    p.temporaryHeroClass = c.heroClass();
    e.add(event("HERO_TEMPORARILY_SELECTED", "playerId", p.id));
  }

  private void confirmHero(GameState g, PlayerState p, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_SELECTION);
    if (p.temporaryHeroClass == null) {
      throw DomainException.of("HERO_NOT_SELECTED", "Select a Hero before confirmation");
    }
    p.hero = HeroState.create(p.temporaryHeroClass, null);
    p.temporaryHeroClass = null;
    p.heroConfirmed = true;
    e.add(
        new DomainEvent(
            "HERO_CONFIRMED", Map.of("playerId", p.id, "heroClass", p.hero.heroClass())));
    if (g.players.stream().allMatch(player -> player.heroConfirmed)) {
      g.phase = GamePhase.HERO_REVEAL;
      e.add(event("HEROES_REVEALED", "count", g.players.size()));
    }
  }

  private void cancelHero(GameState g, PlayerState p, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_SELECTION);
    if (p.heroConfirmed) {
      throw DomainException.of("HERO_ALREADY_CONFIRMED", "Confirmed Hero cannot be cancelled");
    }
    p.temporaryHeroClass = null;
    e.add(event("HERO_SELECTION_CANCELLED", "playerId", p.id));
  }

  private void startingPlacement(GameState g, List<DomainEvent> e) {
    if (g.phase != GamePhase.HERO_REVEAL && g.phase != GamePhase.STARTING_PLACEMENT)
      throw DomainException.of("INVALID_PHASE", "Expected HERO_REVEAL before starting placement");
    if (g.players.stream().anyMatch(player -> !player.heroConfirmed || player.hero == null)) {
      throw DomainException.of("HERO_DRAFT_INCOMPLETE", "Every player must confirm a Hero");
    }
    if (g.players.stream().anyMatch(player -> !player.settlements.isEmpty())) {
      throw DomainException.of("PLACEMENT_ALREADY_STARTED", "Starting placement is already underway");
    }
    g.status = GameStatus.ACTIVE;
    g.phase = GamePhase.STARTING_PLACEMENT;
    g.startingPlacementStep = StartingPlacementStep.OUTPOST;
    g.currentStartingPlacementIndex = 0;
    e.add(event("STARTING_PLACEMENT_STARTED", "playerId", placementPlayerId(g)));
  }

  private void placeStartingOutpost(
      GameState g, PlayerState p, GameCommand.PlaceStartingOutpost c, List<DomainEvent> e) {
    phase(g, GamePhase.STARTING_PLACEMENT);
    requirePlacementTurn(g, p, StartingPlacementStep.OUTPOST);
    if (!startingOutpostLegal(g, c.at())) {
      throw DomainException.of("INVALID_STARTING_OUTPOST", "Starting Outpost is not legal");
    }
    p.settlements.add(new SettlementState(UUID.randomUUID(), c.at(), SettlementLevel.OUTPOST, 2));
    p.hero = new HeroState(
        p.hero.heroClass(), p.hero.hp(), p.hero.mana(), p.hero.grace(), c.at(), false);
    e.add(new DomainEvent("STARTING_OUTPOST_PLACED", Map.of("playerId", p.id, "at", c.at())));
    g.currentStartingPlacementIndex++;
    if (g.currentStartingPlacementIndex == g.players.size()) {
      g.startingPlacementStep = StartingPlacementStep.ROAD;
      g.currentStartingPlacementIndex = 0;
    }
    e.add(event("STARTING_PLACEMENT_TURN_CHANGED", "playerId", placementPlayerId(g)));
  }

  private void placeStartingRoad(
      GameState g, PlayerState p, GameCommand.PlaceStartingRoad c, List<DomainEvent> e) {
    phase(g, GamePhase.STARTING_PLACEMENT);
    requirePlacementTurn(g, p, StartingPlacementStep.ROAD);
    HexCoordinate from = p.settlements.getFirst().location();
    MapHex target = hex(g, c.to());
    boolean occupiedByHostile = g.monsters.stream().anyMatch(m -> m.location().equals(c.to()));
    boolean edgeUsed = g.players.stream().flatMap(x -> x.roads.stream()).anyMatch(
        r -> (r.from().equals(from) && r.to().equals(c.to()))
            || (r.to().equals(from) && r.from().equals(c.to())));
    if (from.distanceTo(c.to()) != 1 || target == null || occupiedByHostile || edgeUsed) {
      throw DomainException.of("INVALID_STARTING_ROAD", "Road must reach an adjacent valid hex");
    }
    p.roads.add(new RoadState(UUID.randomUUID(), from, c.to()));
    p.units.add(new UnitState(UUID.randomUUID(), UnitType.MILITIA, FatigueState.READY, false, true, 0));
    e.add(new DomainEvent(
        "STARTING_ROAD_PLACED", Map.of("playerId", p.id, "from", from, "to", c.to())));
    g.currentStartingPlacementIndex++;
    if (g.currentStartingPlacementIndex == g.players.size()) {
      g.startingPlacementStep = StartingPlacementStep.COMPLETE;
      g.roundNumber = 1;
      g.phase = GamePhase.WORLD_ROLL;
      e.add(event("GAME_STARTED", "round", 1));
    } else {
      e.add(event("STARTING_PLACEMENT_TURN_CHANGED", "playerId", placementPlayerId(g)));
    }
  }

  private boolean startingOutpostLegal(GameState g, HexCoordinate at) {
    MapHex hex = hex(g, at);
    if (hex == null || !isBuildableStartingTerrain(hex.terrain())) return false;
    if (g.players.stream().flatMap(p -> p.settlements.stream()).anyMatch(s -> s.location().equals(at))) return false;
    if (g.monsters.stream().anyMatch(m -> m.location().equals(at))) return false;
    return g.players.stream().flatMap(p -> p.settlements.stream())
        .allMatch(s -> s.location().distanceTo(at) >= 2);
  }

  private boolean isBuildableStartingTerrain(TerrainType terrain) {
    return terrain == TerrainType.FOREST || terrain == TerrainType.FIELD
        || terrain == TerrainType.MOUNTAIN || terrain == TerrainType.QUARRY
        || terrain == TerrainType.TRADE_LAND;
  }

  private void requirePlacementTurn(GameState g, PlayerState p, StartingPlacementStep step) {
    if (g.startingPlacementStep != step)
      throw DomainException.of("INVALID_PLACEMENT_STEP", "Expected starting " + step);
    if (!placementPlayerId(g).equals(p.id))
      throw DomainException.of("NOT_PLAYER_TURN", "Another player must place now");
  }

  private UUID placementPlayerId(GameState g) {
    List<UUID> order = g.order.initialTurnOrder();
    int index = g.startingPlacementStep == StartingPlacementStep.ROAD
        ? order.size() - 1 - g.currentStartingPlacementIndex
        : g.currentStartingPlacementIndex;
    return order.get(index);
  }

  private MapHex hex(GameState g, HexCoordinate at) {
    return g.map.stream().filter(h -> h.coordinate().equals(at)).findFirst().orElse(null);
  }

  private void requireDraftTurn(GameState g, PlayerState p) {
    UUID expected = g.order.heroDraftOrder().get(g.currentHeroDraftIndex);
    if (!expected.equals(p.id)) {
      throw DomainException.of("NOT_PLAYER_TURN", "Another player is currently drafting");
    }
  }

  private void proposeHeroSwap(
      GameState g, PlayerState p, GameCommand.ProposeHeroSwap c, List<DomainEvent> e) {
    if (g.phase != GamePhase.HERO_REVEAL && g.phase != GamePhase.STARTING_PLACEMENT)
      throw DomainException.of("INVALID_PHASE", "Hero swaps are only available before placement");
    if (g.phase == GamePhase.STARTING_PLACEMENT)
      throw DomainException.of("PLACEMENT_ALREADY_STARTED", "Heroes cannot swap after placement begins");
    PlayerState target =
        g.players.stream()
            .filter(player -> player.id.equals(c.targetPlayerId()))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_PLAYER", "Swap target not found"));
    if (p.hero == null || target.hero == null) {
      throw DomainException.of("HERO_DRAFT_INCOMPLETE", "Both Heroes must be confirmed");
    }
    HeroSwapProposal proposal =
        new HeroSwapProposal(
            UUID.randomUUID(),
            p.id,
            target.id,
            target.hero.heroClass(),
            p.hero.heroClass(),
            true,
            false);
    g.heroSwapProposals.add(proposal);
    e.add(event("HERO_SWAP_PROPOSED", "proposalId", proposal.proposalId()));
  }

  private void acceptHeroSwap(
      GameState g, PlayerState p, GameCommand.AcceptHeroSwap c, List<DomainEvent> e) {
    if (g.phase != GamePhase.HERO_REVEAL && g.phase != GamePhase.STARTING_PLACEMENT)
      throw DomainException.of("INVALID_PHASE", "Hero swaps are only available before placement");
    HeroSwapProposal proposal =
        g.heroSwapProposals.stream()
            .filter(item -> item.proposalId().equals(c.proposalId()))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_TARGET", "Swap proposal not found"));
    if (!proposal.targetPlayerId().equals(p.id)) {
      throw DomainException.of("NOT_PLAYER_TURN", "Only the target player may accept this swap");
    }
    PlayerState requester =
        g.players.stream()
            .filter(player -> player.id.equals(proposal.requestingPlayerId()))
            .findFirst()
            .orElseThrow();
    HeroClass requesterHero = requester.hero.heroClass();
    requester.hero = HeroState.create(p.hero.heroClass(), null);
    p.hero = HeroState.create(requesterHero, null);
    g.heroSwapProposals.remove(proposal);
    e.add(event("HERO_SWAP_ACCEPTED", "proposalId", proposal.proposalId()));
  }

  private void roll(GameState g, PlayerState p, GameCommand.RollWorld c, List<DomainEvent> e) {
    phase(g, GamePhase.WORLD_ROLL);
    if (!g.players.get(g.firstPlayerIndex).id.equals(p.id))
      throw DomainException.of("NOT_PLAYER_TURN", "Only the first player rolls");
    int roll =
        c.forced() != null && g.debugMode
            ? c.forced()
            : g.forced2d6 != null ? g.forced2d6 : die(g, 6) + die(g, 6);
    g.forced2d6 = null;
    if (roll < 2 || roll > 12) throw DomainException.of("INVALID_DICE", "2d6 must be 2–12");
    g.lastRoll = roll;
    if (roll == 7) {
      g.phase = GamePhase.MONSTER_EVENT;
      MonsterState monster = monsters.spawn(g, p);
      e.add(event("MONSTER_SPAWNED", "monsterId", monster.id()));
      monsterAttack(g, monster, p, e);
    } else {
      ProductionResolver.ProductionReport report =
          production.resolve(g, roll, g.gameMode == GameMode.BEGINNER);
      g.phase = GamePhase.PRODUCTION;
      e.add(
          new DomainEvent(
              "PRODUCTION_RESOLVED", Map.of("roll", roll, "production", report.production())));
    }
  }

  private void monsterAttack(GameState g, MonsterState monster, PlayerState target, List<DomainEvent> e) {
    if (target.settlements.isEmpty()) return;
    SettlementState attacked =
        target.settlements.stream()
            .min(Comparator.comparingInt(s -> s.location().distanceTo(monster.location())))
            .orElseThrow();
    int roll = die(g, 20);
    int monsterBonus = Math.max(0, monster.strength() - 10);
    int defenseTotal = 10 + target.fortificationTokens * 2;
    int attackTotal = roll + monsterBonus;
    int margin = attackTotal - defenseTotal;
    int damage = margin < 0 ? 0 : margin < 5 ? 1 : 2;
    if (roll == 20) damage++;
    if (roll == 1) damage = 0;
    applySettlementDamage(target, attacked.location(), damage);
    g.lastCombatReport =
        List.of(
            new CombatReportEntry(
                null,
                target.id,
                monster.id(),
                monster.location(),
                attacked.location(),
                "MONSTER_ATTACK",
                roll,
                null,
                attackTotal,
                defenseTotal,
                damage,
                0,
                damage,
                0));
    e.add(
        new DomainEvent(
            "MONSTER_ATTACKED",
            Map.of(
                "monsterId",
                monster.id(),
                "monsterType",
                monster.type(),
                "playerId",
                target.id,
                "target",
                attacked.location(),
                "roll",
                roll,
                "attackTotal",
                attackTotal,
                "defenseTotal",
                defenseTotal,
                "damage",
                damage)));
  }

  private void buyMarketCard(
      GameState g, PlayerState p, GameCommand.BuyMarketCard c, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    if (turn) {
      requireCurrentTurn(g, p);
      int cost = p.selectedAction == ActionType.TRADE && !p.freeTradeCardBuyUsed ? 0 : 1;
      spendActionPoints(p, cost);
      if (cost == 0) p.freeTradeCardBuyUsed = true;
    } else {
      phase(g, GamePhase.MARKET);
    }
    Card card =
        g.market.stream()
            .filter(item -> item.id().equals(c.cardId()))
            .findFirst()
            .orElseThrow(
                () -> DomainException.of("INVALID_TARGET", "Market card is not available"));
    try {
      p.resources = p.resources.subtract(card.cost());
    } catch (IllegalArgumentException ex) {
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Not enough resources to buy this card");
    }
    p.hand.add(card);
    g.market.remove(card);
    if (!g.deck.isEmpty()) {
      g.market.add(g.deck.removeFirst());
    }
    e.add(
        new DomainEvent(
            "MARKET_CARD_BOUGHT",
            Map.of(
                "playerId",
                p.id,
                "cardId",
                card.id(),
                "cardName",
                card.name(),
                "category",
                card.category(),
                "cost",
                card.cost())));
  }

  private void select(GameState g, PlayerState p, GameCommand.SelectAction c, List<DomainEvent> e) {
    if (g.phase != GamePhase.ACTION_CARD_SELECTION && g.phase != GamePhase.PLANNING)
      throw DomainException.of("INVALID_PHASE", "Action Cards are selected after negotiation");
    if (p.actionLocked) throw DomainException.of("ACTION_LOCKED", "Action already locked");
    if (c.action() == p.previousAction)
      throw DomainException.of("ACTION_ON_COOLDOWN", "Choose a different action this round");
    p.selectedAction = c.action();
    p.actionLocked = true;
    e.add(event("ACTION_CARD_SELECTED", "playerId", p.id));
    if (g.players.stream().allMatch(x -> x.actionLocked)) {
      g.phase = GamePhase.ACTION_CARD_REVEAL;
      e.add(
          new DomainEvent(
              "ACTION_CARDS_REVEALED",
              Map.of(
                  "actions",
                  g.players.stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              x -> x.id, x -> x.selectedAction.name())),
                  "turnOrder",
                  actionTurnOrder(g))));
    }
  }

  private void buildRoad(GameState g, PlayerState p, GameCommand.BuildRoad c, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    Resources cost = new Resources(1, 0, 0, 1, 0);
    if (turn) {
      requireCurrentTurn(g, p);
      requireResources(p, cost);
      if (!building.roadLegal(g, p, c.from(), c.to()))
        throw DomainException.of("INVALID_TARGET", "Road must extend your connected network");
      spendActionPoints(p, roadActionPointCost(p));
    } else {
      requireResources(p, cost);
      if (!building.roadLegal(g, p, c.from(), c.to()))
        throw DomainException.of("INVALID_TARGET", "Road must extend your connected network");
      resolutionAction(g, p, ActionType.BUILD);
    }
    spendResources(p, cost);
    p.roads.add(new RoadState(UUID.randomUUID(), c.from(), c.to()));
    if (turn)
      e.add(
          new DomainEvent(
              "ROAD_BUILT", Map.of("playerId", p.id, "from", c.from(), "to", c.to())));
    else finishAction(g, p, e, "ROAD_BUILT");
  }

  private void buildOutpost(
      GameState g, PlayerState p, GameCommand.BuildOutpost c, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    Resources cost = new Resources(1, 1, 0, 1, 0);
    if (turn) {
      requireCurrentTurn(g, p);
      requireResources(p, cost);
      if (!building.outpostLegal(g, p, c.at()))
        throw DomainException.of("INVALID_TARGET", "Outpost target is not legal");
      spendActionPoints(p, p.selectedAction == ActionType.BUILD ? 1 : 2);
    } else {
      requireResources(p, cost);
      if (!building.outpostLegal(g, p, c.at()))
        throw DomainException.of("INVALID_TARGET", "Outpost target is not legal");
      resolutionAction(g, p, ActionType.BUILD);
    }
    spendResources(p, cost);
    p.settlements.add(new SettlementState(UUID.randomUUID(), c.at(), SettlementLevel.OUTPOST, 2));
    if (turn)
      e.add(new DomainEvent("OUTPOST_BUILT", Map.of("playerId", p.id, "at", c.at())));
    else finishAction(g, p, e, "OUTPOST_BUILT");
  }

  private void recruit(GameState g, PlayerState p, GameCommand.Recruit c, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    Resources cost =
        switch (c.unitType()) {
          case MILITIA -> new Resources(0, 1, 0, 0, 0);
          case INFANTRY -> new Resources(0, 1, 1, 0, 0);
          case ARCHER -> new Resources(1, 1, 0, 0, 0);
          case CAVALRY -> new Resources(0, 2, 0, 0, 1);
          case MERCENARY -> new Resources(0, 0, 0, 0, 2);
        };
    if (turn) {
      requireCurrentTurn(g, p);
      requireResources(p, cost);
      spendActionPoints(p, recruitActionPointCost(p, c.unitType()));
    } else {
      requireResources(p, cost);
      resolutionAction(g, p, ActionType.RECRUIT);
    }
    spendResources(p, cost);
    p.units.add(
        new UnitState(
            UUID.randomUUID(),
            c.unitType(),
            FatigueState.READY,
            false,
            false,
            c.unitType() == UnitType.MERCENARY ? g.roundNumber + 1 : 0));
    if (turn)
      e.add(new DomainEvent("UNIT_RECRUITED", Map.of("playerId", p.id, "unitType", c.unitType())));
    else finishAction(g, p, e, "UNIT_RECRUITED");
  }

  private void trade(GameState g, PlayerState p, GameCommand.Trade c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.TRADE);
    int rate = p.hero.heroClass() == HeroClass.MERCHANT ? 2 : 3;
    executeBankTrade(p, c.give(), c.receive(), rate);
    e.add(
        new DomainEvent(
            "TRADE_COMPLETED",
            Map.of("playerId", p.id, "gave", c.give(), "amount", rate, "received", c.receive())));
    finishAction(g, p, e, "TRADE_RESOLVED");
  }

  private void bankTrade(GameState g, PlayerState p, GameCommand.BankTrade c, List<DomainEvent> e) {
    if (g.phase != GamePhase.PLAYER_TURNS) {
      throw DomainException.of("INVALID_PHASE", "Bank trades happen during a Player Turn");
    }
    requireCurrentTurn(g, p);
    spendActionPoints(p, 1);
    int rate = p.selectedAction == ActionType.TRADE
        ? p.hero.heroClass() == HeroClass.MERCHANT ? 2 : 3
        : 4;
    executeBankTrade(p, c.give(), c.receive(), rate);
    e.add(new DomainEvent(
        "BANK_TRADE_COMPLETED",
        Map.of("playerId", p.id, "gave", c.give(), "amount", rate, "received", c.receive())));
  }

  private void executeBankTrade(PlayerState p, ResourceType give, ResourceType receive, int rate) {
    if (give == receive) throw DomainException.of("INVALID_TARGET", "Trade resources must be different");
    try {
      p.resources = p.resources.subtract(resourceCost(give, rate)).add(receive, 1);
    } catch (IllegalArgumentException ex) {
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Not enough resources for bank trade");
    }
  }

  private void proposeTrade(
      GameState g, PlayerState p, GameCommand.ProposeTrade c, List<DomainEvent> e) {
    requireTradePhase(g);
    if (g.phase == GamePhase.PLAYER_TURNS) {
      requireCurrentTurn(g, p);
    }
    PlayerState target = g.players.stream().filter(x -> x.id.equals(c.targetPlayerId())).findFirst()
        .orElseThrow(() -> DomainException.of("INVALID_PLAYER", "Trade target not found"));
    if (target == p) throw DomainException.of("INVALID_TARGET", "Cannot trade with yourself");
    if (c.offeredGold() < 0 || c.requestedGold() < 0)
      throw DomainException.of("INVALID_PAYLOAD", "Gold amounts cannot be negative");
    if (isEmpty(c.offeredResources(), c.offeredGold()) && isEmpty(c.requestedResources(), c.requestedGold()))
      throw DomainException.of("INVALID_PAYLOAD", "Trade must exchange something");
    TradeProposal proposal;
    try {
      proposal = new TradeProposal(
          UUID.randomUUID(), p.id, target.id, c.offeredResources(), c.requestedResources(),
          c.offeredGold(), c.requestedGold(), TradeStatus.PENDING);
    } catch (IllegalArgumentException ex) {
      throw DomainException.of("INVALID_PAYLOAD", ex.getMessage());
    }
    g.tradeProposals.add(proposal);
    e.add(event("TRADE_PROPOSED", "proposalId", proposal.proposalId()));
  }

  private void acceptTrade(
      GameState g, PlayerState p, GameCommand.AcceptTrade c, List<DomainEvent> e) {
    requireTradePhase(g);
    TradeProposal proposal = pendingTrade(g, c.proposalId());
    if (!proposal.targetPlayerId().equals(p.id))
      throw DomainException.of("NOT_PLAYER_TURN", "Only the target player may accept");
    PlayerState proposer = player(g, proposal.proposerPlayerId());
    Resources offered = withAdditionalGold(proposal.offeredResources(), proposal.offeredGold());
    Resources requested = withAdditionalGold(proposal.requestedResources(), proposal.requestedGold());
    if (!proposer.resources.covers(offered) || !p.resources.covers(requested))
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Trade holdings changed before acceptance");
    proposer.resources = proposer.resources.subtract(offered).plus(requested);
    p.resources = p.resources.subtract(requested).plus(offered);
    replaceTrade(g, proposal.withStatus(TradeStatus.ACCEPTED));
    e.add(event("PLAYER_TRADE_COMPLETED", "proposalId", proposal.proposalId()));
  }

  private void closeTrade(
      GameState g, PlayerState p, UUID proposalId, TradeStatus status, List<DomainEvent> e) {
    requireTradePhase(g);
    TradeProposal proposal = pendingTrade(g, proposalId);
    UUID authorized = status == TradeStatus.CANCELLED
        ? proposal.proposerPlayerId() : proposal.targetPlayerId();
    if (!authorized.equals(p.id))
      throw DomainException.of("NOT_PLAYER_TURN", "Player cannot close this proposal");
    replaceTrade(g, proposal.withStatus(status));
    e.add(event("TRADE_" + status.name(), "proposalId", proposalId));
  }

  private void requireTradePhase(GameState g) {
    if (g.phase != GamePhase.NEGOTIATION && g.phase != GamePhase.TRADE_NEGOTIATION
        && g.phase != GamePhase.PLAYER_TURNS
        && !(g.phase == GamePhase.RESOLUTION))
      throw DomainException.of("INVALID_PHASE", "Trades are not open now");
  }

  private TradeProposal pendingTrade(GameState g, UUID id) {
    return g.tradeProposals.stream()
        .filter(t -> t.proposalId().equals(id) && t.status() == TradeStatus.PENDING)
        .findFirst().orElseThrow(() -> DomainException.of("INVALID_TARGET", "Pending trade not found"));
  }

  private void replaceTrade(GameState g, TradeProposal replacement) {
    g.tradeProposals.replaceAll(t -> t.proposalId().equals(replacement.proposalId()) ? replacement : t);
  }

  private boolean isEmpty(Resources resources, int extraGold) {
    return resources.equals(Resources.none()) && extraGold == 0;
  }

  private Resources withAdditionalGold(Resources resources, int gold) {
    return resources.add(ResourceType.GOLD, gold);
  }

  private int resourceAmount(Resources resources, ResourceType type) {
    return switch (type) {
      case WOOD -> resources.wood();
      case FOOD -> resources.food();
      case ORE -> resources.ore();
      case STONE -> resources.stone();
      case GOLD -> resources.gold();
    };
  }

  private void spendActionPoints(PlayerState p, int cost) {
    if (cost < 0) throw DomainException.of("INVALID_ACTION_COST", "Action Point cost cannot be negative");
    if (p.basicActionPoints < cost)
      throw DomainException.of("NO_ACTION_POINTS", "Not enough Action Points remain");
    p.basicActionPoints -= cost;
  }

  private void spendResources(PlayerState p, Resources cost) {
    try {
      p.resources = p.resources.subtract(cost);
    } catch (IllegalArgumentException ex) {
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Not enough resources");
    }
  }

  private void requireResources(PlayerState p, Resources cost) {
    if (!p.resources.covers(cost)) {
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Not enough resources");
    }
  }

  private void spendMana(PlayerState p, int amount) {
    if (p.hero == null || p.hero.mana() < amount)
      throw DomainException.of("INSUFFICIENT_MANA", "Not enough Mana");
    p.hero =
        new HeroState(
            p.hero.heroClass(),
            p.hero.hp(),
            p.hero.mana() - amount,
            p.hero.grace(),
            p.hero.location(),
            p.hero.defeated());
  }

  private void spendGrace(PlayerState p, int amount) {
    if (p.hero == null || p.hero.grace() < amount)
      throw DomainException.of("INSUFFICIENT_GRACE", "Not enough Grace");
    p.hero =
        new HeroState(
            p.hero.heroClass(),
            p.hero.hp(),
            p.hero.mana(),
            p.hero.grace() - amount,
            p.hero.location(),
            p.hero.defeated());
  }

  private void requireHero(PlayerState p, HeroClass heroClass) {
    if (p.hero == null || p.hero.heroClass() != heroClass)
      throw DomainException.of("INVALID_HERO", heroClass + " action is not available");
  }

  private void requireTurnAction(GameState g, PlayerState p) {
    if (g.phase != GamePhase.PLAYER_TURNS)
      throw DomainException.of("INVALID_PHASE", "Action must happen during player turns");
    requireCurrentTurn(g, p);
  }

  private int roadActionPointCost(PlayerState p) {
    if ((p.selectedAction == ActionType.BUILD || p.hero.heroClass() == HeroClass.ENGINEER)
        && !p.freeRoadUsed) {
      p.freeRoadUsed = true;
      return 0;
    }
    return 1;
  }

  private int recruitActionPointCost(PlayerState p, UnitType unitType) {
    if (unitType == UnitType.MILITIA && p.selectedAction == ActionType.RECRUIT && !p.freeMilitiaUsed) {
      p.freeMilitiaUsed = true;
      return 0;
    }
    if (unitType == UnitType.MILITIA) return 1;
    return p.selectedAction == ActionType.RECRUIT ? 1 : 2;
  }

  private int exploreActionPointCost(PlayerState p) {
    if (p.selectedAction == ActionType.EXPLORE && !p.freeExploreUsed) {
      p.freeExploreUsed = true;
      return 0;
    }
    return 1;
  }

  private int deepExploreActionPointCost(PlayerState p) {
    return p.selectedAction == ActionType.EXPLORE ? 1 : 2;
  }

  private int attackActionPointCost(PlayerState p) {
    return fullAttackActionPointCost(p);
  }

  private int fullAttackActionPointCost(PlayerState p) {
    if (p.selectedAction == ActionType.ATTACK && !p.attackDiscountUsed) {
      return 1;
    }
    return 2;
  }

  private void spendFullAttackActionPoints(PlayerState p) {
    int cost = fullAttackActionPointCost(p);
    spendActionPoints(p, cost);
    if (p.selectedAction == ActionType.ATTACK && !p.attackDiscountUsed) {
      p.attackDiscountUsed = true;
    }
  }

  private void explore(GameState g, PlayerState p, GameCommand.Explore c, List<DomainEvent> e) {
    exploreHex(g, p, c.target(), false, e);
  }

  private void deepExplore(GameState g, PlayerState p, GameCommand.DeepExplore c, List<DomainEvent> e) {
    exploreHex(g, p, c.target(), true, e);
  }

  private void exploreHex(
      GameState g, PlayerState p, HexCoordinate targetCoordinate, boolean deep, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    if (turn) {
      requireCurrentTurn(g, p);
      spendActionPoints(p, deep ? deepExploreActionPointCost(p) : exploreActionPointCost(p));
    } else resolutionAction(g, p, ActionType.EXPLORE);
    if (p.hero.location() == null) {
      throw DomainException.of("INVALID_TARGET", "Hero is not on the map");
    }
    int range = p.hero.heroClass() == HeroClass.RANGER ? 2 : 1;
    MapHex target =
        g.map.stream()
            .filter(h -> h.coordinate().equals(targetCoordinate))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_TARGET", "Unknown hex"));
    if (p.hero.location().distanceTo(targetCoordinate) > range)
      throw DomainException.of("INVALID_TARGET", "Select a hex within Hero exploration range");
    if (!p.exploredHexes.add(targetCoordinate))
      throw DomainException.of("INVALID_TARGET", "This hex was already explored by the Hero");
    ExplorationResultType resultType = deep ? deepExplorationType(g, p, target) : explorationType(target);
    String publicReward = "";
    if (resultType == ExplorationResultType.BONUS_RESOURCE) {
      ResourceType reward = target.resource() == null ? ResourceType.FOOD : target.resource();
      int amount = deep || p.hero.heroClass() == HeroClass.RANGER ? 2 : 1;
      p.resources = p.resources.add(reward, amount);
      publicReward = "+" + amount + " " + reward.name();
    } else if (resultType == ExplorationResultType.TRADE_CONTACT) {
      p.resources = p.resources.add(ResourceType.GOLD, 1);
      publicReward = "+1 GOLD";
    } else if (resultType == ExplorationResultType.ARTIFACT_CLUE) {
      p.resources = p.resources.add(ResourceType.GOLD, 1);
      p.reputation = Math.min(12, p.reputation + 1);
      publicReward = "+1 GOLD, +1 Reputation";
    } else if (resultType == ExplorationResultType.HIDDEN_ROUTE) {
      p.resources = p.resources.add(ResourceType.WOOD, 1).add(ResourceType.STONE, 1);
      publicReward = "+1 WOOD, +1 STONE";
    } else if (resultType == ExplorationResultType.LOCAL_QUEST) {
      p.reputation = Math.min(12, p.reputation + 1);
      publicReward = "+1 Reputation";
    } else if (resultType == ExplorationResultType.MONSTER_CLUE) {
      p.resources = p.resources.add(ResourceType.GOLD, 1);
      publicReward = "+1 GOLD";
    } else if (resultType == ExplorationResultType.AMBUSH) {
      p.reputation = Math.min(12, p.reputation + 1);
      publicReward = "+1 Reputation";
    } else if (resultType == ExplorationResultType.VILLAGE_SECRET) {
      p.resources = p.resources.add(ResourceType.GOLD, 1);
      p.reputation = Math.min(12, p.reputation + 1);
      publicReward = "+1 GOLD, +1 Reputation";
    } else if (resultType == ExplorationResultType.TEMPORARY_BUFF) {
      p.fortificationTokens += 1;
      publicReward = "+1 Fortification";
    }
    if (target.terrain() == TerrainType.RUIN) p.exploredRuins.add(targetCoordinate);
    SealProgress old = p.sealProgress;
    p.sealProgress =
        new SealProgress(
            old.heroicQuest(),
            old.distinctMonsterTypes(),
            old.tradesWithDifferentPlayers(),
            old.tradeRoutes(),
            old.peacefulAlliances(),
            p.exploredRuins.size(),
            old.artifacts(),
            old.permanent());
    ExplorationResult result =
        new ExplorationResult(
            p.id,
            targetCoordinate,
            resultType,
            (deep ? "Deep Explore: " : "") + explorationDescription(resultType, target, publicReward));
    g.explorationResults.add(result);
    e.add(
        new DomainEvent(
            deep ? "DEEP_EXPLORE_RESOLVED" : "HEX_EXPLORED",
            Map.of(
                "playerId",
                p.id,
                "target",
                targetCoordinate,
                "result",
                resultType,
                "publicReward",
                publicReward)));
    if (turn) e.add(event(deep ? "DEEP_EXPLORE_BY_ACTION_POINT" : "HEX_EXPLORED_BY_ACTION_POINT", "playerId", p.id));
    else finishAction(g, p, e, "EXPLORE_RESOLVED");
  }

  private ExplorationResultType explorationType(MapHex target) {
    return switch (target.terrain()) {
      case FOREST, FIELD, MOUNTAIN, QUARRY -> ExplorationResultType.BONUS_RESOURCE;
      case TRADE_LAND -> ExplorationResultType.TRADE_CONTACT;
      case VILLAGE -> ExplorationResultType.LOCAL_QUEST;
      case MONSTER_LAIR -> ExplorationResultType.MONSTER_CLUE;
      case RUIN, ANCIENT_CAPITAL -> ExplorationResultType.ARTIFACT_CLUE;
    };
  }

  private ExplorationResultType deepExplorationType(GameState g, PlayerState p, MapHex target) {
    int quality = (int) Math.floorMod(g.seed + g.rngCounter++ + target.coordinate().q() * 31L
        + target.coordinate().r() * 17L, 10);
    if (p.hero.heroClass() == HeroClass.RANGER) quality += 2;
    if (quality <= 1) return ExplorationResultType.AMBUSH;
    if (quality <= 3) return explorationType(target);
    if (quality <= 5) return ExplorationResultType.HIDDEN_ROUTE;
    if (quality <= 7) return ExplorationResultType.MONSTER_CLUE;
    return target.terrain() == TerrainType.VILLAGE
        ? ExplorationResultType.VILLAGE_SECRET
        : ExplorationResultType.ARTIFACT_CLUE;
  }

  private String explorationDescription(
      ExplorationResultType resultType, MapHex target, String publicReward) {
    String reward = publicReward == null || publicReward.isBlank() ? "" : " (" + publicReward + ")";
    return switch (resultType) {
      case BONUS_RESOURCE -> switch (target.terrain()) {
        case FOREST -> "Woodland supplies discovered" + reward;
        case FIELD -> "A concealed food cache discovered" + reward;
        case MOUNTAIN, QUARRY -> "A mineral deposit discovered" + reward;
        default -> "Useful supplies discovered" + reward;
      };
      case TRADE_CONTACT -> "A caravan contact paid for information" + reward;
      case ARTIFACT_CLUE -> "A private artifact clue was uncovered" + reward;
      case HIDDEN_ROUTE -> "A hidden route was mapped. You gained road-building supplies" + reward;
      case LOCAL_QUEST -> "A local request was completed for reputation" + reward;
      case MONSTER_CLUE -> "Monster tracks revealed a paid warning bounty" + reward;
      case AMBUSH -> "An ambush was spotted and avoided; locals respect the warning" + reward;
      case VILLAGE_SECRET -> "A village secret produced a useful lead" + reward;
      case TEMPORARY_BUFF -> "A temporary defensive advantage was prepared" + reward;
      case NOTHING_FOUND -> "Nothing useful was found.";
    };
  }

  private void fortify(GameState g, PlayerState p, List<DomainEvent> e) {
    if (g.phase == GamePhase.PLAYER_TURNS) {
      requireCurrentTurn(g, p);
      spendActionPoints(p, 2);
      p.fortificationTokens += p.hero.heroClass() == HeroClass.ENGINEER ? 3 : 2;
      e.add(event("FORTIFIED", "playerId", p.id));
      return;
    }
    resolutionAction(g, p, ActionType.FORTIFY);
    p.fortificationTokens += p.hero.heroClass() == HeroClass.ENGINEER ? 3 : 2;
    finishAction(g, p, e, "FORTIFIED");
  }

  private void move(GameState g, PlayerState p, GameCommand.MoveHero c, List<DomainEvent> e) {
    if (g.phase != GamePhase.PLAYER_TURNS)
      throw DomainException.of("INVALID_PHASE", "Hero movement is a Basic Action during your turn");
    requireCurrentTurn(g, p);
    spendActionPoints(p, 1);
    if (p.hero.location() == null)
      throw DomainException.of("INVALID_TARGET", "Hero is not on the map");
    int range = 1;
    if (p.hero.location().distanceTo(c.to()) > range
        || g.map.stream().noneMatch(h -> h.coordinate().equals(c.to())))
      throw DomainException.of("TARGET_NOT_ADJACENT", "Target exceeds hero movement range");
    p.hero =
        new HeroState(
            p.hero.heroClass(),
            p.hero.hp(),
            p.hero.mana(),
            p.hero.grace(),
            c.to(),
            p.hero.defeated());
    e.add(event("HERO_MOVED", "playerId", p.id));
  }

  private void attack(GameState g, PlayerState p, GameCommand.Attack c, List<DomainEvent> e) {
    boolean turn = g.phase == GamePhase.PLAYER_TURNS;
    if (turn) {
      requireCurrentTurn(g, p);
      if (p.selectedAction != ActionType.ATTACK)
        throw DomainException.of("ATTACK_CARD_REQUIRED", "Full Attack requires the ATTACK card");
    } else {
      resolutionAction(g, p, ActionType.ATTACK);
    }
    if (p.hero.location() == null || p.hero.location().distanceTo(c.target()) > 1)
      throw DomainException.of("TARGET_NOT_ADJACENT", "Attack target must be adjacent");
    List<UnitState> attackers =
        p.units.stream()
            .filter(u -> !u.garrison() && u.fatigue() != FatigueState.EXHAUSTED)
            .toList();
    if (attackers.isEmpty())
      throw DomainException.of("UNIT_EXHAUSTED", "At least one ready field unit is required");
    ArmyRules army = new ArmyRules();
    int roll = g.forcedD20 != null ? g.forcedD20 : die(g, 20);
    g.forcedD20 = null;
    int heroBonus = p.hero.heroClass() == HeroClass.KNIGHT ? 2 : 0;
    MonsterState monster =
        g.monsters.stream().filter(m -> m.location().equals(c.target())).findFirst().orElse(null);
    PlayerState defender = defenderAt(g, p, c.target());
    if (turn && defender != null) {
      int apCost = fullAttackActionPointCost(p);
      spendFullAttackActionPoints(p);
      declareConflict(g, p, defender, c.target(), ConflictAttackType.FULL_ATTACK, apCost, e);
      return;
    }
    if (turn) spendFullAttackActionPoints(p);
    int defenseArmy = 0, baseExtra = 0;
    if (monster != null) baseExtra = Math.max(0, monster.strength() - 10);
    else {
      if (defender == null)
        throw DomainException.of("INVALID_TARGET", "No enemy or monster at target");
      defenseArmy =
          army.calculateArmyBonus(
              army.armyPower(
                  defender.units.stream().filter(UnitState::garrison).toList(), false, true));
    }
    CombatResolver.CombatResult result =
        new CombatResolver()
            .resolve(
                new CombatResolver.CombatInput(
                    roll,
                    heroBonus,
                    army.calculateArmyBonus(army.armyPower(attackers, true, false)),
                    0,
                    0,
                    0,
                    defenseArmy,
                    baseExtra,
                    0,
                    c.reaction(),
                    0));
    p.units =
        p.units.stream()
            .map(u -> attackers.contains(u) ? new FatigueResolver().afterBattle(u, roll == 1) : u)
            .toList();
    if (monster != null && result.damage() > 0) {
      int hp = monster.hp() - result.damage();
      g.monsters.remove(monster);
      if (hp > 0)
        g.monsters.add(
            new MonsterState(
                monster.id(),
                monster.type(),
                monster.location(),
                monster.strength(),
                hp,
                monster.targetPlayerId(),
                monster.tier()));
      else {
        int reward = monster.tier().equals("MINOR") ? 2 : 3;
        p.resources = p.resources.add(ResourceType.GOLD, reward);
        p.reputation = Math.min(12, p.reputation + 1);
        e.add(event("MONSTER_DEFEATED", "monsterId", monster.id()));
      }
    }
    e.add(
        new DomainEvent(
            "COMBAT_RESOLVED",
            Map.of(
                "playerId",
                p.id,
                "roll",
                roll,
                "attackTotal",
                result.attackTotal(),
                "defenseTotal",
                result.defenseTotal(),
                "damage",
                result.damage(),
                "counterDamage",
                result.counterDamage(),
                "strongRetaliation",
                result.strongRetaliation())));
    if (turn) e.add(event("ATTACK_RESOLVED", "playerId", p.id));
    else finishAction(g, p, e, "ATTACK_RESOLVED");
  }

  private void smallRaid(GameState g, PlayerState p, GameCommand.SmallRaid c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    if (p.hero == null || p.hero.location() == null || p.hero.location().distanceTo(c.target()) > 1)
      throw DomainException.of("TARGET_NOT_ADJACENT", "Small Raid target must be adjacent to Hero");
    MonsterState monster =
        g.monsters.stream().filter(m -> m.location().equals(c.target())).findFirst().orElse(null);
    PlayerState defender = defenderAt(g, p, c.target());
    if (monster == null && defender == null)
      throw DomainException.of("INVALID_TARGET", "Small Raid needs an adjacent enemy or monster");
    spendActionPoints(p, 1);
    if (defender != null) {
      declareConflict(g, p, defender, c.target(), ConflictAttackType.SMALL_RAID, 1, e);
      return;
    }
    int roll = g.forcedD20 != null ? g.forcedD20 : die(g, 20);
    g.forcedD20 = null;
    int total = roll + (p.hero.heroClass() == HeroClass.KNIGHT ? 2 : 0);
    if (total >= 11) {
      if (monster != null) {
        int hp = monster.hp() - 1;
        g.monsters.remove(monster);
        if (hp > 0)
          g.monsters.add(new MonsterState(monster.id(), monster.type(), monster.location(),
              monster.strength(), hp, monster.targetPlayerId(), monster.tier()));
      } else if (defender.resources.gold() > 0) {
        defender.resources = defender.resources.add(ResourceType.GOLD, -1);
        p.resources = p.resources.add(ResourceType.GOLD, 1);
      }
    }
    e.add(new DomainEvent("SMALL_RAID_RESOLVED",
        Map.of("playerId", p.id, "target", c.target(), "roll", roll, "total", total,
            "success", total >= 11)));
  }

  private void declareConflict(
      GameState g,
      PlayerState attacker,
      PlayerState defender,
      HexCoordinate target,
      ConflictAttackType attackType,
      int apCost,
      List<DomainEvent> e) {
    if (g.pendingConflict != null)
      throw DomainException.of("CONFLICT_PENDING", "Resolve the current conflict first");
    g.pendingConflict =
        new PendingConflict(
            UUID.randomUUID(),
            attacker.id,
            defender.id,
            target,
            attackType,
            attacker.selectedAction,
            defender.selectedAction,
            apCost);
    g.phase = GamePhase.WAITING_FOR_DEFENDER_REACTION;
    e.add(
        new DomainEvent(
            "CONFLICT_DECLARED",
            Map.of(
                "conflictId",
                g.pendingConflict.conflictId(),
                "attackerId",
                attacker.id,
                "defenderId",
                defender.id,
                "target",
                target,
                "attackType",
                attackType)));
  }

  private void defenderReaction(
      GameState g, PlayerState p, GameCommand.DefenderReaction c, List<DomainEvent> e) {
    phase(g, GamePhase.WAITING_FOR_DEFENDER_REACTION);
    PendingConflict conflict = g.pendingConflict;
    if (conflict == null) throw DomainException.of("NO_PENDING_CONFLICT", "No conflict is pending");
    if (!conflict.defenderPlayerId().equals(p.id))
      throw DomainException.of("NOT_DEFENDER", "Only the defending player can choose a reaction");
    resolvePendingConflict(g, conflict, c.reaction() == null ? ReactionType.NONE : c.reaction(), e);
  }

  private void resolvePendingConflict(
      GameState g, PendingConflict conflict, ReactionType reaction, List<DomainEvent> e) {
    PlayerState attacker = player(g, conflict.attackerPlayerId());
    PlayerState defender = player(g, conflict.defenderPlayerId());
    int roll = g.forcedD20 != null ? g.forcedD20 : die(g, 20);
    g.forcedD20 = null;
    int defenderRoll = die(g, 20);
    int heroBonus = attacker.hero.heroClass() == HeroClass.KNIGHT ? 2 : 0;
    int attackerCardBonus = conflict.attackerActionCard() == ActionType.ATTACK ? 1 : 0;
    int defenderCardBonus = conflict.defenderActionCard() == ActionType.FORTIFY ? 1 : 0;
    int resourcesStolen = 0;
    int resourcesLost = 0;
    String stolenResource = "NONE";
    int damage = 0;
    boolean success;
    String outcome;
    ArmyRules army = new ArmyRules();
    List<UnitState> attackers =
        attacker.units.stream()
            .filter(u -> !u.garrison() && u.fatigue() != FatigueState.EXHAUSTED)
            .toList();
    if (conflict.attackType() == ConflictAttackType.SMALL_RAID) {
      int reactionDefense =
          reaction == ReactionType.SHIELD ? 4 : reaction == ReactionType.COUNTERATTACK ? 1 : 0;
      int attackTotal = roll + heroBonus + attackerCardBonus;
      int defenseTotal = defenderRoll + reactionDefense + defenderCardBonus;
      success = attackTotal >= defenseTotal;
      if (success) {
        if (reaction == ReactionType.EVACUATION) {
          if (defender.resources.gold() > 0) {
            defender.resources = defender.resources.add(ResourceType.GOLD, -1);
            resourcesLost = 1;
          }
          outcome = "Evacuation avoided the raid theft, but 1 Gold was lost.";
        } else if (defender.resources.gold() > 0) {
          defender.resources = defender.resources.add(ResourceType.GOLD, -1);
          attacker.resources = attacker.resources.add(ResourceType.GOLD, 1);
          resourcesStolen = 1;
          stolenResource = ResourceType.GOLD.name();
          outcome = "Raid succeeded and stole 1 Gold.";
        } else {
          outcome = "Raid succeeded, but no Gold was available to steal.";
        }
      } else {
        outcome = "Raid failed.";
        if (reaction == ReactionType.COUNTERATTACK && defenseTotal - attackTotal >= 3) {
          woundFirstAttacker(attacker, attackers);
          outcome += " Counterattack wounded an attacking unit.";
        }
      }
      g.lastCombatReport =
          List.of(
              new CombatReportEntry(
                  attacker.id,
                  defender.id,
                  null,
                  attacker.hero.location(),
                  conflict.target(),
                  "SMALL_RAID",
                  roll,
                  defenderRoll,
                  attackTotal,
                  defenseTotal,
                  0,
                  0,
                  0,
                  0));
      e.add(conflictResolvedEvent(conflict, reaction, roll, defenderRoll, attackTotal, defenseTotal, 0,
          resourcesStolen, resourcesLost, stolenResource, outcome));
    } else {
      int armyBonus = army.calculateArmyBonus(army.armyPower(attackers, true, false));
      int defenseArmy =
          army.calculateArmyBonus(
              army.armyPower(defender.units.stream().filter(UnitState::garrison).toList(), false, true));
      CombatResolver.CombatResult result =
          new CombatResolver()
              .resolve(
                  new CombatResolver.CombatInput(
                      roll,
                      heroBonus,
                      armyBonus,
                      attackerCardBonus,
                      0,
                      0,
                      defenseArmy,
                      defenderCardBonus,
                      defenderRoll - 10,
                      reaction,
                      defender.fortificationTokens));
      damage = reaction == ReactionType.EVACUATION ? Math.max(0, result.damage() - 1) : result.damage();
      applySettlementDamage(defender, conflict.target(), damage);
      if (reaction == ReactionType.EVACUATION && defender.resources.gold() > 0) {
        defender.resources = defender.resources.add(ResourceType.GOLD, -1);
        resourcesLost = 1;
      }
      if (damage > 0 && reaction != ReactionType.EVACUATION) {
        ResourceType spoils = stealOneResource(defender, attacker);
        if (spoils != null) {
          resourcesStolen = 1;
          stolenResource = spoils.name();
        }
      }
      if (result.counterDamage()) woundFirstAttacker(attacker, attackers);
      attacker.units =
          attacker.units.stream()
              .map(u -> attackers.contains(u) ? new FatigueResolver().afterBattle(u, roll == 1) : u)
              .toList();
      success = result.success();
      outcome =
          success
              ? "Full Attack dealt " + damage + " damage."
              : "Full Attack failed to deal damage.";
      if (resourcesStolen > 0) outcome += " Spoils: stole 1 " + title(stolenResource) + ".";
      if (result.counterDamage()) outcome += " Counterattack wounded an attacking unit.";
      g.lastCombatReport =
          List.of(
              new CombatReportEntry(
                  attacker.id,
                  defender.id,
                  null,
                  attacker.hero.location(),
                  conflict.target(),
                  "FULL_ATTACK",
                  roll,
                  defenderRoll,
                  result.attackTotal(),
                  result.defenseTotal(),
                  damage,
                  result.counterDamage() ? 1 : 0,
                  damage,
                  0));
      e.add(conflictResolvedEvent(conflict, reaction, roll, defenderRoll, result.attackTotal(), result.defenseTotal(),
          damage, resourcesStolen, resourcesLost, stolenResource, outcome));
    }
    g.pendingConflict = null;
    g.phase = GamePhase.PLAYER_TURNS;
    g.currentTurnIndex = g.players.indexOf(attacker);
  }

  private DomainEvent conflictResolvedEvent(
      PendingConflict conflict,
      ReactionType reaction,
      int roll,
      int defenderRoll,
      int attackTotal,
      int defenseTotal,
      int damage,
      int resourcesStolen,
      int resourcesLost,
      String stolenResource,
      String outcome) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("attackerId", conflict.attackerPlayerId());
    payload.put("defenderId", conflict.defenderPlayerId());
    payload.put("target", conflict.target());
    payload.put("attackType", conflict.attackType());
    payload.put("reaction", reaction);
    payload.put("roll", roll);
    payload.put("defenderRoll", defenderRoll);
    payload.put("attackTotal", attackTotal);
    payload.put("defenseTotal", defenseTotal);
    payload.put("damage", damage);
    payload.put("resourcesStolen", resourcesStolen);
    payload.put("resourcesLost", resourcesLost);
    payload.put("stolenResource", stolenResource);
    payload.put("attackerCard", conflict.attackerActionCard() == null ? "NONE" : conflict.attackerActionCard());
    payload.put("defenderCard", conflict.defenderActionCard() == null ? "NONE" : conflict.defenderActionCard());
    payload.put("outcome", outcome);
    return new DomainEvent("CONFLICT_RESOLVED", payload);
  }

  private PlayerState defenderAt(GameState g, PlayerState attacker, HexCoordinate target) {
    return g.players.stream()
        .filter(player -> !player.id.equals(attacker.id))
        .filter(player -> player.settlements.stream().anyMatch(s -> s.location().equals(target)))
        .findFirst()
        .orElse(null);
  }

  private void applySettlementDamage(PlayerState defender, HexCoordinate target, int damage) {
    if (damage <= 0) return;
    defender.settlements =
        defender.settlements.stream()
            .map(
                settlement -> {
                  if (!settlement.location().equals(target)) return settlement;
                  int remaining = Math.max(0, settlement.durability() - damage);
                  return new SettlementState(
                      settlement.id(), settlement.location(), settlement.level(), remaining);
                })
            .filter(settlement -> settlement.durability() > 0)
            .toList();
  }

  private ResourceType stealOneResource(PlayerState defender, PlayerState attacker) {
    for (ResourceType type :
        List.of(ResourceType.GOLD, ResourceType.ORE, ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE)) {
      if (resourceAmount(defender.resources, type) > 0) {
        defender.resources = defender.resources.add(type, -1);
        attacker.resources = attacker.resources.add(type, 1);
        return type;
      }
    }
    return null;
  }

  private void woundFirstAttacker(PlayerState attacker, List<UnitState> attackers) {
    attackers.stream()
        .findFirst()
        .ifPresent(
            wounded ->
                attacker.units =
                    attacker.units.stream()
                        .map(
                            unit ->
                                unit.id().equals(wounded.id())
                                    ? new UnitState(
                                        unit.id(),
                                        unit.type(),
                                        unit.fatigue(),
                                        true,
                                        unit.garrison(),
                                        unit.contractUntilRound())
                                    : unit)
                        .toList());
  }

  private void priestHeal(GameState g, PlayerState p, GameCommand.PriestHeal c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.PRIEST);
    spendActionPoints(p, 1);
    spendGrace(p, 1);
    boolean healed = false;
    if (p.hero.location() != null && p.hero.location().distanceTo(c.target()) <= 1 && p.hero.hp() < 3) {
      p.hero = new HeroState(p.hero.heroClass(), Math.min(3, p.hero.hp() + 1), p.hero.mana(),
          p.hero.grace(), p.hero.location(), p.hero.defeated());
      healed = true;
    } else {
      for (int i = 0; i < p.units.size(); i++) {
        UnitState unit = p.units.get(i);
        if (unit.wounded()) {
          p.units.set(i, new UnitState(unit.id(), unit.type(), unit.fatigue(), false,
              unit.garrison(), unit.contractUntilRound()));
          healed = true;
          break;
        }
      }
    }
    if (!healed) throw DomainException.of("INVALID_TARGET", "No damaged friendly target nearby");
    e.add(event("PRIEST_HEAL_USED", "playerId", p.id));
  }

  private void priestBless(GameState g, PlayerState p, GameCommand.PriestBless c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.PRIEST);
    spendActionPoints(p, 1);
    spendGrace(p, 1);
    p.blessTokens++;
    e.add(event("PRIEST_BLESS_USED", "playerId", p.id));
  }

  private void priestSanctuary(
      GameState g, PlayerState p, GameCommand.PriestSanctuary c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.PRIEST);
    spendActionPoints(p, 2);
    spendGrace(p, 2);
    p.sanctuaryTokens++;
    e.add(event("PRIEST_SANCTUARY_USED", "playerId", p.id));
  }

  private void arcaneBolt(GameState g, PlayerState p, GameCommand.ArcaneBolt c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.MAGE);
    spendActionPoints(p, 1);
    spendMana(p, 1);
    if (p.hero.location() == null || p.hero.location().distanceTo(c.target()) > 1)
      throw DomainException.of("INVALID_TARGET", "Arcane Bolt target must be adjacent");
    int roll = g.forcedD20 != null ? g.forcedD20 : die(g, 20);
    g.forcedD20 = null;
    int total = roll + 2;
    MonsterState monster =
        g.monsters.stream().filter(m -> m.location().equals(c.target())).findFirst().orElse(null);
    if (monster == null)
      throw DomainException.of("INVALID_TARGET", "Arcane Bolt needs a monster target for now");
    boolean success = total >= 10;
    if (total >= 10) {
      int hp = monster.hp() - 1;
      g.monsters.remove(monster);
      if (hp > 0)
        g.monsters.add(new MonsterState(monster.id(), monster.type(), monster.location(),
            monster.strength(), hp, monster.targetPlayerId(), monster.tier()));
    }
    g.lastCombatReport =
        List.of(
            new CombatReportEntry(
                p.id,
                null,
                monster.id(),
                p.hero.location(),
                c.target(),
                "ARCANE_BOLT",
                roll,
                null,
                total,
                10,
                success ? 1 : 0,
                0,
                0,
                success ? 1 : 0));
    e.add(new DomainEvent("ARCANE_BOLT_RESOLVED",
        Map.of("playerId", p.id, "target", c.target(), "roll", roll, "total", total,
            "success", success)));
  }

  private void mageWard(GameState g, PlayerState p, GameCommand.MageWard c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.MAGE);
    spendActionPoints(p, 1);
    spendMana(p, 1);
    p.wardTokens++;
    e.add(event("MAGE_WARD_USED", "playerId", p.id));
  }

  private void mageReveal(GameState g, PlayerState p, GameCommand.MageReveal c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.MAGE);
    spendActionPoints(p, 1);
    spendMana(p, 1);
    MapHex target = hex(g, c.target());
    if (target == null || p.hero.location() == null || p.hero.location().distanceTo(c.target()) > 1)
      throw DomainException.of("INVALID_TARGET", "Reveal target must be adjacent");
    ExplorationResultType preview = explorationType(target);
    g.explorationResults.add(new ExplorationResult(p.id, c.target(), preview,
        "Mage Reveal preview: " + preview.name()));
    e.add(new DomainEvent("MAGE_REVEAL_USED",
        Map.of("playerId", p.id, "target", c.target(), "preview", preview)));
  }

  private void transmute(GameState g, PlayerState p, GameCommand.Transmute c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.MAGE);
    if (c.give() == c.receive())
      throw DomainException.of("INVALID_PAYLOAD", "Choose two different resources");
    spendActionPoints(p, 1);
    spendMana(p, 1);
    spendResources(p, Resources.none().add(c.give(), 1));
    p.resources = p.resources.add(c.receive(), 1);
    e.add(
        new DomainEvent(
            "MAGE_TRANSMUTE_USED",
            Map.of("playerId", p.id, "give", c.give(), "receive", c.receive())));
  }

  private void scout(GameState g, PlayerState p, GameCommand.Scout c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.RANGER);
    int cost = p.selectedAction == ActionType.EXPLORE ? 0 : 1;
    spendActionPoints(p, cost);
    MapHex target = hex(g, c.target());
    if (target == null || p.hero.location() == null || p.hero.location().distanceTo(c.target()) > 1)
      throw DomainException.of("INVALID_TARGET", "Scout target must be adjacent");
    ExplorationResultType preview = explorationType(target);
    e.add(new DomainEvent("RANGER_SCOUT_USED",
        Map.of("playerId", p.id, "target", c.target(), "preview", preview)));
  }

  private void swiftMove(GameState g, PlayerState p, GameCommand.SwiftMove c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.RANGER);
    if (p.swiftMoveUsed) throw DomainException.of("ACTION_ALREADY_USED", "Swift Move already used");
    p.swiftMoveUsed = true;
    if (p.hero.location() == null || p.hero.location().distanceTo(c.to()) > 1 || hex(g, c.to()) == null)
      throw DomainException.of("INVALID_TARGET", "Swift Move target must be adjacent");
    p.hero = new HeroState(p.hero.heroClass(), p.hero.hp(), p.hero.mana(), p.hero.grace(),
        c.to(), p.hero.defeated());
    e.add(event("RANGER_SWIFT_MOVE_USED", "playerId", p.id));
  }

  private void quickRoad(GameState g, PlayerState p, GameCommand.QuickRoad c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.ENGINEER);
    if (p.quickRoadUsed) throw DomainException.of("ACTION_ALREADY_USED", "Quick Road already used");
    p.quickRoadUsed = true;
    buildRoad(g, p, new GameCommand.BuildRoad(c.from(), c.to()), e);
  }

  private void repair(GameState g, PlayerState p, GameCommand.Repair c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.ENGINEER);
    if (p.repairUsed) throw DomainException.of("ACTION_ALREADY_USED", "Repair already used");
    p.repairUsed = true;
    p.units = p.units.stream()
        .map(u -> u.wounded() ? new UnitState(u.id(), u.type(), u.fatigue(), false,
            u.garrison(), u.contractUntilRound()) : u)
        .toList();
    e.add(event("ENGINEER_REPAIR_USED", "playerId", p.id));
  }

  private void marketDeal(GameState g, PlayerState p, GameCommand.MarketDeal c, List<DomainEvent> e) {
    requireTurnAction(g, p);
    requireHero(p, HeroClass.MERCHANT);
    int rate = p.selectedAction == ActionType.TRADE && !p.freeTradeCardBuyUsed ? 2 : 3;
    p.freeTradeCardBuyUsed = true;
    if (resourceAmount(p.resources, c.give()) < rate)
      throw DomainException.of("INSUFFICIENT_RESOURCES", "Not enough resources for Market Deal");
    p.resources = p.resources.add(c.give(), -rate).add(c.receive(), 1);
    spendActionPoints(p, 1);
    e.add(event("MERCHANT_MARKET_DEAL_USED", "playerId", p.id));
  }

  private void lockAttackPlan(
      GameState g, PlayerState p, GameCommand.LockAttackPlan c, List<DomainEvent> e) {
    phase(g, GamePhase.RESOLUTION);
    if (!p.actionLocked || p.selectedAction != ActionType.ATTACK) {
      throw DomainException.of("INVALID_PHASE", "Player did not select ATTACK");
    }
    boolean earlierActionPending =
        g.players.stream()
            .anyMatch(
                player ->
                    player.actionLocked
                        && player.selectedAction != null
                        && player.selectedAction.ordinal() < ActionType.ATTACK.ordinal());
    if (earlierActionPending) {
      throw DomainException.of("NOT_PLAYER_TURN", "Earlier action types must resolve first");
    }
    if (p.attackPlan != null) {
      throw DomainException.of("ATTACK_PLAN_LOCKED", "Attack Plan is already locked");
    }
    if (c.source().distanceTo(c.target()) != 1) {
      throw DomainException.of("TARGET_NOT_ADJACENT", "Attack target must border the source");
    }
    boolean controlsSource =
        p.settlements.stream().anyMatch(settlement -> settlement.location().equals(c.source()))
            || (p.hero != null && c.source().equals(p.hero.location()));
    if (!controlsSource) {
      throw DomainException.of("INVALID_TARGET", "Attack source is not controlled by the player");
    }
    Set<UUID> ownedUnitIds =
        p.units.stream()
            .filter(unit -> unit.fatigue() != FatigueState.EXHAUSTED)
            .map(UnitState::id)
            .collect(java.util.stream.Collectors.toSet());
    if (!ownedUnitIds.containsAll(c.participatingUnitIds())) {
      throw DomainException.of("UNIT_EXHAUSTED", "Plan contains unavailable units");
    }
    if (c.participatingUnitIds().isEmpty() && !c.heroParticipates()) {
      throw DomainException.of("INVALID_PAYLOAD", "Attack Plan must commit a Hero or units");
    }
    if (c.heroParticipates()
        && (p.hero == null || p.hero.defeated() || !c.source().equals(p.hero.location()))) {
      throw DomainException.of("INVALID_TARGET", "Participating Hero must be at the source");
    }
    p.attackPlan =
        new AttackPlan(
            p.id,
            c.source(),
            c.target(),
            c.participatingUnitIds(),
            c.heroParticipates(),
            c.selectedTacticCardId());
    p.previousAction = ActionType.ATTACK;
    e.add(event("ATTACK_PLAN_LOCKED", "playerId", p.id));
    List<PlayerState> attackers =
        g.players.stream()
            .filter(player -> player.actionLocked && player.selectedAction == ActionType.ATTACK)
            .toList();
    if (attackers.stream().allMatch(player -> player.attackPlan != null)) {
      e.add(
          new DomainEvent(
              "ATTACK_PLANS_REVEALED",
              Map.of(
                  "plans",
                  attackers.stream()
                      .map(
                          player ->
                              Map.of(
                                  "playerId",
                                  player.id,
                                  "source",
                                  player.attackPlan.source(),
                                  "target",
                                  player.attackPlan.target(),
                                  "unitCount",
                                  player.attackPlan.participatingUnitIds().size(),
                                  "heroParticipates",
                                  player.attackPlan.heroParticipates()))
                      .toList())));
    }
  }

  private void resolveAttackBatch(GameState g, PlayerState p, List<DomainEvent> e) {
    phase(g, GamePhase.RESOLUTION);
    List<PlayerState> attackers =
        g.players.stream()
            .filter(player -> player.actionLocked && player.selectedAction == ActionType.ATTACK)
            .toList();
    if (attackers.isEmpty() || attackers.stream().anyMatch(player -> player.attackPlan == null)) {
      throw DomainException.of("ATTACK_PLANS_INCOMPLETE", "Every attacker must lock a plan first");
    }
    if (attackers.stream().noneMatch(player -> player.id.equals(p.id))) {
      throw DomainException.of("NOT_PLAYER_TURN", "Only an attacking player may resolve the batch");
    }
    SimultaneousCombatResolver resolver = new SimultaneousCombatResolver();
    CombatResolutionBatch batch = resolver.calculate(g, () -> die(g, 20));
    g.lastCombatReport = combatReport(g, batch);
    resolver.apply(g, batch);
    List<Map<String, Object>> conflictReports =
        batch.conflicts().stream()
            .map(
                conflict ->
                    Map.<String, Object>of(
                        "type",
                        conflict.type(),
                        "players",
                        conflict.plans().stream().map(AttackPlan::playerId).toList(),
                        "destination",
                        conflict.destination()))
            .toList();
    for (PlayerState attacker : attackers) {
      attacker.previousAction = ActionType.ATTACK;
      attacker.selectedAction = null;
      attacker.actionLocked = false;
      attacker.attackPlan = null;
    }
    e.add(
        new DomainEvent(
            "SIMULTANEOUS_COMBAT_RESOLVED",
            Map.of("conflicts", conflictReports, "rolls", batch.reports())));
    if (g.hybridTurnMode) {
      p.mainActionCompletedThisRound = true;
      advancePlayerTurn(g, e);
      return;
    }
    if (g.players.stream().noneMatch(player -> player.actionLocked)) {
      g.phase = GamePhase.END_ROUND;
      e.add(event("RESOLUTION_COMPLETE", "round", g.roundNumber));
    }
  }

  private List<CombatReportEntry> combatReport(GameState g, CombatResolutionBatch batch) {
    Map<UUID, AttackPlan> plans = new HashMap<>();
    for (PlayerState player : g.players) {
      if (player.attackPlan != null) {
        plans.put(player.id, player.attackPlan);
      }
    }
    List<CombatReportEntry> report = new ArrayList<>();
    for (CombatResolutionBatch.CombatRollReport roll : batch.reports()) {
      AttackPlan plan = plans.get(roll.playerId());
      if (plan == null) continue;
      CombatResolutionBatch.PendingDamage playerDamage =
          batch.pendingDamage().stream()
              .filter(pending -> roll.damage() > 0)
              .filter(pending -> !pending.playerId().equals(roll.playerId()))
              .filter(pending -> pending.amount() == roll.damage())
              .findFirst()
              .orElse(null);
      PlayerState defender =
          playerDamage == null ? ownerAt(g, plan.target()) : player(g, playerDamage.playerId());
      MonsterState monster = monsterAt(g, plan.target());
      int unitDamage =
          playerDamage == null
              ? 0
              : Math.min(playerDamage.amount(), playerDamage.unitIds().size());
      int settlementDamage =
          playerDamage == null
              ? 0
              : Math.max(0, playerDamage.amount() - playerDamage.unitIds().size());
      int monsterDamage =
          monster == null
              ? 0
              : batch.pendingMonsterDamage().stream()
                  .filter(pending -> pending.monsterId().equals(monster.id()))
                  .mapToInt(CombatResolutionBatch.PendingMonsterDamage::amount)
                  .sum();
      report.add(
          new CombatReportEntry(
              roll.playerId(),
              defender == null ? null : defender.id,
              monster == null ? null : monster.id(),
              plan.source(),
              plan.target(),
              roll.type().name(),
              roll.roll(),
              null,
              roll.total(),
              roll.opposingTotal(),
              roll.damage(),
              unitDamage,
              settlementDamage,
              monsterDamage));
    }
    return List.copyOf(report);
  }

  private PlayerState ownerAt(GameState g, HexCoordinate target) {
    return g.players.stream()
        .filter(player -> player.settlements.stream().anyMatch(s -> s.location().equals(target)))
        .findFirst()
        .orElse(null);
  }

  private PlayerState player(GameState g, UUID id) {
    return g.players.stream().filter(player -> player.id.equals(id)).findFirst().orElseThrow();
  }

  private MonsterState monsterAt(GameState g, HexCoordinate target) {
    return g.monsters.stream()
        .filter(monster -> monster.location().equals(target))
        .findFirst()
        .orElse(null);
  }

  private void resolveOrAdvance(GameState g, PlayerState p, List<DomainEvent> e) {
    if (g.phase == GamePhase.MONSTER_EVENT) {
      g.phase = GamePhase.NEGOTIATION;
      e.add(event("MONSTER_EVENT_CLOSED", "round", g.roundNumber));
      return;
    }
    if (g.phase == GamePhase.PRODUCTION) {
      g.phase = GamePhase.NEGOTIATION;
      e.add(event("PRODUCTION_CLOSED", "round", g.roundNumber));
      return;
    }
    if (g.phase == GamePhase.NEGOTIATION || g.phase == GamePhase.TRADE_NEGOTIATION) {
      g.tradeProposals.replaceAll(
          trade -> trade.status() == TradeStatus.PENDING
              ? trade.withStatus(TradeStatus.EXPIRED) : trade);
      for (PlayerState player : g.players) {
        player.selectedAction = null;
        player.actionLocked = false;
      }
      g.phase = GamePhase.ACTION_CARD_SELECTION;
      e.add(event("NEGOTIATION_CLOSED", "round", g.roundNumber));
      return;
    }
    if (g.phase == GamePhase.PLAYER_TURNS) {
      requireCurrentTurn(g, p);
      p.mainActionCompletedThisRound = true;
      p.basicActionPoints = 0;
      p.previousAction = p.selectedAction;
      p.selectedAction = null;
      p.actionLocked = false;
      e.add(event("MAIN_ACTION_SKIPPED", "playerId", p.id));
      advancePlayerTurn(g, e);
      return;
    }
    if (g.phase == GamePhase.ACTION_CARD_REVEAL || g.phase == GamePhase.REVEAL) {
      g.hybridTurnMode = true;
      g.actionTurnOrder = actionTurnOrder(g);
      g.actionTurnOrderIndex = 0;
      for (PlayerState player : g.players) {
        player.basicActionPoints = 0;
        player.mainActionCompletedThisRound = false;
        resetTurnDiscounts(player);
      }
      PlayerState first = player(g, g.actionTurnOrder.getFirst());
      startPlayerTurn(g, first);
      g.currentTurnIndex = g.players.indexOf(first);
      g.phase = GamePhase.PLAYER_TURNS;
      e.add(
          new DomainEvent(
              "PLAYER_TURNS_STARTED",
              Map.of(
                  "playerId", first.id,
                  "turnOrder", g.actionTurnOrder)));
      return;
    }
    phase(g, GamePhase.RESOLUTION);
    if (p.selectedAction == ActionType.ATTACK) {
      throw DomainException.of(
          "ATTACK_PLAN_REQUIRED", "ATTACK cannot be passed after selection; lock a plan");
    }
    resolutionAction(g, p, p.selectedAction);
    finishAction(g, p, e, "ACTION_PASSED");
  }

  private void finishAction(GameState g, PlayerState p, List<DomainEvent> e, String type) {
    p.previousAction = p.selectedAction;
    p.selectedAction = null;
    p.actionLocked = false;
    e.add(event(type, "playerId", p.id));
    if (g.hybridTurnMode) {
      p.mainActionCompletedThisRound = true;
      advancePlayerTurn(g, e);
      return;
    }
    if (g.players.stream().noneMatch(x -> x.actionLocked)) {
      g.phase = GamePhase.END_ROUND;
      e.add(event("RESOLUTION_COMPLETE", "round", g.roundNumber));
    }
  }

  private void requireCurrentTurn(GameState g, PlayerState p) {
    if (g.phase != GamePhase.PLAYER_TURNS || !currentTurnPlayer(g).id.equals(p.id))
      throw DomainException.of("NOT_PLAYER_TURN", "Another player is taking their turn");
  }

  private PlayerState currentTurnPlayer(GameState g) {
    if (!g.actionTurnOrder.isEmpty() && g.actionTurnOrderIndex < g.actionTurnOrder.size()) {
      return player(g, g.actionTurnOrder.get(g.actionTurnOrderIndex));
    }
    return g.players.get(g.currentTurnIndex);
  }

  private void startPlayerTurn(GameState g, PlayerState p) {
    p.basicActionPoints = baseActionPoints(g);
    resetTurnDiscounts(p);
    if (p.selectedAction == ActionType.FORTIFY) {
      p.fortificationTokens += p.hero.heroClass() == HeroClass.ENGINEER ? 3 : 2;
    }
  }

  private int baseActionPoints(GameState g) {
    return g.gameMode == GameMode.BEGINNER ? 2 : 3;
  }

  private void resetTurnDiscounts(PlayerState p) {
    p.freeExploreUsed = false;
    p.freeTradeCardBuyUsed = false;
    p.freeRoadUsed = false;
    p.freeMilitiaUsed = false;
    p.attackDiscountUsed = false;
    p.swiftMoveUsed = false;
    p.quickRoadUsed = false;
    p.repairUsed = false;
  }

  private List<UUID> actionTurnOrder(GameState g) {
    return g.players.stream()
        .filter(player -> player.selectedAction != null)
        .sorted(
            Comparator.comparingInt((PlayerState player) -> player.selectedAction.ordinal())
                .thenComparingInt(
                    player -> Math.floorMod(g.players.indexOf(player) - g.firstPlayerIndex, g.players.size())))
        .map(player -> player.id)
        .toList();
  }

  private void advancePlayerTurn(GameState g, List<DomainEvent> e) {
    if (g.players.stream().allMatch(player -> player.mainActionCompletedThisRound)) {
      g.phase = GamePhase.END_ROUND;
      e.add(event("PLAYER_TURNS_COMPLETE", "round", g.roundNumber));
      return;
    }
    if (!g.actionTurnOrder.isEmpty()) {
      do {
        g.actionTurnOrderIndex++;
      } while (g.actionTurnOrderIndex < g.actionTurnOrder.size()
          && player(g, g.actionTurnOrder.get(g.actionTurnOrderIndex)).mainActionCompletedThisRound);
      if (g.actionTurnOrderIndex >= g.actionTurnOrder.size()) {
        g.phase = GamePhase.END_ROUND;
        e.add(event("PLAYER_TURNS_COMPLETE", "round", g.roundNumber));
        return;
      }
      PlayerState next = player(g, g.actionTurnOrder.get(g.actionTurnOrderIndex));
      startPlayerTurn(g, next);
      g.currentTurnIndex = g.players.indexOf(next);
      g.phase = GamePhase.PLAYER_TURNS;
      e.add(event("PLAYER_TURN_STARTED", "playerId", next.id));
      return;
    }
    do {
      g.currentTurnIndex = (g.currentTurnIndex + 1) % g.players.size();
    } while (g.players.get(g.currentTurnIndex).mainActionCompletedThisRound);
    startPlayerTurn(g, g.players.get(g.currentTurnIndex));
    g.phase = GamePhase.PLAYER_TURNS;
    e.add(event("PLAYER_TURN_STARTED", "playerId", g.players.get(g.currentTurnIndex).id));
  }

  private void endRound(GameState g, List<DomainEvent> e) {
    if (g.phase != GamePhase.END_ROUND && g.phase != GamePhase.FINAL_ROUND)
      throw DomainException.of("INVALID_PHASE", "Round cannot end now");
    if (g.phase == GamePhase.FINAL_ROUND) {
      List<PlayerState> w = victory.qualified(g);
      if (!w.isEmpty()) {
        g.winners = w.stream().map(p -> p.id).toList();
        g.phase = GamePhase.GAME_OVER;
        g.status = GameStatus.FINISHED;
        e.add(event("GAME_OVER", "winners", g.winners));
        return;
      }
    }
    boolean strengthenMonsters = g.roundNumber % 2 == 0;
    if (strengthenMonsters) {
      g.monsters.replaceAll(
          m ->
              new MonsterState(
                  m.id(),
                  m.type(),
                  m.location(),
                  Math.min(22, m.strength() + 1),
                  m.hp(),
                  m.targetPlayerId(),
                  m.tier()));
    }
    for (MonsterState monster : List.copyOf(g.monsters)) {
      PlayerState target = player(g, monster.targetPlayerId());
      monsterAttack(g, monster, target, e);
    }
    for (PlayerState p : g.players) {
      p.basicActionPoints = baseActionPoints(g);
      p.mainActionCompletedThisRound = false;
      p.selectedAction = null;
      p.actionLocked = false;
      resetTurnDiscounts(p);
      p.hero =
          new HeroState(
              p.hero.heroClass(),
              p.hero.hp(),
              Math.min(
                  p.hero.heroClass() == HeroClass.MAGE ? 3 : p.hero.mana(),
                  p.hero.mana() + (p.hero.heroClass() == HeroClass.MAGE ? 1 : 0)),
              Math.min(
                  p.hero.heroClass() == HeroClass.PRIEST ? 4 : p.hero.grace(),
                  p.hero.grace() + (p.hero.heroClass() == HeroClass.PRIEST ? 1 : 0)),
              p.hero.location(),
              p.hero.defeated());
      p.blessTokens = 0;
      p.sanctuaryTokens = 0;
      p.wardTokens = 0;
      p.units =
          p.units.stream().map(u -> u.garrison() ? u : new FatigueResolver().recover(u)).toList();
    }
    g.actionTurnOrder.clear();
    g.actionTurnOrderIndex = 0;
    g.roundNumber++;
    g.firstPlayerIndex = (g.firstPlayerIndex + 1) % g.players.size();
    g.phase = GamePhase.WORLD_ROLL;
    e.add(event("ROUND_STARTED", "round", g.roundNumber));
  }

  private void debug(GameState g, PlayerState p, GameCommand.Debug c, List<DomainEvent> e) {
    if (!g.debugMode) throw DomainException.of("DEBUG_DISABLED", "Debug mode is disabled");
    int v = c.value() == null ? 0 : c.value();
    switch (c.operation()) {
      case "FORCE_2D6" -> g.forced2d6 = v;
      case "FORCE_D20" -> g.forcedD20 = v;
      case "ADD_RESOURCES" ->
          p.resources =
              new Resources(
                  p.resources.wood() + v,
                  p.resources.food() + v,
                  p.resources.ore() + v,
                  p.resources.stone() + v,
                  p.resources.gold() + v);
      case "REPUTATION" -> p.reputation = Math.max(-3, Math.min(12, v));
      case "MANA" ->
          p.hero =
              new HeroState(
                  p.hero.heroClass(),
                  p.hero.hp(),
                  Math.min(3, v),
                  p.hero.grace(),
                  p.hero.location(),
                  p.hero.defeated());
      case "GRACE" ->
          p.hero =
              new HeroState(
                  p.hero.heroClass(),
                  p.hero.hp(),
                  p.hero.mana(),
                  Math.min(4, v),
                  p.hero.location(),
                  p.hero.defeated());
      case "ADVANCE_PHASE" -> {
        if (g.phase == GamePhase.WORLD_ROLL) g.phase = GamePhase.NEGOTIATION;
        else if (g.phase == GamePhase.PRODUCTION) g.phase = GamePhase.NEGOTIATION;
        else if (g.phase == GamePhase.NEGOTIATION) g.phase = GamePhase.ACTION_CARD_SELECTION;
        else if (g.phase == GamePhase.ACTION_CARD_SELECTION) g.phase = GamePhase.ACTION_CARD_REVEAL;
        else if (g.phase == GamePhase.ACTION_CARD_REVEAL) g.phase = GamePhase.PLAYER_TURNS;
        else g.phase = GamePhase.END_ROUND;
      }
      default -> throw DomainException.of("INVALID_DEBUG_COMMAND", "Unsupported debug operation");
    }
    e.add(event("DEBUG_APPLIED", "operation", c.operation()));
  }

  private void resolutionAction(GameState g, PlayerState p, ActionType required) {
    phase(g, GamePhase.RESOLUTION);
    if (!p.actionLocked || p.selectedAction != required)
      throw DomainException.of("INVALID_PHASE", "The selected action is not " + required);
    PlayerState next =
        g.players.stream()
            .filter(x -> x.actionLocked)
            .min(
                Comparator.comparingInt((PlayerState x) -> x.selectedAction.ordinal())
                    .thenComparingInt(
                        x ->
                            Math.floorMod(
                                g.players.indexOf(x) - g.firstPlayerIndex, g.players.size())))
            .orElseThrow();
    if (next != p)
      throw DomainException.of("NOT_PLAYER_TURN", next.displayName + " resolves first");
  }

  private int die(GameState g, int sides) {
    long z = g.seed + (++g.rngCounter) * 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return (int) Math.floorMod(z ^ (z >>> 31), sides) + 1;
  }

  private Resources resourceCost(ResourceType type, int amount) {
    return switch (type) {
      case WOOD -> new Resources(amount, 0, 0, 0, 0);
      case FOOD -> new Resources(0, amount, 0, 0, 0);
      case ORE -> new Resources(0, 0, amount, 0, 0);
      case STONE -> new Resources(0, 0, 0, amount, 0);
      case GOLD -> new Resources(0, 0, 0, 0, amount);
    };
  }

  private void phase(GameState g, GamePhase required) {
    if (g.phase != required)
      throw DomainException.of("INVALID_PHASE", "Expected " + required + " but was " + g.phase);
  }

  private String publicLogMessage(GameState g, DomainEvent event) {
    Map<String, Object> p = event.publicPayload();
    return switch (event.type()) {
      case "PRODUCTION_RESOLVED" -> productionMessage(g, p);
      case "MONSTER_SPAWNED" -> "🎲 Roll 7: no production. A monster appears in the realm.";
      case "MONSTER_ATTACKED" ->
          p.getOrDefault("monsterType", "Monster") + " attacked " + playerName(g, p.get("playerId"))
              + " at " + coordText(p.get("target")) + ": d20 " + p.getOrDefault("roll", "?")
              + ", attack " + p.getOrDefault("attackTotal", "?") + " vs defense "
              + p.getOrDefault("defenseTotal", "?") + ", damage " + p.getOrDefault("damage", 0)
              + ".";
      case "STARTING_OUTPOST_PLACED" ->
          playerName(g, p.get("playerId")) + " built a starting Outpost at " + coordText(p.get("at")) + ".";
      case "STARTING_ROAD_PLACED" ->
          playerName(g, p.get("playerId")) + " built a starting Road from " + coordText(p.get("from"))
              + " to " + coordText(p.get("to")) + ".";
      case "ROAD_BUILT" ->
          playerName(g, p.get("playerId")) + " built a Road"
              + (p.containsKey("from") ? " from " + coordText(p.get("from")) + " to " + coordText(p.get("to")) : "")
              + ".";
      case "OUTPOST_BUILT" ->
          playerName(g, p.get("playerId")) + " built an Outpost"
              + (p.containsKey("at") ? " at " + coordText(p.get("at")) : "")
              + ".";
      case "UNIT_RECRUITED" ->
          playerName(g, p.get("playerId")) + " recruited "
              + String.valueOf(p.getOrDefault("unitType", "a unit")) + ".";
      case "HEX_EXPLORED", "DEEP_EXPLORE_RESOLVED" ->
          playerName(g, p.get("playerId")) + " explored " + coordText(p.get("target"))
              + (String.valueOf(p.getOrDefault("publicReward", "")).isBlank()
                  ? "."
                  : " and gained " + p.get("publicReward") + ".");
      case "RANGER_SCOUT_USED" ->
          playerName(g, p.get("playerId")) + " scouted " + coordText(p.get("target"))
              + ". Possible lead: " + p.getOrDefault("preview", "unknown") + ".";
      case "MAGE_REVEAL_USED" ->
          playerName(g, p.get("playerId")) + " revealed signs at " + coordText(p.get("target"))
              + ". Possible category: " + p.getOrDefault("preview", "unknown") + ".";
      case "COMBAT_RESOLVED" ->
          playerName(g, p.get("playerId")) + " attacked: roll " + p.getOrDefault("roll", "?")
              + ", attack " + p.getOrDefault("attackTotal", "?") + " vs defense "
              + p.getOrDefault("defenseTotal", "?") + ". Damage: " + p.getOrDefault("damage", 0) + ".";
      case "SMALL_RAID_RESOLVED" ->
          playerName(g, p.get("playerId")) + " performed a Small Raid at " + coordText(p.get("target"))
              + ": roll " + p.getOrDefault("roll", "?") + ", total " + p.getOrDefault("total", "?")
              + (Boolean.TRUE.equals(p.get("success")) ? ". Success." : ". Failed.") ;
      case "ARCANE_BOLT_RESOLVED" ->
          playerName(g, p.get("playerId")) + " cast Arcane Bolt at " + coordText(p.get("target"))
              + ": roll " + p.getOrDefault("roll", "?") + ", total " + p.getOrDefault("total", "?")
              + (Boolean.TRUE.equals(p.get("success")) ? ". Hit." : ". Miss.") ;
      case "PRIEST_HEAL_USED" -> playerName(g, p.get("playerId")) + " used Heal.";
      case "PRIEST_BLESS_USED" -> playerName(g, p.get("playerId")) + " used Bless.";
      case "PRIEST_SANCTUARY_USED" -> playerName(g, p.get("playerId")) + " used Sanctuary.";
      case "MAGE_WARD_USED" -> playerName(g, p.get("playerId")) + " placed a Ward.";
      case "MAGE_TRANSMUTE_USED" ->
          playerName(g, p.get("playerId")) + " transmuted "
              + title(String.valueOf(p.getOrDefault("give", "one resource"))) + " into "
              + title(String.valueOf(p.getOrDefault("receive", "another resource"))) + ".";
      case "CONFLICT_DECLARED" ->
          playerName(g, p.get("attackerId")) + " declared " + p.getOrDefault("attackType", "an attack")
              + " against " + playerName(g, p.get("defenderId")) + " at " + coordText(p.get("target"))
              + ". Waiting for defender reaction.";
      case "CONFLICT_RESOLVED" ->
          playerName(g, p.get("attackerId")) + " vs " + playerName(g, p.get("defenderId"))
              + " — " + p.getOrDefault("attackType", "conflict")
              + ", reaction: " + p.getOrDefault("reaction", "NONE")
              + ", attacker d20 " + p.getOrDefault("roll", "?")
              + ", defender d20 " + p.getOrDefault("defenderRoll", "?")
              + ", attack " + p.getOrDefault("attackTotal", "?")
              + " vs defense " + p.getOrDefault("defenseTotal", "?")
              + ", damage " + p.getOrDefault("damage", 0)
              + ", stolen " + p.getOrDefault("resourcesStolen", 0)
              + ("NONE".equals(String.valueOf(p.getOrDefault("stolenResource", "NONE")))
                  ? ""
                  : " " + title(String.valueOf(p.get("stolenResource"))))
              + ", lost " + p.getOrDefault("resourcesLost", 0)
              + ". Cards revealed: attacker " + p.getOrDefault("attackerCard", "NONE")
              + ", defender " + p.getOrDefault("defenderCard", "NONE")
              + ". " + p.getOrDefault("outcome", "");
      case "HERO_MOVED" -> playerName(g, p.get("playerId")) + " moved their Hero.";
      case "RANGER_SWIFT_MOVE_USED" -> playerName(g, p.get("playerId")) + " used Swift Move.";
      case "ENGINEER_REPAIR_USED" -> playerName(g, p.get("playerId")) + " repaired damage.";
      case "MERCHANT_MARKET_DEAL_USED" -> playerName(g, p.get("playerId")) + " made a Market Deal.";
      case "PLAYER_TURN_STARTED" -> "Turn: " + playerName(g, p.get("playerId")) + " is active.";
      case "PLAYER_TURNS_COMPLETE" -> "All players finished their action turns.";
      case "ROUND_STARTED" -> "Round " + p.getOrDefault("round", g.roundNumber) + " begins.";
      case "GAME_OVER" -> "Game over. Winner: " + winnersText(g, p.get("winners")) + ".";
      default -> null;
    };
  }

  private String productionMessage(GameState g, Map<String, Object> payload) {
    Object roll = payload.getOrDefault("roll", g.lastRoll);
    Object production = payload.get("production");
    if (!(production instanceof Collection<?> entries) || entries.isEmpty()) {
      return "🎲 Roll " + roll + ": no settlements produced resources.";
    }
    Map<String, Map<String, Integer>> byPlayer = new LinkedHashMap<>();
    Map<String, Set<Integer>> sourceNumbers = new LinkedHashMap<>();
    for (Object entry : entries) {
      if (entry instanceof ProductionResolver.Production item) {
        String name = playerName(g, item.playerId());
        byPlayer
            .computeIfAbsent(name, ignored -> new LinkedHashMap<>())
            .merge(item.resource().name(), item.amount(), Integer::sum);
        if (item.productionNumber() != 0) {
          sourceNumbers.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(item.productionNumber());
        }
      }
    }
    if (byPlayer.isEmpty()) return "🎲 Roll " + roll + ": production resolved.";
    String gains =
        byPlayer.entrySet().stream()
            .map(entry ->
                entry.getKey() + " gains "
                    + entry.getValue().entrySet().stream()
                        .map(resource -> resource.getValue() + " " + title(resource.getKey()))
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("nothing")
                    + productionSourceNote(roll, sourceNumbers.get(entry.getKey())))
            .reduce((a, b) -> a + "; " + b)
            .orElse("production resolved");
    return "🎲 Roll " + roll + ": " + gains + ".";
  }

  private String productionSourceNote(Object roll, Set<Integer> numbers) {
    if (numbers == null || numbers.isEmpty()) return "";
    Set<String> labels =
        numbers.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    if (labels.size() == 1 && labels.contains(String.valueOf(roll))) return "";
    return " from number " + String.join("/", labels) + " via Beginner mode";
  }

  private String playerName(GameState g, Object id) {
    if (id instanceof UUID uuid) {
      return g.players.stream()
          .filter(player -> player.id.equals(uuid))
          .map(player -> player.color.name().charAt(0) + player.color.name().substring(1).toLowerCase())
          .findFirst()
          .orElse("Unknown");
    }
    return "Unknown";
  }

  private String winnersText(GameState g, Object winners) {
    if (winners instanceof Collection<?> ids) {
      return ids.stream().map(id -> playerName(g, id)).reduce((a, b) -> a + ", " + b).orElse("Unknown");
    }
    return "Unknown";
  }

  private String coordText(Object value) {
    if (value instanceof HexCoordinate c) return c.q() + "," + c.r();
    return "?";
  }

  private String title(String value) {
    return value.charAt(0) + value.substring(1).toLowerCase();
  }

  private DomainEvent event(String type, String key, Object value) {
    return new DomainEvent(type, Map.of(key, value));
  }
}
