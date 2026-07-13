package com.hexboundrealms.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.hexboundrealms.api.dto.Requests.*;
import com.hexboundrealms.api.dto.Views.*;
import com.hexboundrealms.domain.game.*;
import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import com.hexboundrealms.domain.settlement.BuildingValidator;
import com.hexboundrealms.infrastructure.persistence.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameApplicationService {
  private final GameJpaRepository games;
  private final GameEventJpaRepository events;
  private final ObjectMapper json;
  private final GameEngine engine;
  private final SimpMessagingTemplate websocket;
  private final GameViewMapper views = new GameViewMapper();
  private final CardCatalog cards = new CardCatalog();
  private final BuildingValidator building = new BuildingValidator();

  public GameApplicationService(
      GameJpaRepository games,
      GameEventJpaRepository events,
      ObjectMapper json,
      GameEngine engine,
      SimpMessagingTemplate websocket) {
    this.games = games;
    this.events = events;
    this.json = json;
    this.engine = engine;
    this.websocket = websocket;
  }

  @Transactional
  public PublicGameView create(CreateGame request) {
    GameState g = new GameState();
    g.id = UUID.randomUUID();
    g.name = request.name();
    g.seed = request.seed() == null ? new Random().nextLong() : request.seed();
    g.maxPlayers = request.maxPlayers();
    g.debugMode = request.debugMode();
    g.map = new ArrayList<>(new MapGenerator().generate(g.seed));
    g.deck = new ArrayList<>(cards.all());
    Collections.shuffle(g.deck, new Random(g.seed ^ 0xCAFE));
    for (int i = 0; i < 5; i++) g.market.add(g.deck.removeFirst());
    GameEntity entity =
        new GameEntity(g.id, g.seed, g.status.name(), g.phase.name(), g.roundNumber, write(g));
    games.save(entity);
    return views.publicView(g);
  }

  @Transactional
  public JoinResult join(UUID gameId, JoinGame request) {
    GameEntity entity = entity(gameId);
    GameState g = read(entity);
    if (g.status != GameStatus.LOBBY)
      throw DomainException.of("GAME_ALREADY_STARTED", "This game already started");
    if (g.players.size() >= g.maxPlayers) throw DomainException.of("GAME_FULL", "No seats remain");
    if (g.players.stream().anyMatch(p -> p.color == request.playerColor()))
      throw DomainException.of("COLOR_TAKEN", "Choose another color");
    PlayerState p =
        new PlayerState(
            UUID.randomUUID(),
            request.displayName(),
            request.playerColor(),
            UUID.randomUUID().toString());
    g.players.add(p);
    g.version = entity.getVersion() + 1;
    entity.update(g.status.name(), g.phase.name(), g.roundNumber, write(g));
    games.saveAndFlush(entity);
    return new JoinResult(p.id, p.accessToken, g.version);
  }

  @Transactional(readOnly = true)
  public PublicGameView get(UUID id) {
    GameEntity e = entity(id);
    GameState g = read(e);
    g.version = e.getVersion();
    return views.publicView(g);
  }

  @Transactional(readOnly = true)
  public PrivatePlayerView privateView(UUID id, UUID playerId, String token) {
    GameEntity e = entity(id);
    GameState g = read(e);
    g.version = e.getVersion();
    PlayerState p =
        g.players.stream()
            .filter(x -> x.id.equals(playerId))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_PLAYER", "Unknown player"));
    if (!Objects.equals(p.accessToken, token))
      throw DomainException.of("INVALID_ACCESS_TOKEN", "Private player token is invalid");
    return views.privateView(g, p);
  }

  @Transactional
  public PublicGameView start(UUID id) {
    GameEntity e = entity(id);
    GameState g = read(e);
    if (g.players.isEmpty()) throw DomainException.of("INVALID_PLAYER_COUNT", "Join players first");
    CommandResult result = engine.execute(g, g.players.getFirst().id, new GameCommand.Start());
    return persist(e, g, UUID.randomUUID(), result);
  }

  @Transactional
  public PublicGameView command(UUID id, CommandEnvelope envelope) {
    GameEntity entity = entity(id);
    GameState g = read(entity);
    Optional<GameEventEntity> duplicate = events.findByGameIdAndCommandId(id, envelope.commandId());
    if (duplicate.isPresent()) {
      g.version = entity.getVersion();
      return views.publicView(g);
    }
    if (entity.getVersion() != envelope.expectedVersion())
      throw DomainException.of(
          "VERSION_CONFLICT",
          "Expected version "
              + envelope.expectedVersion()
              + " but latest is "
              + entity.getVersion());
    CommandResult result = engine.execute(g, envelope.playerId(), toCommand(envelope));
    return persist(entity, g, envelope.commandId(), result);
  }

  private PublicGameView persist(
      GameEntity entity, GameState g, UUID commandId, CommandResult result) {
    g.version = entity.getVersion() + 1;
    entity.update(g.status.name(), g.phase.name(), g.roundNumber, write(g));
    long sequence = events.countByGameId(g.id) + 1;
    events.save(
        new GameEventEntity(
            g.id,
            sequence,
            commandId,
            result.events().isEmpty() ? "COMMAND_ACCEPTED" : result.events().getLast().type(),
            write(result.events())));
    try {
      games.saveAndFlush(entity);
      events.flush();
    } catch (DataIntegrityViolationException ex) {
      throw DomainException.of("DUPLICATE_COMMAND", "Command was already accepted");
    }
    PublicGameView view = views.publicView(g);
    websocket.convertAndSend(
        "/topic/games/" + g.id, new ServerEvent(g.id, g.version, sequence, result.events(), view));
    return view;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> eventLog(UUID id) {
    return events.findByGameIdOrderBySequenceNumber(id).stream()
        .map(
            e ->
                Map.<String, Object>of(
                    "sequenceNumber",
                    e.getSequenceNumber(),
                    "type",
                    e.getEventType(),
                    "payload",
                    readTree(e.getEventJson())))
        .toList();
  }

  @Transactional(readOnly = true)
  public LegalActions legal(UUID id, UUID playerId) {
    GameState g = read(entity(id));
    PlayerState p =
        g.players.stream()
            .filter(x -> x.id.equals(playerId))
            .findFirst()
            .orElseThrow(() -> DomainException.of("INVALID_PLAYER", "Unknown player"));
    if (g.phase == GamePhase.WAITING_FOR_DEFENDER_REACTION) {
      return new LegalActions(
          List.of(),
          List.of(),
          List.of(),
          List.of(),
          defenderReactionActions(g, playerId));
    }
    boolean placementTurn = g.phase == GamePhase.STARTING_PLACEMENT
        && placementPlayerId(g).map(playerId::equals).orElse(false);
    List<String> buildTargets = g.map.stream()
        .filter(h -> g.phase == GamePhase.STARTING_PLACEMENT
            ? placementTurn && g.startingPlacementStep == StartingPlacementStep.OUTPOST
                && startingOutpostLegal(g, h)
            : building.outpostLegal(g, p, h.coordinate()))
        .map(h -> h.coordinate().q() + "," + h.coordinate().r()).toList();
    List<String> movementTargets = placementTurn && g.startingPlacementStep == StartingPlacementStep.ROAD
        ? p.settlements.getFirst().location().neighbors().stream()
            .filter(at -> g.map.stream().anyMatch(h -> h.coordinate().equals(at)))
            .filter(at -> g.monsters.stream().noneMatch(m -> m.location().equals(at)))
            .map(at -> at.q() + "," + at.r()).toList()
        : g.phase == GamePhase.PLAYER_TURNS && p.hero != null && p.hero.location() != null
            ? p.hero.location().neighbors().stream()
                .filter(at -> g.map.stream().anyMatch(h -> h.coordinate().equals(at)))
                .filter(at -> g.monsters.stream().noneMatch(m -> m.location().equals(at)))
                .map(at -> at.q() + "," + at.r()).toList()
            : List.of();
    List<String> actions =
        (g.phase == GamePhase.ACTION_CARD_SELECTION
                || g.phase == GamePhase.PLANNING
                || (g.phase == GamePhase.PLAYER_TURNS
                    && currentTurnPlayerId(g).map(playerId::equals).orElse(false)))
            ? Arrays.stream(ActionType.values())
                .filter(a -> a != p.previousAction)
                .map(Enum::name)
                .toList()
            : List.of();
    List<String> attackTargets = attackTargets(g, p);
    List<String> roadTargets = roadTargets(g, p);
    return new LegalActions(
        actions,
        movementTargets,
        buildTargets,
        attackTargets,
        visibleActions(g, p, playerId, movementTargets, buildTargets, roadTargets, attackTargets));
  }

  private List<LegalActionDto> visibleActions(
      GameState g,
      PlayerState p,
      UUID playerId,
      List<String> movementTargets,
      List<String> buildTargets,
      List<String> roadTargets,
      List<String> attackTargets) {
    if (g.phase != GamePhase.PLAYER_TURNS || currentTurnPlayerId(g).map(id -> !id.equals(playerId)).orElse(true)) {
      return List.of();
    }
    List<String> exploreTargets = exploreTargets(g, p);
    List<String> friendlyTargets = friendlyTargets(p);
    List<LegalActionDto> result = new ArrayList<>();
    result.add(action("MOVE_HERO", "Move Hero", "Move your Hero 1 hex.", 1, Resources.none(),
        p.basicActionPoints >= 1 && !movementTargets.isEmpty(), apOrTargetReason(p, 1, movementTargets),
        true, TargetType.HEX, movementTargets));
    int exploreCost = p.selectedAction == ActionType.EXPLORE && !p.freeExploreUsed ? 0 : 1;
    result.add(action("EXPLORE_HEX", "Explore Hex", "Explore current or nearby terrain.", exploreCost,
        Resources.none(), p.basicActionPoints >= exploreCost && !exploreTargets.isEmpty(),
        apOrTargetReason(p, exploreCost, exploreTargets), true, TargetType.HEX, exploreTargets));
    int deepCost = p.selectedAction == ActionType.EXPLORE ? 1 : 2;
    result.add(action("DEEP_EXPLORE", "Deep Explore", "Spend more time for better exploration odds.", deepCost,
        Resources.none(), p.basicActionPoints >= deepCost && !exploreTargets.isEmpty(),
        apOrTargetReason(p, deepCost, exploreTargets), true, TargetType.HEX, exploreTargets));
    result.add(action("BUILD_ROAD", "Build Road", "Build a road from your network.", 1,
        new Resources(1, 0, 0, 1, 0), p.basicActionPoints >= 1 && !roadTargets.isEmpty(),
        apOrTargetReason(p, 1, roadTargets), true, TargetType.HEX, roadTargets));
    result.add(action("BUILD_OUTPOST", "Build Outpost", "Found an outpost on a legal hex.", p.selectedAction == ActionType.BUILD ? 1 : 2,
        new Resources(1, 0, 0, 1, 0), p.basicActionPoints >= (p.selectedAction == ActionType.BUILD ? 1 : 2) && !buildTargets.isEmpty(),
        apOrTargetReason(p, p.selectedAction == ActionType.BUILD ? 1 : 2, buildTargets), true, TargetType.FRIENDLY_HEX, buildTargets));
    int recruitCost = p.selectedAction == ActionType.RECRUIT && !p.freeMilitiaUsed ? 0 : 1;
    result.add(action("RECRUIT", "Recruit Militia", "Add one Militia.", recruitCost,
        new Resources(0, 1, 0, 0, 0), p.basicActionPoints >= recruitCost && p.resources.food() >= 1,
        p.resources.food() < 1 ? "You need 1 Food." : p.basicActionPoints < recruitCost ? "Not enough AP." : null,
        false, TargetType.NONE, List.of()));
    result.add(action("BANK_TRADE", "Bank Trade", "Convert resources through the bank.", 1, Resources.none(),
        p.basicActionPoints >= 1, p.basicActionPoints < 1 ? "You need 1 AP." : null, false, TargetType.NONE, List.of()));
    result.add(action("BUY_MARKET_CARD", "Buy Card", "Buy one visible market card.", p.selectedAction == ActionType.TRADE && !p.freeTradeCardBuyUsed ? 0 : 1,
        Resources.none(), p.basicActionPoints >= (p.selectedAction == ActionType.TRADE && !p.freeTradeCardBuyUsed ? 0 : 1) && !g.market.isEmpty(),
        g.market.isEmpty() ? "No market cards are available." : null, false, TargetType.CARD, List.of()));
    result.add(action("SMALL_RAID", "Small Raid", "A 1 AP raid against an adjacent enemy or monster.", 1,
        Resources.none(), p.basicActionPoints >= 1 && !attackTargets.isEmpty(),
        apOrTargetReason(p, 1, attackTargets), true, TargetType.ENEMY_HEX, attackTargets));
    int fullAttackCost = p.selectedAction == ActionType.ATTACK && !p.attackDiscountUsed ? 1 : 2;
    boolean fullAttackAvailable = p.selectedAction == ActionType.ATTACK && p.basicActionPoints >= fullAttackCost && !attackTargets.isEmpty();
    result.add(action("FULL_ATTACK", "Full Attack", "A stronger attack that requires the ATTACK card.", fullAttackCost,
        Resources.none(), fullAttackAvailable,
        p.selectedAction != ActionType.ATTACK ? "Requires ATTACK action card." : apOrTargetReason(p, fullAttackCost, attackTargets),
        true, TargetType.ENEMY_HEX, attackTargets));
    addHeroActions(result, g, p, exploreTargets, friendlyTargets, roadTargets, attackTargets, movementTargets);
    result.add(action("END_PLAYER_TURN", "End Turn", "Finish your turn now.", 0, Resources.none(),
        true, null, false, TargetType.NONE, List.of()));
    return result;
  }

  private List<LegalActionDto> defenderReactionActions(GameState g, UUID playerId) {
    if (g.pendingConflict == null || !g.pendingConflict.defenderPlayerId().equals(playerId)) {
      return List.of();
    }
    return List.of(
        action("DEFENDER_REACTION_SHIELD", "Shield", "Reduce or block incoming damage/resource loss.", 0,
            Resources.none(), true, null, false, TargetType.NONE, List.of()),
        action("DEFENDER_REACTION_COUNTERATTACK", "Counterattack", "Risky response; can punish a failed attack.", 0,
            Resources.none(), true, null, false, TargetType.NONE, List.of()),
        action("DEFENDER_REACTION_EVACUATION", "Evacuation", "Avoid the worst effect but lose a smaller guaranteed amount.", 0,
            Resources.none(), true, null, false, TargetType.NONE, List.of()),
        action("DEFENDER_REACTION_NONE", "No Reaction", "Accept the attack and conserve defenses.", 0,
            Resources.none(), true, null, false, TargetType.NONE, List.of()));
  }

  private void addHeroActions(
      List<LegalActionDto> result,
      GameState g,
      PlayerState p,
      List<String> exploreTargets,
      List<String> friendlyTargets,
      List<String> roadTargets,
      List<String> attackTargets,
      List<String> movementTargets) {
    if (p.hero == null) return;
    switch (p.hero.heroClass()) {
      case PRIEST -> {
        result.add(action("PRIEST_HEAL", "Heal", "Restore 1 HP or remove unit damage.", 1,
            Map.of("grace", 1), p.basicActionPoints >= 1 && p.hero.grace() >= 1 && hasDamagedFriendly(p),
            p.hero.grace() < 1 ? "You need 1 Grace." : p.basicActionPoints < 1 ? "You need 1 AP." : !hasDamagedFriendly(p) ? "No damaged friendly target nearby." : null,
            true, TargetType.HERO, friendlyTargets));
        result.add(action("PRIEST_BLESS", "Bless", "Next d20 roll gets +2.", 1,
            Map.of("grace", 1), p.basicActionPoints >= 1 && p.hero.grace() >= 1,
            p.hero.grace() < 1 ? "You need 1 Grace." : p.basicActionPoints < 1 ? "You need 1 AP." : null,
            true, TargetType.HERO, friendlyTargets));
        result.add(action("PRIEST_SANCTUARY", "Sanctuary", "Protect a friendly Hero from the next direct wound.", 2,
            Map.of("grace", 2), p.basicActionPoints >= 2 && p.hero.grace() >= 2,
            p.hero.grace() < 2 ? "You need 2 Grace." : p.basicActionPoints < 2 ? "You need 2 AP." : null,
            true, TargetType.HERO, friendlyTargets));
      }
      case MAGE -> {
        result.add(action("ARCANE_BOLT", "Arcane Bolt", "d20 + 2 magical attack against a monster.", 1,
            Map.of("mana", 1), p.basicActionPoints >= 1 && p.hero.mana() >= 1 && !attackTargets.isEmpty(),
            p.hero.mana() < 1 ? "You need 1 Mana." : p.basicActionPoints < 1 ? "You need 1 AP." : attackTargets.isEmpty() ? "No valid target in range." : null,
            true, TargetType.MONSTER_HEX, attackTargets));
        result.add(action("MAGE_WARD", "Ward", "+2 Defense against the next attack.", 1,
            Map.of("mana", 1), p.basicActionPoints >= 1 && p.hero.mana() >= 1,
            p.hero.mana() < 1 ? "You need 1 Mana." : p.basicActionPoints < 1 ? "You need 1 AP." : null,
            true, TargetType.FRIENDLY_HEX, friendlyTargets));
        result.add(action("MAGE_REVEAL", "Reveal", "Preview an adjacent exploration category.", 1,
            Map.of("mana", 1), p.basicActionPoints >= 1 && p.hero.mana() >= 1 && !exploreTargets.isEmpty(),
            p.hero.mana() < 1 ? "You need 1 Mana." : p.basicActionPoints < 1 ? "You need 1 AP." : "No adjacent explorable hex.",
            true, TargetType.HEX, exploreTargets));
        result.add(action("TRANSMUTE", "Transmute", "Convert 1 owned resource into another.", 1,
            Map.of("mana", 1), p.basicActionPoints >= 1 && p.hero.mana() >= 1 && totalResources(p.resources) > 0,
            p.hero.mana() < 1 ? "You need 1 Mana." : p.basicActionPoints < 1 ? "You need 1 AP." : "You have no resources to convert.",
            false, TargetType.NONE, List.of()));
      }
      case KNIGHT -> result.add(action("CHALLENGE", "Command Attack", "Passive: +2 Attack Total during Full Attack.", 0,
          Resources.none(), true, null, false, TargetType.NONE, List.of()));
      case MERCHANT -> result.add(action("MARKET_DEAL", "Market Deal", "Improved 3:1 bank trade. TRADE card can make it 2:1 once.", 1,
          Resources.none(), p.basicActionPoints >= 1, p.basicActionPoints < 1 ? "You need 1 AP." : null,
          false, TargetType.NONE, List.of()));
      case RANGER -> {
        result.add(action("SCOUT", "Scout", "Preview a nearby exploration result.", p.selectedAction == ActionType.EXPLORE ? 0 : 1,
            Resources.none(), !exploreTargets.isEmpty(), exploreTargets.isEmpty() ? "No adjacent explorable hex." : null,
            true, TargetType.HEX, exploreTargets));
        result.add(action("SWIFT_MOVE", "Swift Move", "Move Hero 1 extra hex once per turn.", 0,
            Resources.none(), !p.swiftMoveUsed && !movementTargets.isEmpty(),
            p.swiftMoveUsed ? "Swift Move already used." : movementTargets.isEmpty() ? "No valid movement target." : null,
            true, TargetType.HEX, movementTargets));
      }
      case ENGINEER -> {
        result.add(action("QUICK_ROAD", "Quick Road", "Build one road for 0 AP once per turn.", 0,
            new Resources(1, 0, 0, 1, 0), !p.quickRoadUsed && !roadTargets.isEmpty(),
            p.quickRoadUsed ? "Quick Road already used." : apOrTargetReason(p, 0, roadTargets),
            true, TargetType.HEX, roadTargets));
        result.add(action("REPAIR", "Repair", "Repair 1 damage once per turn.", 0,
            Resources.none(), !p.repairUsed, p.repairUsed ? "Repair already used." : null,
            true, TargetType.FRIENDLY_HEX, friendlyTargets));
      }
    }
  }

  private Optional<UUID> currentTurnPlayerId(GameState g) {
    if (g.phase == GamePhase.WAITING_FOR_DEFENDER_REACTION && g.pendingConflict != null) {
      return Optional.of(g.pendingConflict.defenderPlayerId());
    }
    if (g.phase != GamePhase.PLAYER_TURNS || g.players.isEmpty()) return Optional.empty();
    if (!g.actionTurnOrder.isEmpty() && g.actionTurnOrderIndex < g.actionTurnOrder.size()) {
      return Optional.of(g.actionTurnOrder.get(g.actionTurnOrderIndex));
    }
    return Optional.of(g.players.get(g.currentTurnIndex).id);
  }

  private Optional<UUID> placementPlayerId(GameState g) {
    if (g.order.initialTurnOrder().isEmpty() || g.startingPlacementStep == StartingPlacementStep.COMPLETE)
      return Optional.empty();
    int index = g.startingPlacementStep == StartingPlacementStep.ROAD
        ? g.order.initialTurnOrder().size() - 1 - g.currentStartingPlacementIndex
        : g.currentStartingPlacementIndex;
    return Optional.of(g.order.initialTurnOrder().get(index));
  }

  private boolean startingOutpostLegal(GameState g, com.hexboundrealms.domain.map.MapHex h) {
    boolean buildable = switch (h.terrain()) {
      case FOREST, FIELD, MOUNTAIN, QUARRY, TRADE_LAND -> true;
      default -> false;
    };
    return buildable
        && g.monsters.stream().noneMatch(m -> m.location().equals(h.coordinate()))
        && g.players.stream().flatMap(x -> x.settlements.stream())
            .allMatch(s -> s.location().distanceTo(h.coordinate()) >= 2);
  }

  private LegalActionDto action(
      String actionType,
      String label,
      String description,
      int apCost,
      Resources resourceCost,
      boolean available,
      String disabledReason,
      boolean requiresTarget,
      TargetType targetType,
      List<String> hexTargets) {
    return action(
        actionType,
        label,
        description,
        apCost,
        Map.of(
            "wood", resourceCost.wood(),
            "food", resourceCost.food(),
            "ore", resourceCost.ore(),
            "stone", resourceCost.stone(),
            "gold", resourceCost.gold()),
        available,
        disabledReason,
        requiresTarget,
        targetType,
        hexTargets);
  }

  private LegalActionDto action(
      String actionType,
      String label,
      String description,
      int apCost,
      Map<String, Integer> resourceCost,
      boolean available,
      String disabledReason,
      boolean requiresTarget,
      TargetType targetType,
      List<String> hexTargets) {
    return new LegalActionDto(
        actionType,
        label,
        description,
        apCost,
        resourceCost,
        available,
        available ? null : disabledReason,
        requiresTarget,
        targetType,
        hexTargets,
        List.of(),
        List.of());
  }

  private String apOrTargetReason(PlayerState p, int cost, List<String> targets) {
    if (p.basicActionPoints < cost) return "You only have " + p.basicActionPoints + " AP remaining.";
    if (targets.isEmpty()) return "No valid target.";
    return null;
  }

  private List<String> exploreTargets(GameState g, PlayerState p) {
    if (p.hero == null || p.hero.location() == null) return List.of();
    int range = p.hero.heroClass() == HeroClass.RANGER ? 2 : 1;
    return g.map.stream()
        .filter(h -> p.hero.location().distanceTo(h.coordinate()) <= range)
        .filter(h -> !p.exploredHexes.contains(h.coordinate()))
        .map(h -> hexId(h.coordinate()))
        .toList();
  }

  private List<String> attackTargets(GameState g, PlayerState p) {
    if (p.hero == null || p.hero.location() == null) return List.of();
    return g.map.stream()
        .filter(h -> p.hero.location().distanceTo(h.coordinate()) == 1)
        .filter(h -> g.monsters.stream().anyMatch(m -> m.location().equals(h.coordinate()))
            || g.players.stream()
                .filter(other -> !other.id.equals(p.id))
                .flatMap(other -> other.settlements.stream())
                .anyMatch(s -> s.location().equals(h.coordinate())))
        .map(h -> hexId(h.coordinate()))
        .toList();
  }

  private List<String> friendlyTargets(PlayerState p) {
    List<String> targets = new ArrayList<>();
    if (p.hero != null && p.hero.location() != null) targets.add(hexId(p.hero.location()));
    p.settlements.stream().map(SettlementState::location).map(this::hexId).forEach(targets::add);
    return targets.stream().distinct().toList();
  }

  private List<String> buildTargets(GameState g, PlayerState p) {
    return g.map.stream()
        .filter(h -> building.outpostLegal(g, p, h.coordinate()))
        .map(h -> hexId(h.coordinate()))
        .toList();
  }

  private List<String> roadTargets(GameState g, PlayerState p) {
    List<HexCoordinate> sources = new ArrayList<>();
    p.settlements.stream().map(SettlementState::location).forEach(sources::add);
    p.roads.stream().flatMap(road -> java.util.stream.Stream.of(road.from(), road.to())).forEach(sources::add);
    return sources.stream()
        .distinct()
        .flatMap(source -> source.neighbors().stream()
            .filter(target -> building.roadLegal(g, p, source, target)))
        .map(this::hexId)
        .distinct()
        .toList();
  }

  private boolean hasDamagedFriendly(PlayerState p) {
    return (p.hero != null && p.hero.hp() < 3) || p.units.stream().anyMatch(UnitState::wounded);
  }

  private int totalResources(Resources resources) {
    return resources.wood() + resources.food() + resources.ore() + resources.stone() + resources.gold();
  }

  private String hexId(HexCoordinate coordinate) {
    return coordinate.q() + "," + coordinate.r();
  }

  public List<Card> cards() {
    return cards.all();
  }

  @Transactional(readOnly = true)
  public HeroDraftView heroDraft(UUID id) {
    GameState g = read(entity(id));
    Map<UUID, HeroClass> selections =
        g.players.stream()
            .filter(player -> player.heroConfirmed && player.hero != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    player -> player.id, player -> player.hero.heroClass()));
    UUID current =
        g.phase == GamePhase.HERO_DRAFT && g.currentHeroDraftIndex < g.order.heroDraftOrder().size()
            ? g.order.heroDraftOrder().get(g.currentHeroDraftIndex)
            : g.phase == GamePhase.HERO_SELECTION
                ? g.players.stream().filter(player -> !player.heroConfirmed).map(player -> player.id).findFirst().orElse(null)
                : null;
    return new HeroDraftView(
        g.phase,
        g.order.initialTurnOrder(),
        g.order.heroDraftOrder(),
        current,
        List.of(HeroClass.values()),
        selections,
        true);
  }

  private GameCommand toCommand(CommandEnvelope e) {
    JsonNode p = e.payload() == null ? json.createObjectNode() : e.payload();
    return switch (e.type()) {
      case "ROLL_WORLD", "ROLL_WORLD_DICE" ->
          new GameCommand.RollWorld(p.has("forced") ? p.get("forced").asInt() : null);
      case "BUY_MARKET_CARD", "BUY_BONUS_CARD" -> new GameCommand.BuyMarketCard(UUID.fromString(text(p, "cardId")));
      case "SELECT_HERO" -> new GameCommand.SelectHero(HeroClass.valueOf(text(p, "heroClass")));
      case "CONFIRM_HERO" -> new GameCommand.ConfirmHero();
      case "CANCEL_HERO_SELECTION" -> new GameCommand.CancelHeroSelection();
      case "START_STARTING_PLACEMENT" -> new GameCommand.StartStartingPlacement();
      case "PLACE_STARTING_OUTPOST" -> new GameCommand.PlaceStartingOutpost(coord(p, "at"));
      case "PLACE_STARTING_ROAD" -> new GameCommand.PlaceStartingRoad(coord(p, "to"));
      case "PROPOSE_HERO_SWAP" ->
          new GameCommand.ProposeHeroSwap(UUID.fromString(text(p, "targetPlayerId")));
      case "ACCEPT_HERO_SWAP" ->
          new GameCommand.AcceptHeroSwap(UUID.fromString(text(p, "proposalId")));
      case "SELECT_ACTION", "SELECT_ACTION_CARD" -> new GameCommand.SelectAction(ActionType.valueOf(text(p, "action")));
      case "BUILD_ROAD" -> new GameCommand.BuildRoad(coord(p, "from"), coord(p, "to"));
      case "BUILD_OUTPOST" -> new GameCommand.BuildOutpost(coord(p, "at"));
      case "RECRUIT" -> new GameCommand.Recruit(UnitType.valueOf(text(p, "unitType")));
      case "TRADE_RESOURCE" ->
          new GameCommand.Trade(
              ResourceType.valueOf(text(p, "give")), ResourceType.valueOf(text(p, "receive")));
      case "BANK_TRADE" ->
          new GameCommand.BankTrade(
              ResourceType.valueOf(text(p, "give")), ResourceType.valueOf(text(p, "receive")));
      case "MARKET_DEAL" ->
          new GameCommand.MarketDeal(
              ResourceType.valueOf(text(p, "give")), ResourceType.valueOf(text(p, "receive")));
      case "PROPOSE_TRADE" -> new GameCommand.ProposeTrade(
          UUID.fromString(text(p, "targetPlayerId")), resources(p, "offeredResources"),
          resources(p, "requestedResources"), p.path("offeredGold").asInt(0),
          p.path("requestedGold").asInt(0));
      case "ACCEPT_TRADE" -> new GameCommand.AcceptTrade(UUID.fromString(text(p, "proposalId")));
      case "REJECT_TRADE" -> new GameCommand.RejectTrade(UUID.fromString(text(p, "proposalId")));
      case "CANCEL_TRADE" -> new GameCommand.CancelTrade(UUID.fromString(text(p, "proposalId")));
      case "EXPLORE", "EXPLORE_HEX" -> new GameCommand.Explore(coord(p, "target"));
      case "DEEP_EXPLORE" -> new GameCommand.DeepExplore(coord(p, "target"));
      case "MOVE_HERO" -> new GameCommand.MoveHero(coord(p, "to"));
      case "SWIFT_MOVE" -> new GameCommand.SwiftMove(coord(p, "to"));
      case "ATTACK", "START_ATTACK" ->
          new GameCommand.Attack(
              coord(p, "target"),
              p.hasNonNull("reaction") ? ReactionType.valueOf(text(p, "reaction")) : null);
      case "FULL_ATTACK" ->
          new GameCommand.Attack(
              coord(p, "target"),
              p.hasNonNull("reaction") ? ReactionType.valueOf(text(p, "reaction")) : null);
      case "SMALL_RAID" -> new GameCommand.SmallRaid(coord(p, "target"));
      case "DEFENDER_REACTION", "CHOOSE_DEFENDER_REACTION" ->
          new GameCommand.DefenderReaction(ReactionType.valueOf(text(p, "reaction")));
      case "PRIEST_HEAL" -> new GameCommand.PriestHeal(coord(p, "target"));
      case "PRIEST_BLESS" -> new GameCommand.PriestBless(coord(p, "target"));
      case "PRIEST_SANCTUARY" -> new GameCommand.PriestSanctuary(coord(p, "target"));
      case "ARCANE_BOLT" -> new GameCommand.ArcaneBolt(coord(p, "target"));
      case "MAGE_WARD" -> new GameCommand.MageWard(coord(p, "target"));
      case "MAGE_REVEAL" -> new GameCommand.MageReveal(coord(p, "target"));
      case "TRANSMUTE" ->
          new GameCommand.Transmute(
              ResourceType.valueOf(text(p, "give")), ResourceType.valueOf(text(p, "receive")));
      case "SCOUT" -> new GameCommand.Scout(coord(p, "target"));
      case "QUICK_ROAD" -> new GameCommand.QuickRoad(coord(p, "from"), coord(p, "to"));
      case "REPAIR" -> new GameCommand.Repair(coord(p, "target"));
      case "LOCK_ATTACK_PLAN" ->
          new GameCommand.LockAttackPlan(
              coord(p, "source"),
              coord(p, "target"),
              json.convertValue(
                  p.path("participatingUnitIds"),
                  json.getTypeFactory().constructCollectionType(Set.class, UUID.class)),
              p.path("heroParticipates").asBoolean(false),
              p.hasNonNull("selectedTacticCardId")
                  ? Optional.of(UUID.fromString(text(p, "selectedTacticCardId")))
                  : Optional.empty());
      case "RESOLVE_ATTACK_BATCH" -> new GameCommand.ResolveAttackBatch();
      case "FORTIFY" -> new GameCommand.Fortify();
      case "RESOLVE_ACTION", "REVEAL_ACTION_CARDS", "END_PLAYER_TURN" -> new GameCommand.ResolveAction();
      case "END_ROUND" -> new GameCommand.EndRound();
      case "DEBUG" ->
          new GameCommand.Debug(
              text(p, "operation"), p.has("value") ? p.get("value").asInt() : null);
      default -> throw DomainException.of("UNKNOWN_COMMAND", "Unknown command type " + e.type());
    };
  }

  private HexCoordinate coord(JsonNode p, String key) {
    JsonNode n = p.get(key);
    if (n == null) throw DomainException.of("INVALID_PAYLOAD", "Missing " + key);
    return new HexCoordinate(n.get("q").asInt(), n.get("r").asInt());
  }

  private Resources resources(JsonNode p, String key) {
    JsonNode value = p.path(key);
    try {
      return new Resources(value.path("wood").asInt(0), value.path("food").asInt(0),
          value.path("ore").asInt(0), value.path("stone").asInt(0), value.path("gold").asInt(0));
    } catch (IllegalArgumentException ex) {
      throw DomainException.of("INVALID_PAYLOAD", key + " cannot contain negative values");
    }
  }

  private String text(JsonNode p, String key) {
    if (!p.hasNonNull(key)) throw DomainException.of("INVALID_PAYLOAD", "Missing " + key);
    return p.get(key).asText();
  }

  private GameEntity entity(UUID id) {
    return games
        .findById(id)
        .orElseThrow(() -> DomainException.of("GAME_NOT_FOUND", "Game not found"));
  }

  private GameState read(GameEntity e) {
    try {
      return json.readValue(e.getStateJson(), GameState.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private JsonNode readTree(String value) {
    try {
      return json.readTree(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
