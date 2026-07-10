package com.hexboundrealms.domain.map;

import java.util.UUID;

public record MapHex(
    HexCoordinate coordinate,
    TerrainType terrain,
    ResourceType resource,
    Integer productionNumber,
    UUID ownerId,
    Integer villageLoyalty,
    String monster) {
  public MapHex withOwner(UUID owner, int loyalty) {
    return new MapHex(coordinate, terrain, resource, productionNumber, owner, loyalty, monster);
  }

  public MapHex withMonster(String value) {
    return new MapHex(
        coordinate, terrain, resource, productionNumber, ownerId, villageLoyalty, value);
  }
}
