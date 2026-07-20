package com.hexboundrealms.application.service;

import com.hexboundrealms.domain.game.GameModel.*;
import java.util.*;

public final class CardCatalog {
  public List<Card> all() {
    String[] names = {
      "Flanking Maneuver",
      "Hidden Warehouse",
      "Experienced Captain",
      "Trade Charter",
      "Sacred Relic",
      "Siege Ladders",
      "Last Charge",
      "Emergency Supplies",
      "Scout Network",
      "Reinforced Gates",
      "Forced March",
      "Field Medic",
      "Monster Hunter",
      "Neutral Envoy",
      "Supply Caravan",
      "Arcane Focus",
      "Blessed Armor",
      "Paid Informant",
      "Portable Barricade",
      "Ancient Map"
    };
    CardType[] types = CardType.values();
    List<Card> cards = new ArrayList<>();
    for (int i = 0; i < names.length; i++)
      cards.add(
          new Card(
              UUID.nameUUIDFromBytes(names[i].getBytes()),
              names[i],
              types[i % types.length],
              new Resources(0, 0, 0, 0, 1 + (i % 2)),
              effect(names[i], types[i % types.length]),
              "See card category"));
    return List.copyOf(cards);
  }

  private String effect(String name, CardType type) {
    if (name.equals("Ancient Map")) {
      return "Quest: complete your next Explore or Deep Explore for +1 Quest Glory, +1 Gold, and +1 reputation.";
    }
    if (name.equals("Blessed Armor")) {
      return "Reaction: automatically reveal against a monster attack for +4 defense, then discard.";
    }
    return switch (type) {
      case TACTIC -> "Gain +2 on an eligible declared roll.";
      case REACTION -> "Reveal during defense for a tactical response.";
      case ALLY -> "Add one temporary support power.";
      case UPGRADE -> "Improve a settlement or army capability.";
      case QUEST -> "Quest: complete your next Explore or Deep Explore for +1 Quest Glory, +1 Gold, and +1 reputation.";
    };
  }
}
