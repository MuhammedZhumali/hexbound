CREATE TABLE games (
  id UUID PRIMARY KEY,
  seed BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  phase VARCHAR(32) NOT NULL,
  round_number INTEGER NOT NULL,
  version BIGINT NOT NULL,
  state_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE game_events (
  id UUID PRIMARY KEY,
  game_id UUID NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  sequence_number BIGINT NOT NULL,
  command_id UUID NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_game_command UNIQUE(game_id, command_id),
  CONSTRAINT uq_game_sequence UNIQUE(game_id, sequence_number)
);
CREATE INDEX ix_game_events_game_id ON game_events(game_id, sequence_number);

