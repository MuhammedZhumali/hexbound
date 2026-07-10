package com.hexboundrealms.domain.game;

import com.hexboundrealms.domain.game.GameModel.GameState;
import java.util.List;

public record CommandResult(GameState state, List<DomainEvent> events) {}
