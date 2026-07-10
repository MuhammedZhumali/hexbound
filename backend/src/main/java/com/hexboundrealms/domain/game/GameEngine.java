package com.hexboundrealms.domain.game;

import com.hexboundrealms.domain.game.GameModel.GameState;
import java.util.UUID;

public interface GameEngine {
  CommandResult execute(GameState state, UUID playerId, GameCommand command);
}
