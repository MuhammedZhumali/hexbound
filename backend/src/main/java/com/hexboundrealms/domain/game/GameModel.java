package com.hexboundrealms.domain.game;

import com.hexboundrealms.domain.map.*;
import java.util.*;

public final class GameModel {
  private GameModel() {}

  public enum GameStatus {
    LOBBY,
    ACTIVE,
    FINISHED
  }

  public enum GameMode {
    STANDARD,
    BEGINNER
  }

  public enum GamePhase {
    SETUP,
    HERO_SELECTION,
    HERO_REVEAL,
    HERO_DRAFT,
    STARTING_PLACEMENT,
    WORLD_ROLL,
    PRODUCTION,
    WORLD,
    MONSTER_EVENT,
    NEGOTIATION,
    TRADE_NEGOTIATION,
    ACTION_CARD_SELECTION,
    ACTION_CARD_REVEAL,
    PLAYER_TURNS,
    WAITING_FOR_DEFENDER_REACTION,
    MARKET,
    PLANNING,
    REVEAL,
    RESOLUTION,
    END_ROUND,
    FINAL_ROUND,
    GAME_OVER
  }

  public enum HeroClass {
    KNIGHT,
    MERCHANT,
    PRIEST,
    RANGER,
    MAGE,
    ENGINEER
  }

  public enum PlayerColor {
    BLUE,
    RED,
    GREEN,
    GOLD
  }

  public enum ActionType {
    EXPLORE,
    TRADE,
    BUILD,
    RECRUIT,
    FORTIFY,
    ATTACK
  }

  public record TurnActionPoints(UUID playerId, int remainingActionPoints) {}

  public enum StartingPlacementStep {
    OUTPOST,
    ROAD,
    COMPLETE
  }

