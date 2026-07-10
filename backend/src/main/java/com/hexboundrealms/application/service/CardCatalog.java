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
              switch (types[i % types.length]) {
                case TACTIC -> "Gain +2 on an eligible declared roll.";
                case REACTION -> "Reveal during defense for a tactical response.";
                case ALLY -> "Add one temporary support power.";
                case UPGRADE -> "Improve a settlement or army capability.";
                case QUEST -> "Complete its objective for a non-purchase Glory opportunity.";
              },
              "See card category"));
    return List.copyOf(cards);
  }
}
