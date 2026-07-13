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
    return new LegalActions(actions, movementTargets, buildTargets, List.of());
  }

  private Optional<UUID> currentTurnPlayerId(GameState g) {
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
      case "PROPOSE_TRADE" -> new GameCommand.ProposeTrade(
          UUID.fromString(text(p, "targetPlayerId")), resources(p, "offeredResources"),
          resources(p, "requestedResources"), p.path("offeredGold").asInt(0),
          p.path("requestedGold").asInt(0));
      case "ACCEPT_TRADE" -> new GameCommand.AcceptTrade(UUID.fromString(text(p, "proposalId")));
      case "REJECT_TRADE" -> new GameCommand.RejectTrade(UUID.fromString(text(p, "proposalId")));
      case "CANCEL_TRADE" -> new GameCommand.CancelTrade(UUID.fromString(text(p, "proposalId")));
      case "EXPLORE", "EXPLORE_HEX" -> new GameCommand.Explore(coord(p, "target"));
      case "MOVE_HERO" -> new GameCommand.MoveHero(coord(p, "to"));
      case "ATTACK", "START_ATTACK" ->
          new GameCommand.Attack(
              coord(p, "target"),
              p.hasNonNull("reaction") ? ReactionType.valueOf(text(p, "reaction")) : null);
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