  public enum TradeStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED,
    EXPIRED
  }

  public enum ExplorationResultType {
    BONUS_RESOURCE,
    HIDDEN_ROUTE,
    LOCAL_QUEST,
    MONSTER_CLUE,
    AMBUSH,
    ARTIFACT_CLUE,
    VILLAGE_SECRET,
    TRADE_CONTACT,
    TEMPORARY_BUFF,
    NOTHING_FOUND
  }

  public enum ExplorationState {
    UNEXPLORED,
    SCOUTED,
    DEPLETED
  }

  public enum SettlementLevel {
    OUTPOST,
    TOWN,
    CITY
  }

  public enum UnitType {
    MILITIA,
    INFANTRY,
    ARCHER,
    CAVALRY,
    MERCENARY
  }

  public enum FatigueState {
    READY,
    FATIGUED,
    EXHAUSTED
  }

  public enum ReactionType {
    NONE,
    SHIELD,
    COUNTERATTACK,
    EVACUATION,
    AMBUSH
  }

  public enum ConflictAttackType {
    SMALL_RAID,
    FULL_ATTACK
  }

  public enum PathSeal {
    RULER,
    HERO,
    PROSPERITY,
    INFLUENCE,
    KNOWLEDGE
  }

  public enum CardType {
    TACTIC,
    REACTION,
    ALLY,
    UPGRADE,
    QUEST
  }

  public record Resources(int wood, int food, int ore, int stone, int gold) {
    public Resources {
      if (wood < 0 || food < 0 || ore < 0 || stone < 0 || gold < 0)
        throw new IllegalArgumentException("Resources cannot be negative");
    }

    public static Resources none() {
      return new Resources(0, 0, 0, 0, 0);
    }

    public static Resources starting() {
      return new Resources(1, 1, 0, 1, 1);
    }

    public Resources add(ResourceType type, int n) {
      return switch (type) {
        case WOOD -> new Resources(wood + n, food, ore, stone, gold);
        case FOOD -> new Resources(wood, food + n, ore, stone, gold);
        case ORE -> new Resources(wood, food, ore + n, stone, gold);
        case STONE -> new Resources(wood, food, ore, stone + n, gold);
        case GOLD -> new Resources(wood, food, ore, stone, gold + n);
      };
    }

    public boolean covers(Resources c) {
      return wood >= c.wood && food >= c.food && ore >= c.ore && stone >= c.stone && gold >= c.gold;
    }

    public Resources subtract(Resources c) {
      if (!covers(c)) throw new IllegalArgumentException("INSUFFICIENT_RESOURCES");
      return new Resources(
          wood - c.wood, food - c.food, ore - c.ore, stone - c.stone, gold - c.gold);
    }

    public Resources plus(Resources other) {
      return new Resources(
          wood + other.wood, food + other.food, ore + other.ore, stone + other.stone,
          gold + other.gold);
    }
  }

  public record TradeProposal(
      UUID proposalId,
      UUID proposerPlayerId,
      UUID targetPlayerId,
      Resources offeredResources,
      Resources requestedResources,
      int offeredGold,
      int requestedGold,
      TradeStatus status) {
    public TradeProposal {
      Objects.requireNonNull(proposalId);
      Objects.requireNonNull(proposerPlayerId);
      Objects.requireNonNull(targetPlayerId);
      Objects.requireNonNull(offeredResources);
      Objects.requireNonNull(requestedResources);
      Objects.requireNonNull(status);
      if (proposerPlayerId.equals(targetPlayerId)) throw new IllegalArgumentException("Self trade");
      if (offeredGold < 0 || requestedGold < 0) throw new IllegalArgumentException("Negative gold");
    }

    public TradeProposal withStatus(TradeStatus next) {
      return new TradeProposal(
          proposalId, proposerPlayerId, targetPlayerId, offeredResources, requestedResources,
          offeredGold, requestedGold, next);
    }
  }

  public record TributeClaim(
      UUID creditorPlayerId, UUID debtorPlayerId, int amount, int expiresAtRound) {}

  public record ExplorationResult(
      UUID playerId, HexCoordinate target, ExplorationResultType type, String description) {}

  public record GloryState(
      int construction, int monsters, int quests, int diplomacy, int battles, int artifacts) {
    public static GloryState empty() {
      return new GloryState(0, 0, 0, 0, 0, 0);
    }

    public int total() {
      return construction + monsters + quests + diplomacy + battles + artifacts;
    }
  }

  public record HeroState(
      HeroClass heroClass, int hp, int mana, int grace, HexCoordinate location, boolean defeated) {
    public static HeroState create(HeroClass h, HexCoordinate c) {
      return new HeroState(
          h, 3, h == HeroClass.MAGE ? 3 : 0, h == HeroClass.PRIEST ? 4 : 0, c, false);
    }
  }

  public record UnitState(
      UUID id,
      UnitType type,
      FatigueState fatigue,
      boolean wounded,
      boolean garrison,
      int contractUntilRound) {}

  public record SettlementState(
      UUID id, HexCoordinate location, SettlementLevel level, int durability) {}

  public record RoadState(UUID id, HexCoordinate from, HexCoordinate to) {}

  public record Card(
      UUID id, String name, CardType category, Resources cost, String effect, String timing) {}

  public record CombatReportEntry(
      UUID attackerId,
      UUID defenderId,
      UUID monsterId,
      HexCoordinate source,
      HexCoordinate target,
      String conflictType,
      int roll,
      Integer defenseRoll,
      int attackTotal,
      int defenseTotal,
      int damage,
      int unitDamage,
      int settlementDamage,
      int monsterDamage) {}

  public record MonsterState(
      UUID id,
      String type,
      HexCoordinate location,
      int strength,
      int hp,
      UUID targetPlayerId,
      String tier) {}

  public record SealProgress(
      boolean heroicQuest,
      int distinctMonsterTypes,
      int tradesWithDifferentPlayers,
      int tradeRoutes,
      int peacefulAlliances,
      int ruinsExplored,
      int artifacts,
      Set<PathSeal> permanent) {
    public static SealProgress empty() {
      return new SealProgress(false, 0, 0, 0, 0, 0, 0, new HashSet<>());
    }
  }

  public record HeroDraftSettings(boolean allowDuplicateHeroClasses) {
    public static HeroDraftSettings defaults() {
      return new HeroDraftSettings(true);
    }
  }

  public record GameOrder(List<UUID> initialTurnOrder, List<UUID> heroDraftOrder) {
    public static GameOrder empty() {
      return new GameOrder(List.of(), List.of());
    }
  }

  public record HeroSwapProposal(
      UUID proposalId,
      UUID requestingPlayerId,
      UUID targetPlayerId,
      HeroClass requestedHeroClass,
      HeroClass offeredHeroClass,
      boolean requesterAccepted,
      boolean targetAccepted) {}

  public record PendingConflict(
      UUID conflictId,
      UUID attackerPlayerId,
      UUID defenderPlayerId,
      HexCoordinate target,
      ConflictAttackType attackType,
      ActionType attackerActionCard,
      ActionType defenderActionCard,
      int apCost) {}

  public static final class PlayerState {
    public UUID id;
    public String displayName;
    public PlayerColor color;
    public String accessToken;
    public HeroState hero;
    public HeroClass temporaryHeroClass;
    public boolean heroConfirmed;
    public Resources resources = Resources.starting();
    public GloryState glory = GloryState.empty();
    public int reputation;
    public List<SettlementState> settlements = new ArrayList<>();
    public List<RoadState> roads = new ArrayList<>();
    public List<UnitState> units = new ArrayList<>();
    public List<Card> hand = new ArrayList<>();
    public Set<HexCoordinate> exploredRuins = new HashSet<>();
    public Set<HexCoordinate> exploredHexes = new HashSet<>();
    public Map<String, ExplorationState> explorationStates = new HashMap<>();
    public SealProgress sealProgress = SealProgress.empty();
    public ActionType selectedAction;
    public ActionType previousAction;
    public boolean actionLocked;
    public int fortificationTokens;
    public int fortifyTokenStockpile;
    public int temporaryFortifyTokens;
    public int freeFortifyAssignmentsRemaining;
    public boolean freeFortifyBuyUsed;
    public Map<String, Integer> assignedFortifyTokens = new HashMap<>();
    public Map<String, Integer> temporaryAssignedFortifyTokens = new HashMap<>();
    public int basicActionPoints = 3;
    public boolean mainActionCompletedThisRound;
    public boolean freeExploreUsed;
    public boolean freeTradeCardBuyUsed;
    public boolean freeRoadUsed;
    public boolean freeMilitiaUsed;
    public boolean attackDiscountUsed;
    public boolean swiftMoveUsed;
    public boolean quickRoadUsed;
    public boolean repairUsed;
    public int blessTokens;
    public int sanctuaryTokens;
    public int wardTokens;
    public com.hexboundrealms.domain.combat.AttackPlan attackPlan;

    public PlayerState() {}

    public PlayerState(UUID id, String name, PlayerColor color, String token) {
      this.id = id;
      displayName = name;
      this.color = color;
      accessToken = token;
    }

    public PlayerState(UUID id, String name, PlayerColor color, String token, HeroClass heroClass) {
      this(id, name, color, token);
      hero = HeroState.create(heroClass, null);
      heroConfirmed = true;
    }
  }

  public static final class GameState {
    public UUID id;
    public String name;
    public long seed;
    public int maxPlayers;
    public boolean debugMode;
    public GameMode gameMode = GameMode.STANDARD;
    public GameStatus status = GameStatus.LOBBY;
    public GamePhase phase = GamePhase.SETUP;
    public int roundNumber = 0;
    public long version = 0;
    public int firstPlayerIndex = 0;
    public HeroDraftSettings heroDraftSettings = HeroDraftSettings.defaults();
    public GameOrder order = GameOrder.empty();
    public int currentHeroDraftIndex = 0;
    public StartingPlacementStep startingPlacementStep = StartingPlacementStep.OUTPOST;
    public int currentStartingPlacementIndex = 0;
    public int currentTurnIndex = 0;
    public List<UUID> actionTurnOrder = new ArrayList<>();
    public int actionTurnOrderIndex = 0;
    public boolean hybridTurnMode;
    public List<HeroSwapProposal> heroSwapProposals = new ArrayList<>();
    public List<TradeProposal> tradeProposals = new ArrayList<>();
    public List<TributeClaim> tributeClaims = new ArrayList<>();
    public List<ExplorationResult> explorationResults = new ArrayList<>();
    public PendingConflict pendingConflict;
    public Integer lastRoll;
    public Integer forced2d6;
    public Integer forcedD20;
    public List<MapHex> map = new ArrayList<>();
    public List<PlayerState> players = new ArrayList<>();
    public List<MonsterState> monsters = new ArrayList<>();
    public List<Card> market = new ArrayList<>();
    public List<Card> deck = new ArrayList<>();
    public List<CombatReportEntry> lastCombatReport = new ArrayList<>();
    public List<String> eventLog = new ArrayList<>();
    public long rngCounter = 0;
    public List<UUID> winners = new ArrayList<>();

    public GameState() {}
  }
}
