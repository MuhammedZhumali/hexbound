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
      case GameCommand.ProposeHeroSwap c -> proposeHeroSwap(game, player, c, events);
      case GameCommand.AcceptHeroSwap c -> acceptHeroSwap(game, player, c, events);
      case GameCommand.RollWorld c -> roll(game, player, c, events);
      case GameCommand.BuyMarketCard c -> buyMarketCard(game, player, c, events);
      case GameCommand.SelectAction c -> select(game, player, c, events);
      case GameCommand.BuildRoad c -> buildRoad(game, player, c, events);
      case GameCommand.BuildOutpost c -> buildOutpost(game, player, c, events);
      case GameCommand.Recruit c -> recruit(game, player, c, events);
      case GameCommand.Trade c -> trade(game, player, c, events);
      case GameCommand.Explore c -> explore(game, player, c, events);
      case GameCommand.MoveHero c -> move(game, player, c, events);
      case GameCommand.Attack ignored ->
          throw DomainException.of(
              "ATTACK_PLAN_REQUIRED", "Submit and lock a complete Attack Plan");
      case GameCommand.LockAttackPlan c -> lockAttackPlan(game, player, c, events);
      case GameCommand.ResolveAttackBatch ignored -> resolveAttackBatch(game, player, events);
      case GameCommand.Fortify ignored -> fortify(game, player, events);
      case GameCommand.ResolveAction ignored -> resolveOrAdvance(game, player, events);
      case GameCommand.EndRound ignored -> endRound(game, events);
      case GameCommand.Debug c -> debug(game, player, c, events);
    }
    List<PlayerState> qualified = victory.qualified(game);
    if (!qualified.isEmpty() && game.phase != GamePhase.GAME_OVER) {
      game.phase = GamePhase.FINAL_ROUND;
      events.add(event("FINAL_ROUND_STARTED", "playerId", qualified.getFirst().id));
    }
    game.eventLog.addAll(events.stream().map(DomainEvent::type).toList());
    return new CommandResult(game, List.copyOf(events));
  }

  private void start(GameState g, List<DomainEvent> e) {
    phase(g, GamePhase.SETUP);
    if (g.players.size() < 3 || g.players.size() > 4)
      throw DomainException.of("INVALID_PLAYER_COUNT", "Hexbound Realms needs 3–4 players");
    List<UUID> initialOrder = new ArrayList<>(g.players.stream().map(p -> p.id).toList());
    Collections.shuffle(initialOrder, new Random(g.seed));
    List<UUID> draftOrder = new ArrayList<>(initialOrder);
    Collections.reverse(draftOrder);
    g.order = new GameOrder(List.copyOf(initialOrder), List.copyOf(draftOrder));
    g.players.sort(Comparator.comparingInt(p -> initialOrder.indexOf(p.id)));
    g.firstPlayerIndex = 0;
    g.currentHeroDraftIndex = 0;
    g.phase = GamePhase.HERO_DRAFT;
    e.add(new DomainEvent("HERO_DRAFT_STARTED", Map.of("draftOrder", draftOrder)));
  }

  private void selectHero(
      GameState g, PlayerState p, GameCommand.SelectHero c, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_DRAFT);
    requireDraftTurn(g, p);
    if (p.heroConfirmed) {
      throw DomainException.of(
          "HERO_ALREADY_CONFIRMED", "Confirmed Hero cannot be changed directly");
    }
    p.temporaryHeroClass = c.heroClass();
    e.add(event("HERO_TEMPORARILY_SELECTED", "playerId", p.id));
  }

  private void confirmHero(GameState g, PlayerState p, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_DRAFT);
    requireDraftTurn(g, p);
    if (p.temporaryHeroClass == null) {
      throw DomainException.of("HERO_NOT_SELECTED", "Select a Hero before confirmation");
    }
    p.hero = HeroState.create(p.temporaryHeroClass, null);
    p.temporaryHeroClass = null;
    p.heroConfirmed = true;
    e.add(
        new DomainEvent(
            "HERO_CONFIRMED", Map.of("playerId", p.id, "heroClass", p.hero.heroClass())));
    g.currentHeroDraftIndex++;
    if (g.currentHeroDraftIndex >= g.order.heroDraftOrder().size()) {
      g.phase = GamePhase.STARTING_PLACEMENT;
      e.add(event("HERO_DRAFT_COMPLETED", "count", g.players.size()));
    } else {
      e.add(
          event(
              "HERO_DRAFT_TURN_CHANGED",
              "playerId",
              g.order.heroDraftOrder().get(g.currentHeroDraftIndex)));
    }
  }

  private void cancelHero(GameState g, PlayerState p, List<DomainEvent> e) {
    phase(g, GamePhase.HERO_DRAFT);
    requireDraftTurn(g, p);
    if (p.heroConfirmed) {
      throw DomainException.of("HERO_ALREADY_CONFIRMED", "Confirmed Hero cannot be cancelled");
    }
    p.temporaryHeroClass = null;
    e.add(event("HERO_SELECTION_CANCELLED", "playerId", p.id));
  }

  private void startingPlacement(GameState g, List<DomainEvent> e) {
    phase(g, GamePhase.STARTING_PLACEMENT);
    if (g.players.stream().anyMatch(player -> !player.heroConfirmed || player.hero == null)) {
      throw DomainException.of("HERO_DRAFT_INCOMPLETE", "Every player must confirm a Hero");
    }
    g.status = GameStatus.ACTIVE;
    g.roundNumber = 1;
    List<MapHex> outer =
        g.map.stream()
            .filter(
                h ->
                    h.coordinate().distanceTo(new HexCoordinate(0, 0)) == 3 && h.resource() != null)
            .toList();
    List<PlayerState> placementOrder =
        g.order.initialTurnOrder().stream()
            .map(
                id ->
                    g.players.stream()
                        .filter(player -> player.id.equals(id))
                        .findFirst()
                        .orElseThrow())
            .toList();
    for (int i = 0; i < placementOrder.size(); i++) {
      PlayerState p = placementOrder.get(i);
      HexCoordinate at =
          outer.get((i * outer.size() / g.players.size()) % outer.size()).coordinate();
      p.settlements.add(new SettlementState(UUID.randomUUID(), at, SettlementLevel.OUTPOST, 2));
      p.hero = new HeroState(p.hero.heroClass(), 3, p.hero.mana(), p.hero.grace(), at, false);
    }
    List<PlayerState> roadOrder = new ArrayList<>(placementOrder);
    Collections.reverse(roadOrder);
    for (PlayerState p : roadOrder) {
      HexCoordinate at = p.settlements.getFirst().location();
      HexCoordinate next =
          at.neighbors().stream()
              .filter(n -> g.map.stream().anyMatch(h -> h.coordinate().equals(n)))
              .findFirst()
              .orElseThrow();
      p.roads.add(new RoadState(UUID.randomUUID(), at, next));
      p.units.add(
          new UnitState(UUID.randomUUID(), UnitType.MILITIA, FatigueState.READY, false, true, 0));
    }
    g.phase = GamePhase.WORLD;
    e.add(event("GAME_STARTED", "round", 1));
  }

  private void requireDraftTurn(GameState g, PlayerState p) {
    UUID expected = g.order.heroDraftOrder().get(g.currentHeroDraftIndex);
    if (!expected.equals(p.id)) {
      throw DomainException.of("NOT_PLAYER_TURN", "Another player is currently drafting");
    }
  }

  private void proposeHeroSwap(
      GameState g, PlayerState p, GameCommand.ProposeHeroSwap c, List<DomainEvent> e) {
    phase(g, GamePhase.STARTING_PLACEMENT);
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
    phase(g, GamePhase.STARTING_PLACEMENT);
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
    phase(g, GamePhase.WORLD);
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
    } else {
      ProductionResolver.ProductionReport report = production.resolve(g, roll);
      g.phase = GamePhase.MARKET;
      e.add(
          new DomainEvent(
              "PRODUCTION_RESOLVED", Map.of("roll", roll, "production", report.production())));
    }
  }

  private void buyMarketCard(
      GameState g, PlayerState p, GameCommand.BuyMarketCard c, List<DomainEvent> e) {
    phase(g, GamePhase.MARKET);
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
    phase(g, GamePhase.PLANNING);
    if (p.actionLocked) throw DomainException.of("ACTION_LOCKED", "Action already locked");
    if (c.action() == p.previousAction)
      throw DomainException.of("ACTION_ON_COOLDOWN", "Choose a different action this round");
    p.selectedAction = c.action();
    p.actionLocked = true;
    e.add(event("ACTION_SELECTED", "playerId", p.id));
    if (g.players.stream().allMatch(x -> x.actionLocked)) {
      g.phase = GamePhase.REVEAL;
      e.add(event("ALL_ACTIONS_LOCKED", "count", g.players.size()));
    }
  }

  private void buildRoad(GameState g, PlayerState p, GameCommand.BuildRoad c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.BUILD);
    Resources cost = new Resources(1, 0, 0, 1, 0);
    if (!building.roadLegal(g, p, c.from(), c.to()))
      throw DomainException.of("INVALID_TARGET", "Road must extend your connected network");
    p.resources = p.resources.subtract(cost);
    p.roads.add(new RoadState(UUID.randomUUID(), c.from(), c.to()));
    finishAction(g, p, e, "ROAD_BUILT");
  }

  private void buildOutpost(
      GameState g, PlayerState p, GameCommand.BuildOutpost c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.BUILD);
    Resources cost = new Resources(1, 1, 0, 1, 0);
    if (!building.outpostLegal(g, p, c.at()))
      throw DomainException.of("INVALID_TARGET", "Outpost target is not legal");
    p.resources = p.resources.subtract(cost);
    p.settlements.add(new SettlementState(UUID.randomUUID(), c.at(), SettlementLevel.OUTPOST, 2));
    finishAction(g, p, e, "OUTPOST_BUILT");
  }

  private void recruit(GameState g, PlayerState p, GameCommand.Recruit c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.RECRUIT);
    Resources cost =
        switch (c.unitType()) {
          case MILITIA -> new Resources(0, 1, 0, 0, 0);
          case INFANTRY -> new Resources(0, 1, 1, 0, 0);
          case ARCHER -> new Resources(1, 1, 0, 0, 0);
          case CAVALRY -> new Resources(0, 2, 0, 0, 1);
          case MERCENARY -> new Resources(0, 0, 0, 0, 2);
        };
    p.resources = p.resources.subtract(cost);
    p.units.add(
        new UnitState(
            UUID.randomUUID(),
            c.unitType(),
            FatigueState.READY,
            false,
            false,
            c.unitType() == UnitType.MERCENARY ? g.roundNumber + 1 : 0));
    finishAction(g, p, e, "UNIT_RECRUITED");
  }

  private void trade(GameState g, PlayerState p, GameCommand.Trade c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.TRADE);
    if (c.give() == c.receive()) {
      throw DomainException.of("INVALID_TARGET", "Trade resources must be different");
    }
    p.resources = p.resources.subtract(resourceCost(c.give(), 1)).add(c.receive(), 1);
    e.add(
        new DomainEvent(
            "TRADE_COMPLETED",
            Map.of("playerId", p.id, "gave", c.give(), "received", c.receive())));
    finishAction(g, p, e, "TRADE_RESOLVED");
  }

  private void explore(GameState g, PlayerState p, GameCommand.Explore c, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.EXPLORE);
    if (p.hero.location() == null) {
      throw DomainException.of("INVALID_TARGET", "Hero is not on the map");
    }
    int range = p.hero.heroClass() == HeroClass.RANGER ? 2 : 1;
    MapHex target =
        g.map.stream()
            .filter(h -> h.coordinate().equals(c.target()))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_TARGET", "Unknown hex"));
    if (target.terrain() != TerrainType.RUIN || p.hero.location().distanceTo(c.target()) > range) {
      throw DomainException.of("INVALID_TARGET", "Select an Ancient Ruin within hero range");
    }
    if (!p.exploredRuins.add(c.target())) {
      throw DomainException.of("INVALID_TARGET", "This ruin was already explored by the hero");
    }
    p.resources = p.resources.add(ResourceType.GOLD, 1);
    p.reputation = Math.min(12, p.reputation + 1);
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
    e.add(
        new DomainEvent(
            "RUIN_EXPLORED", Map.of("playerId", p.id, "target", c.target(), "gold", 1)));
    finishAction(g, p, e, "EXPLORE_RESOLVED");
  }

  private void fortify(GameState g, PlayerState p, List<DomainEvent> e) {
    resolutionAction(g, p, ActionType.FORTIFY);
    p.resources = p.resources.subtract(new Resources(1, 1, 1, 0, 0));
    p.fortificationTokens += p.hero.heroClass() == HeroClass.ENGINEER ? 4 : 3;
    finishAction(g, p, e, "FORTIFIED");
  }

  private void move(GameState g, PlayerState p, GameCommand.MoveHero c, List<DomainEvent> e) {
    if (p.hero.location() == null)
      throw DomainException.of("INVALID_TARGET", "Hero is not on the map");
    int range = p.hero.heroClass() == HeroClass.RANGER ? 2 : 1;
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
    resolutionAction(g, p, ActionType.ATTACK);
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
    int defenseArmy = 0, baseExtra = 0;
    if (monster != null) baseExtra = Math.max(0, monster.strength() - 10);
    else {
      PlayerState defender =
          g.players.stream()
              .filter(x -> x.settlements.stream().anyMatch(s -> s.location().equals(c.target())))
              .findFirst()
              .orElseThrow(
                  () -> DomainException.of("INVALID_TARGET", "No enemy or monster at target"));
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
    finishAction(g, p, e, "ATTACK_RESOLVED");
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
      g.phase = GamePhase.MARKET;
      e.add(event("MONSTER_EVENT_CLOSED", "round", g.roundNumber));
      return;
    }
    if (g.phase == GamePhase.MARKET) {
      g.phase = GamePhase.PLANNING;
      e.add(event("PLANNING_STARTED", "round", g.roundNumber));
      return;
    }
    if (g.phase == GamePhase.REVEAL) {
      g.phase = GamePhase.RESOLUTION;
      e.add(
          new DomainEvent(
              "ACTIONS_REVEALED",
              Map.of(
                  "actions",
                  g.players.stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              x -> x.id, x -> x.selectedAction.name())))));
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
    if (g.players.stream().noneMatch(x -> x.actionLocked)) {
      g.phase = GamePhase.END_ROUND;
      e.add(event("RESOLUTION_COMPLETE", "round", g.roundNumber));
    }
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
    for (PlayerState p : g.players) {
      p.hero =
          new HeroState(
              p.hero.heroClass(),
              p.hero.hp(),
              Math.min(
                  p.hero.heroClass() == HeroClass.MAGE ? 3 : p.hero.mana(),
                  p.hero.mana() + (p.hero.heroClass() == HeroClass.MAGE ? 1 : 0)),
              p.hero.grace(),
              p.hero.location(),
              p.hero.defeated());
      p.units =
          p.units.stream().map(u -> u.garrison() ? u : new FatigueResolver().recover(u)).toList();
    }
    g.roundNumber++;
    g.firstPlayerIndex = (g.firstPlayerIndex + 1) % g.players.size();
    g.phase = GamePhase.WORLD;
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
        if (g.phase == GamePhase.WORLD) g.phase = GamePhase.MARKET;
        else if (g.phase == GamePhase.MARKET) g.phase = GamePhase.PLANNING;
        else if (g.phase == GamePhase.PLANNING) g.phase = GamePhase.RESOLUTION;
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

  private DomainEvent event(String type, String key, Object value) {
    return new DomainEvent(type, Map.of(key, value));
  }
}
