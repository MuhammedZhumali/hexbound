import type { Game, PrivateView } from '../../types/game';

export type GuidanceLevel = 'BEGINNER' | 'NORMAL' | 'EXPERT';

type ChecklistItem = {
  label: string;
  state: 'done' | 'current' | 'todo';
};

const phaseNames: Record<string, string> = {
  SETUP: 'Setup',
  HERO_SELECTION: 'Hero Selection',
  HERO_REVEAL: 'Hero Reveal',
  HERO_DRAFT: 'Hero Selection',
  STARTING_PLACEMENT: 'Starting Placement',
  WORLD_ROLL: 'World Roll',
  WORLD: 'World Roll',
  PRODUCTION: 'Production',
  NEGOTIATION: 'Negotiation',
  TRADE_NEGOTIATION: 'Negotiation',
  MARKET: 'Market',
  ACTION_CARD_SELECTION: 'Action Card Selection',
  ACTION_CARD_REVEAL: 'Action Card Reveal',
  PLAYER_TURNS: 'Player Turns',
  PLANNING: 'Action Card Selection',
  REVEAL: 'Action Card Reveal',
  RESOLUTION: 'Combat and Actions',
  MONSTER_EVENT: 'Monster Event',
  END_ROUND: 'End Round',
  FINAL_ROUND: 'Final Round',
  GAME_OVER: 'Game Over',
};

const phaseHelp: Record<string, { objective: string; beginner: string[]; normal: string[] }> = {
  HERO_SELECTION: {
    objective: 'Pick a hero class. Heroes reveal together after everyone confirms.',
    beginner: [
      'Heroes nudge your early tactics, but the round loop stays the same for everyone.',
      'Duplicate classes are allowed, so player color and name identify the owner.',
    ],
    normal: ['Choose and confirm a hero.'],
  },
  HERO_REVEAL: {
    objective: 'All heroes are public. Start manual placement next.',
    beginner: ['Use hero strengths to decide your first settlement and road direction.'],
    normal: ['Continue to starting placement.'],
  },
  HERO_DRAFT: {
    objective: 'Pick a hero class. The choice becomes public after confirmation.',
    beginner: [
      'Heroes define your strongest early tactic, but they do not lock you into one strategy.',
      'Duplicate classes are allowed, so player color and name matter more than class alone.',
    ],
    normal: ['Choose and confirm a hero when the draft reaches your player.'],
  },
  STARTING_PLACEMENT: {
    objective: 'Place your starting Outpost, then choose the first Road manually.',
    beginner: [
      'Look for useful production numbers such as 6 and 8.',
      'Try to touch two or three resource types early.',
      'Locked hexes are invalid; click one to see a plain-language reason.',
    ],
    normal: ['Choose a highlighted hex. The backend confirms the final placement.'],
  },
  WORLD_ROLL: {
    objective: 'The first player rolls 2d6 for production.',
    beginner: [
      'Settlements on matching production numbers gain resources.',
      'A roll of 7 cancels production and starts a Monster Event.',
    ],
    normal: ['Roll production or wait for the first player to roll.'],
  },
  WORLD: {
    objective: 'The first player rolls 2d6 for production.',
    beginner: [
      'Settlements on matching production numbers gain resources.',
      'A roll of 7 cancels production and starts a Monster Event.',
    ],
    normal: ['Roll production or wait for the first player to roll.'],
  },
  PRODUCTION: {
    objective: 'Production has resolved. Move into negotiation.',
    beginner: ['Check what you gained, then think about what resource you still need.'],
    normal: ['Continue to negotiation.'],
  },
  NEGOTIATION: {
    objective: 'Negotiate player-to-player trades before action turns.',
    beginner: [
      'Player trades can exchange resources and gold in either direction.',
      'Bank trade is available during your AP turn, but player trades are often better.',
    ],
    normal: ['Create, accept, reject, or cancel pending offers.'],
  },
  TRADE_NEGOTIATION: {
    objective: 'Negotiate player-to-player trades before action turns.',
    beginner: [
      'Player trades can exchange resources and gold in either direction.',
      'Bank trade is available during your AP turn, but player trades are often better.',
    ],
    normal: ['Create, accept, reject, or cancel pending offers.'],
  },
  PLAYER_TURNS: {
    objective: 'Spend your Action Points. Your revealed card gives a bonus, not a separate turn.',
    beginner: [
      'You can move, explore, build, recruit, trade with the bank, or attack if the card allows it.',
      'If you are unsure, build roads, trade for missing resources, or explore nearby terrain.',
    ],
    normal: ['Spend AP, then end your player turn.'],
  },
  ACTION_CARD_SELECTION: {
    objective: 'Choose one secret action card for initiative and a turn bonus.',
    beginner: [
      'Cards reveal together. Initiative order is Explore, Trade, Build, Recruit, Fortify, Attack.',
      'Every player still gets 3 AP when their turn comes.',
    ],
    normal: ['Choose one available action card and confirm it.'],
  },
  PLANNING: {
    objective: 'Choose one secret action card for initiative and a turn bonus.',
    beginner: [
      'Cards reveal together. Initiative order is Explore, Trade, Build, Recruit, Fortify, Attack.',
      'Every player still gets 3 AP when their turn comes.',
    ],
    normal: ['Choose one available action card and confirm it.'],
  },
  ACTION_CARD_REVEAL: {
    objective: 'Action cards are public. Start player turns in initiative order.',
    beginner: ['The card is a bonus for your 3 AP turn, not a separate complex subsystem.'],
    normal: ['Start player turns.'],
  },
  RESOLUTION: {
    objective: 'Resolve the selected action with backend validation.',
    beginner: [
      'Combat uses d20: roll plus bonuses against the target defense total.',
      'Hidden reactions are not revealed before the rules allow them.',
    ],
    normal: ['Follow the active action panel.'],
  },
  MONSTER_EVENT: {
    objective: 'A roll of 7 spawned a monster and skipped production.',
    beginner: [
      'The monster blocks nearby production and can become a target for later attacks.',
      'The target player may need help if assistance rules are active.',
    ],
    normal: ['Review the monster, then continue to the next phase.'],
  },
  MARKET: {
    objective: 'Buy useful cards if you can afford them.',
    beginner: ['Cards can create tactical swings. Do not spend all gold if you need units soon.'],
    normal: ['Buy a market card or continue.'],
  },
  END_ROUND: {
    objective: 'Refresh the round and pass the first player marker.',
    beginner: ['This is the cleanup step. The next round begins after confirmation.'],
    normal: ['End the round when everyone is ready.'],
  },
};

