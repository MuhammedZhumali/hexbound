import type { Game, PrivateView } from '../../types/game';
import { monsterArt } from '../../assets/gameAssets';

export function CombatPreview({
  game,
  view,
}: {
  game: Game;
  view: PrivateView;
}) {
  if (view.selectedAction !== 'ATTACK') return null;
  const readyUnits = view.units.filter((unit) => unit.fatigue !== 'EXHAUSTED');
  const heroBonus = heroAttackBonus(view.hero?.heroClass);
  const armyBonus = Math.min(readyUnits.length * 2, 6);
  return (
    <section className="combat-preview" aria-label="Attack preview">
      <p className="eyebrow">Combat Preview</p>
      <div className="combat-preview-grid">
        <div>
          <b>Attacker</b>
          <span>{readyUnits.length} ready units</span>
          <span>Hero bonus: {formatBonus(heroBonus)}</span>
          <span>Army bonus: up to {formatBonus(armyBonus)}</span>
        </div>
        <div>
          <b>Target Defense</b>
          <span>Base: 10</span>
          <span>Walls, garrison, terrain: checked by backend</span>
          <span>Hidden reactions: not revealed</span>
        </div>
        <div>
          <b>Formula</b>
          <span>d20 + army + hero + tactics - fatigue</span>
          <span>Compared against backend defense total</span>
        </div>
      </div>
      {(game.revealedAttackPlans ?? []).length === 0 && (
        <p className="muted">
          Choose source, target, units, and whether the hero participates before locking the plan.
        </p>
      )}
    </section>
  );
}

export function MonsterEventModal({
  game,
  onContinue,
  busy,
}: {
  game: Game;
  onContinue: () => void;
  busy: boolean;
}) {
  if (game.phase !== 'MONSTER_EVENT' || game.monsters.length === 0) return null;
  const monster = game.monsters[game.monsters.length - 1];
  const target = game.players.find((player) => player.id === monster.targetPlayerId);
  return (
    <div className="monster-callout" role="status" aria-label="Monster event">
      <img className="monster-callout-art" src={monsterArt(monster.type)} alt="" />
      <div>
        <p className="eyebrow">Monster Event</p>
        <h3>A Monster Appears</h3>
        <p>No production happens on a roll of 7.</p>
      </div>
      <dl>
        <dt>Target</dt>
        <dd>{target?.displayName ?? 'Unknown player'}</dd>
        <dt>Location</dt>
        <dd>
          {monster.location.q}, {monster.location.r}
        </dd>
        <dt>Effect</dt>
        <dd>Blocks nearby production</dd>
        <dt>Strength</dt>
        <dd>{monster.strength}</dd>
        <dt>Reward</dt>
        <dd>Glory and resources after defeat</dd>
      </dl>
      <button className="primary" disabled={busy} onClick={onContinue}>
        Continue
      </button>
    </div>
  );
}

function heroAttackBonus(heroClass?: string) {
  if (heroClass === 'KNIGHT') return 2;
  if (heroClass === 'MAGE') return 1;
  if (heroClass === 'RANGER') return 1;
  return 0;
}

function formatBonus(value: number) {
  return value >= 0 ? `+${value}` : String(value);
}
