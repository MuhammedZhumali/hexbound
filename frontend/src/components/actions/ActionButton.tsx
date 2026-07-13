import type { ReactNode } from 'react';

export function ActionButton({
  icon,
  label,
  summary,
  cost,
  disabledReason,
  selected,
  busy,
  onClick,
}: {
  icon?: ReactNode;
  label: string;
  summary?: string;
  cost?: string;
  disabledReason?: string;
  selected?: boolean;
  busy?: boolean;
  onClick: () => void;
}) {
  const disabled = Boolean(disabledReason || busy);
  return (
    <button
      className={`context-action ${selected ? 'selected' : ''}`}
      disabled={disabled}
      onClick={onClick}
      aria-describedby={disabledReason ? `${slug(label)}-reason` : undefined}
    >
      {icon && <span className="context-action-icon">{icon}</span>}
      <span className="context-action-copy">
        <b>{label}</b>
        {summary && <small>{summary}</small>}
        {cost && <em>Cost: {cost}</em>}
        {disabledReason ? (
          <strong id={`${slug(label)}-reason`}>Disabled: {disabledReason}</strong>
        ) : (
          <i>Available</i>
        )}
      </span>
    </button>
  );
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-');
}
