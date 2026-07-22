export type Coord = { q: number; r: number };
export type Resources = { wood: number; food: number; ore: number; stone: number; gold: number };
export type Hex = {
  coordinate: Coord;
  terrain: string;
  resource?: string;
  productionNumber?: number;
  ownerId?: string;
  villageLoyalty?: number;
  monster?: string;
};
export type Settlement = { id: string; location: Coord; level: string; durability: number };
export type Road = { id: string; from: Coord; to: Coord };
export type Unit = {
  id: string;
  type: string;
  fatigue: string;
  wounded: boolean;
  garrison: boolean;
  contractUntilRound: number;
};
export type Player = {
  id: string;
  displayName: string;
  color: string;
  heroClass?: string;
  glory: number;
  activeSealCount?: number;
  reputation: number;
  hasSelectedAction: boolean;
  hasLockedAttackPlan: boolean;
  revealedAction?: string;
  settlements: Settlement[];
  roads: Road[];
  unitCount: number;
  hero?: {
    heroClass: string;
    hp: number;
    mana: number;
    grace: number;
    location?: Coord;
    defeated: boolean;
  };
};
export type Card = {
  id: string;
  name: string;
  category: string;
  cost: Resources;
  effect: string;
  timing: string;
};
export type Monster = {
  id: string;
  type: string;
  location: Coord;
  strength: number;
  hp: number;
  targetPlayerId: string;
  tier: string;
};
export type Game = {
  id: string;
  name: string;
  seed: number;
  debugMode: boolean;
  gameMode: 'STANDARD' | 'BEGINNER';
  status: string;
  phase: string;
  roundNumber: number;
  version: number;
  lastRoll?: number;
  firstPlayerId?: string;
  currentTurnPlayerId?: string;
  pendingConflict?: PendingConflict;
  startingPlacementStep?: 'OUTPOST' | 'ROAD' | 'COMPLETE';
  currentStartingPlacementPlayerId?: string;
  map: Hex[];
  players: Player[];
  monsters: Monster[];
  market: Card[];
  tradeProposals?: TradeProposal[];
  explorationResults?: ExplorationResult[];
  eventLog: string[];
  winners: string[];
  revealedAttackPlans: AttackPlanView[];
  combatReport: CombatReportEntry[];
};
export type PendingConflict = {
  conflictId: string;
  attackerPlayerId: string;
  defenderPlayerId: string;
  target: Coord;
  attackType: 'SMALL_RAID' | 'FULL_ATTACK';
};
export type TradeProposal = {
  proposalId: string;
  proposerPlayerId: string;
  targetPlayerId: string;
  offeredResources: Resources;
  requestedResources: Resources;
  offeredGold: number;
  requestedGold: number;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED';
};
export type ExplorationResult = {
  playerId: string;
  target: Coord;
  type: string;
  description: string;
};
export type CombatReportEntry = {
  attackerId?: string;
  defenderId?: string;
  monsterId?: string;
  source: Coord;
  target: Coord;
  conflictType: string;
  roll: number;
  defenseRoll?: number;
  attackTotal: number;
  defenseTotal: number;
  damage: number;
  unitDamage: number;
  settlementDamage: number;
  monsterDamage: number;
};
export type AttackPlan = {
  playerId: string;
  source: Coord;
  target: Coord;
  participatingUnitIds: string[];
  heroParticipates: boolean;
  selectedTacticCardId?: string;
};
export type AttackPlanView = {
  playerId: string;
  source: Coord;
  target: Coord;
  participatingUnitCount: number;
  heroParticipates: boolean;
};
export type HeroDraft = {
  phase: string;
  initialTurnOrder: string[];
  draftOrder: string[];
  currentDraftPlayerId?: string;
  availableHeroClasses: string[];
  confirmedSelections: Record<string, string>;
  allowDuplicateHeroClasses: boolean;
};
export type PrivateView = {
  game: Game;
  playerId: string;
  accessToken: string;
  resources: Resources;
  hero?: Player['hero'];
  glory: Record<string, number>;
  reputation: number;
  selectedAction?: string;
  previousAction?: string;
  fortificationTokens: number;
  fortifyTokenStockpile: number;
  temporaryFortifyTokens: number;
  freeFortifyAssignmentsRemaining: number;
  assignedFortifyTokens: Record<string, number>;
  temporaryAssignedFortifyTokens: Record<string, number>;
  basicActionPoints: number;
  settlements: Settlement[];
  roads: Road[];
  units: Unit[];
  hand: Card[];
  activeSeals: string[];
  privateExplorationResults: ExplorationResult[];
  temporaryHeroClass?: string;
  attackPlan?: AttackPlan;
};
export type Seat = { playerId: string; accessToken: string; displayName: string };

export type TargetType =
  | 'NONE'
  | 'HEX'
  | 'FRIENDLY_HEX'
  | 'ENEMY_HEX'
  | 'MONSTER_HEX'
  | 'UNIT'
  | 'HERO'
  | 'PLAYER'
  | 'CARD';

export type LegalAction = {
  actionType: string;
  label: string;
  description: string;
  apCost: number;
  resourceCost: Partial<Record<keyof Resources | 'mana' | 'grace', number>>;
  available: boolean;
  disabledReason?: string;
  requiresTarget: boolean;
  targetType: TargetType;
  validTargetHexIds: string[];
  validTargetUnitIds: string[];
  validTargetPlayerIds: string[];
};

export type LegalActionsResponse = {
  actions: string[];
  movementTargets: string[];
  buildTargets: string[];
  attackTargets: string[];
  availableActions: LegalAction[];
};