export function phaseLabel(phase: string) {
  return phaseNames[phase] ?? phase.replaceAll('_', ' ');
}

export function PhaseGuide({
  game,
  guidanceLevel,
  invalidSelectionReason,
}: {
  game: Game;
  guidanceLevel: GuidanceLevel;
  invalidSelectionReason?: string;
}) {
  const guide = phaseHelp[game.phase] ?? {
    objective: 'Follow the current backend phase.',
    beginner: ['The interface only enables actions that match the server state.'],
    normal: ['Use the contextual action bar below the board.'],
  };
  const details =
    guidanceLevel === 'EXPERT'
      ? []
      : guidanceLevel === 'NORMAL'
        ? guide.normal
        : guide.beginner;
  const objective =
    game.phase === 'PLAYER_TURNS'
      ? `Spend up to ${game.gameMode === 'BEGINNER' ? 2 : 3} Action Points. Your revealed card gives a bonus, not a separate turn.`
      : guide.objective;

  return (
    <section className="guide-card" aria-label="Phase guide">
      <p className="eyebrow">Phase Guide</p>
      <h3>{phaseLabel(game.phase)}</h3>
      <p>{objective}</p>
      {details.length > 0 && (
        <ul>
          {details.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
      {invalidSelectionReason && guidanceLevel !== 'EXPERT' && (
        <div className="inline-warning" role="status">
          {invalidSelectionReason}
        </div>
      )}
    </section>
  );
}

export function ActionChecklist({ game, view }: { game: Game; view?: PrivateView }) {
  const items = checklistFor(game, view);
  return (
    <section className="guide-card compact-guide" aria-label="Action checklist">
      <p className="eyebrow">Current Task</p>
      <ol className="action-checklist">
        {items.map((item) => (
          <li className={item.state} key={item.label}>
            <span>{item.state === 'done' ? 'OK' : item.state === 'current' ? 'Now' : '--'}</span>
            {item.label}
          </li>
        ))}
      </ol>
    </section>
  );
}

export function BeginnerRecommendation({
  game,
  guidanceLevel,
}: {
  game: Game;
  guidanceLevel: GuidanceLevel;
}) {
  if (guidanceLevel !== 'BEGINNER') return null;
  const text =
    {
      STARTING_PLACEMENT:
        'Recommended: choose a hex near at least two resource types. Numbers 6 and 8 produce most often.',
      NEGOTIATION:
        'Recommended: try a player trade before relying on the bank. Bank trade costs AP during your turn.',
      TRADE_NEGOTIATION:
        'Recommended: try a player trade before relying on the bank. Bank trade costs AP during your turn.',
      ACTION_CARD_SELECTION:
        'Recommended: pick Build or Trade if your economy is thin; Explore is great when your hero is well positioned.',
      ACTION_CARD_REVEAL:
        'Recommended: note who acts before you, then spend your 3 AP on concrete board progress.',
      PLAYER_TURNS:
        'Recommended: spend your 3 AP on board progress: move, build, recruit, trade, explore, or attack.',
      PLANNING:
        'Recommended: if your economy is thin, Build or Trade usually helps more than an early attack.',
      RESOLUTION:
        'Before attacking, compare army strength, hero bonuses, fatigue, walls, and garrison.',
      MONSTER_EVENT:
        'Recommended: note what production is blocked and whether the monster is worth attacking later.',
    }[game.phase] ?? 'Recommended: check the current task, then use the contextual action bar.';

  return (
    <section className="recommendation-card" aria-label="Beginner recommendation">
      <p className="eyebrow">Recommended</p>
      <p>{text}</p>
    </section>
  );
}

function checklistFor(game: Game, view?: PrivateView): ChecklistItem[] {
  if (game.phase === 'HERO_SELECTION' || game.phase === 'HERO_DRAFT') {
    return [
      { label: 'Join the table', state: 'done' },
      { label: 'Choose hero', state: view?.hero ? 'done' : 'current' },
      { label: 'Confirm hero', state: view?.hero ? 'done' : 'todo' },
      { label: 'Start placement', state: 'todo' },
    ];
  }
  if (game.phase === 'STARTING_PLACEMENT') {
    return [
      { label: 'Choose hero', state: 'done' },
      {
        label: 'Place starting Outpost',
        state: game.startingPlacementStep === 'OUTPOST' ? 'current' : 'done',
      },
      {
        label: 'Place starting Road',
        state:
          game.startingPlacementStep === 'ROAD'
            ? 'current'
            : game.startingPlacementStep === 'OUTPOST'
              ? 'todo'
              : 'done',
      },
      { label: 'Start Round 1', state: game.startingPlacementStep === 'COMPLETE' ? 'current' : 'todo' },
    ];
  }
  if (game.phase === 'NEGOTIATION' || game.phase === 'TRADE_NEGOTIATION') {
    return [
      { label: 'Review pending offers', state: 'current' },
      { label: 'Propose or answer trades', state: 'current' },
      { label: 'Close negotiations', state: 'todo' },
    ];
  }
  if (game.phase === 'PLAYER_TURNS') {
    const maxAp = game.gameMode === 'BEGINNER' ? 2 : 3;
    return [
      { label: 'Optional: trade with players', state: 'done' },
      {
        label: `Spend up to ${maxAp} AP`,
        state: view?.basicActionPoints === 0 ? 'done' : 'current',
      },
      { label: 'Use your card bonus if useful', state: view?.selectedAction ? 'current' : 'todo' },
      { label: 'End player turn', state: 'todo' },
    ];
  }
  if (game.phase === 'ACTION_CARD_SELECTION' || game.phase === 'PLANNING') {
    return [
      { label: 'Review your resources and board', state: 'done' },
      { label: 'Choose one secret action card', state: view?.selectedAction ? 'done' : 'current' },
      { label: 'Wait for simultaneous reveal', state: view?.selectedAction ? 'current' : 'todo' },
    ];
  }
  if (game.phase === 'ACTION_CARD_REVEAL' || game.phase === 'REVEAL') {
    return [
      { label: 'Reveal all action cards', state: 'done' },
      { label: 'Check initiative order', state: 'current' },
      { label: 'Start 3 AP player turns', state: 'todo' },
    ];
  }
  if (game.phase === 'MONSTER_EVENT') {
    return [
      { label: 'Monster spawned', state: 'done' },
      { label: 'Review target and blocked area', state: 'current' },
      { label: 'Continue to market', state: 'todo' },
    ];
  }
  return [
    { label: 'Read current phase', state: 'done' },
    { label: 'Check available actions', state: 'current' },
    { label: 'Confirm with backend', state: 'todo' },
  ];
}
