package com.hexboundrealms.domain.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProductionResolverTest {
  private final ProductionResolver resolver = new ProductionResolver();

  @Test
  void productionOnlyTriggersOnExactRolledNumber() {
    GameState game = new GameState();
    PlayerState player =
        new PlayerState(UUID.randomUUID(), "Blue", PlayerColor.BLUE, "token", HeroClass.KNIGHT);
    HexCoordinate at = new HexCoordinate(0, 0);
    player.settlements.add(new SettlementState(UUID.randomUUID(), at, SettlementLevel.OUTPOST, 2));
    game.players.add(player);
    game.map.add(new MapHex(at, TerrainType.MOUNTAIN, ResourceType.ORE, 6, null, null, null));

    assertThat(resolver.resolve(game, 5).production()).isEmpty();
    assertThat(player.resources.ore()).isZero();
    assertThat(resolver.resolve(game, 6).production()).hasSize(1);
    assertThat(player.resources.ore()).isEqualTo(1);
  }
}
