package com.hexboundrealms.domain.production;

import com.hexboundrealms.domain.game.GameModel.*;
import com.hexboundrealms.domain.map.*;
import java.util.*;

public final class ProductionResolver {
  public record Production(
      UUID playerId, UUID settlementId, ResourceType resource, int amount, int productionNumber) {}

  public record ProductionReport(int roll, List<Production> production) {}

  public ProductionReport resolve(GameState game, int roll) {
    List<Production> report = new ArrayList<>();
    if (roll == 7) return new ProductionReport(roll, report);
    Map<HexCoordinate, MapHex> map = new HashMap<>();
    game.map.forEach(h -> map.put(h.coordinate(), h));
    for (PlayerState p : game.players) {
      for (SettlementState s : p.settlements) {
        MapHex h = map.get(s.location());
        if (h != null
            && h.productionNumber() != null
            && h.productionNumber() == roll
            && h.resource() != null
            && game.monsters.stream().noneMatch(m -> m.location().distanceTo(s.location()) <= 1)) {
          int amount = s.level() == SettlementLevel.CITY ? 2 : 1;
          p.resources = p.resources.add(h.resource(), amount);
          report.add(new Production(p.id, s.id(), h.resource(), amount, h.productionNumber()));
        }
      }
    }
    return new ProductionReport(roll, List.copyOf(report));
  }
}
