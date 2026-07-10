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
    List<String> buildTargets =
        g.map.stream()
            .filter(h -> building.outpostLegal(g, p, h.coordinate()))
            .map(h -> h.coordinate().q() + "," + h.coordinate().r())
            .toList();
    List<String> actions =
        g.phase == GamePhase.PLANNING
            ? Arrays.stream(ActionType.values())
                .filter(a -> a != p.previousAction)
                .map(Enum::name)
                .toList()
            : List.of();
    return new LegalActions(actions, List.of(), buildTargets, List.of());
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
      case "ROLL_WORLD" ->
          new GameCommand.RollWorld(p.has("forced") ? p.get("forced").asInt() : null);
      case "BUY_MARKET_CARD" -> new GameCommand.BuyMarketCard(UUID.fromString(text(p, "cardId")));
      case "SELECT_HERO" -> new GameCommand.SelectHero(HeroClass.valueOf(text(p, "heroClass")));
      case "CONFIRM_HERO" -> new GameCommand.ConfirmHero();
      case "CANCEL_HERO_SELECTION" -> new GameCommand.CancelHeroSelection();
      case "START_STARTING_PLACEMENT" -> new GameCommand.StartStartingPlacement();
      case "PROPOSE_HERO_SWAP" ->
          new GameCommand.ProposeHeroSwap(UUID.fromString(text(p, "targetPlayerId")));
      case "ACCEPT_HERO_SWAP" ->
          new GameCommand.AcceptHeroSwap(UUID.fromString(text(p, "proposalId")));
      case "SELECT_ACTION" -> new GameCommand.SelectAction(ActionType.valueOf(text(p, "action")));
      case "BUILD_ROAD" -> new GameCommand.BuildRoad(coord(p, "from"), coord(p, "to"));
      case "BUILD_OUTPOST" -> new GameCommand.BuildOutpost(coord(p, "at"));
      case "RECRUIT" -> new GameCommand.Recruit(UnitType.valueOf(text(p, "unitType")));
      case "TRADE_RESOURCE" ->
          new GameCommand.Trade(
              ResourceType.valueOf(text(p, "give")), ResourceType.valueOf(text(p, "receive")));
      case "EXPLORE" -> new GameCommand.Explore(coord(p, "target"));
      case "MOVE_HERO" -> new GameCommand.MoveHero(coord(p, "to"));
      case "ATTACK" ->
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
      case "RESOLVE_ACTION" -> new GameCommand.ResolveAction();
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
