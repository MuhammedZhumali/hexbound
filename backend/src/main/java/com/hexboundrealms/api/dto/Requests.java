package com.hexboundrealms.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.hexboundrealms.domain.game.GameModel.*;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class Requests {
  private Requests() {}

  public record CreateGame(
      @NotBlank String name,
      Long seed,
      @Min(1) @Max(4) int maxPlayers,
      boolean debugMode,
      GameMode gameMode) {}

  public record JoinGame(@NotBlank String displayName, @NotNull PlayerColor playerColor) {}

  public record GuestLogin(@NotNull PlayerColor playerColor) {}

  public record CommandEnvelope(
      @NotNull UUID commandId,
      @NotNull UUID playerId,
      @PositiveOrZero long expectedVersion,
      @NotBlank String type,
      JsonNode payload) {}
}
