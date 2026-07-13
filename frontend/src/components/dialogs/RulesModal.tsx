import { useEffect } from 'react';

const sections = [
  ['Goal of the Game', 'Reach 10 Glory. The top bar keeps the closest progress visible.'],
  ['Round Structure', 'Roll production, resolve a monster on 7, negotiate, choose secret action cards, reveal, take 3 AP player turns, then end the round.'],
  ['Resources', 'Wood and Stone build roads. Food recruits. Ore supports infantry and fortify. Gold buys cards and flexible help.'],
  ['Starting Placement', 'Place Outposts manually in turn order, then Roads in reverse order. Valid hexes are highlighted.'],
  ['Trading', 'Player trades exchange resources and gold during negotiation. Bank trade is available during AP turns.'],
  ['Building', 'Build Roads to expand your network. Outposts and upgrades are confirmed by backend rules.'],
  ['Exploration', 'Explore can target many terrain types. The backend chooses the final result after you commit.'],
  ['Heroes', 'Hero class gives abilities, but player color and name identify the owner when classes repeat.'],
  ['Armies and Combat', 'Combat rolls d20 plus bonuses against defense. Fatigue, garrison, walls, and reactions matter.'],
  ['Monsters', 'A roll of 7 skips production and can spawn a monster that blocks nearby production.'],
  ['Villages and Reputation', 'Villages can create quests, alliances, or risks. Reputation affects some hero paths.'],
  ['Action Cards', 'Each round, choose one secret card. It sets initiative and gives a bonus; every player still gets 3 AP.'],
  ['Glory', 'Glory is the score track. First to 10 Glory wins after end-round cleanup.'],
  ['Common Beginner Tips', 'Secure mixed resources, trade before using the bank, and do not attack while exhausted.'],
];

const glossary = [
  'Outpost',
  'Town',
  'City',
  'Road',
  'Garrison',
  'Reserve',
  'Fatigue',
  'Exhausted',
  'Grace',
  'Mana',
  'Glory',
  'Action Card',
  'Loyalty',
  'Monster Event',
  'Fortification Token',
];

export function RulesModal({ onClose }: { onClose: () => void }) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [onClose]);

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section
        className="rules-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="rules-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header>
          <div>
            <p className="eyebrow">Rulebook</p>
            <h2 id="rules-title">Hexbound Realms Rulebook</h2>
          </div>
          <button className="icon-button" onClick={onClose} aria-label="Close rulebook">
            x
          </button>
        </header>

        <div className="rules-grid deep">
          {sections.map(([title, text], index) => (
            <article key={title}>
              <b>
                {index + 1}. {title}
              </b>
              <p>{text}</p>
            </article>
          ))}
        </div>

        <section className="glossary-block">
          <p className="eyebrow">Glossary</p>
          <div className="glossary-chips">
            {glossary.map((term) => (
              <span key={term}>{term}</span>
            ))}
          </div>
        </section>
      </section>
    </div>
  );
}
