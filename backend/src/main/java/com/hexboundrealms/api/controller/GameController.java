package com.hexboundrealms.api.controller;

import com.hexboundrealms.api.dto.Requests.*;
import com.hexboundrealms.api.dto.Views.*;
import com.hexboundrealms.application.service.GameApplicationService;
import com.hexboundrealms.domain.game.GameModel.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class GameController {
  private final GameApplicationService games;

  public GameController(GameApplicationService games) {
    this.games = games;
  }

  @PostMapping("/games")
  @ResponseStatus(HttpStatus.CREATED)
  public PublicGameView create(@Valid @RequestBody CreateGame request) {
    return games.create(request);
  }

  @GetMapping("/games/{id}")
  public PublicGameView get(@PathVariable UUID id) {
    return games.get(id);
  }

  @PostMapping("/games/{id}/players")
  @ResponseStatus(HttpStatus.CREATED)
  public JoinResult join(@PathVariable UUID id, @Valid @RequestBody JoinGame request) {
    return games.join(id, request);
  }

  @GetMapping("/games/{id}/players/{playerId}")
  public PrivatePlayerView privateView(
      @PathVariable UUID id,
      @PathVariable UUID playerId,
      @RequestHeader("X-Player-Token") String token) {
    return games.privateView(id, playerId, token);
  }

  @PostMapping("/games/{id}/start")
  public PublicGameView start(@PathVariable UUID id) {
    return games.start(id);
  }

  @PostMapping("/games/{id}/commands")
  public PublicGameView command(
      @PathVariable UUID id, @Valid @RequestBody CommandEnvelope envelope) {
    return games.command(id, envelope);
  }

  @GetMapping("/games/{id}/events")
  public List<Map<String, Object>> events(@PathVariable UUID id) {
    return games.eventLog(id);
  }

  @GetMapping("/games/{id}/legal-actions")
  public LegalActions legal(@PathVariable UUID id, @RequestParam UUID playerId) {
    return games.legal(id, playerId);
  }

  @GetMapping("/games/{id}/hero-draft")
  public HeroDraftView heroDraft(@PathVariable UUID id) {
    return games.heroDraft(id);
  }

  @GetMapping("/reference/heroes")
  public List<Map<String, Object>> heroes() {
    return List.of(
        hero(
            HeroClass.KNIGHT,
            "Knight",
            "Direct military commander",
            null,
            null,
            List.of("+2 Attack Total", "+1 passive defense", "Reroll attack results 1–4"),
            List.of("Large armies require additional Food")),
        hero(
            HeroClass.MERCHANT,
            "Merchant",
            "Economy and market specialist",
            null,
            null,
            List.of(
                "One adjacent-number settlement activation",
                "Hand limit 6",
                "Reserve a market card"),
            List.of("-1 personal combat checks")),
        hero(
            HeroClass.PRIEST,
            "Priest",
            "Support Hero using Grace and Reputation",
            "GRACE",
            4,
            List.of("Heal", "Blessing", "Sanctuary", "Resurrection", "Divine Intervention"),
            List.of("Grace recovery depends on support play")),
        hero(
            HeroClass.RANGER,
            "Ranger",
            "Exploration and mobile warfare",
            null,
            null,
            List.of("Movement range 2", "+2 Ruin exploration", "+2 against wilderness monsters"),
            List.of("Army bonus capped at +4")),
        hero(
            HeroClass.MAGE,
            "Mage",
            "Arcane combat and control",
            "MANA",
            3,
            List.of("Arcane Bolt", "Ward", "Teleport", "Transmutation", "Counterspell"),
            List.of("No combat bonus without Mana")),
        hero(
            HeroClass.ENGINEER,
            "Engineer",
            "Construction and fortification",
            null,
            null,
            List.of(
                "Discount first road/building", "Extra Fortification Token", "Durable City Walls"),
            List.of("No Hero attack bonus")));
  }

  private Map<String, Object> hero(
      HeroClass heroClass,
      String displayName,
      String description,
      String resourceType,
      Integer maximumResource,
      List<String> abilities,
      List<String> restrictions) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("heroClass", heroClass);
    result.put("displayName", displayName);
    result.put("description", description);
    result.put("resourceType", resourceType);
    result.put("maximumResource", maximumResource);
    result.put("abilities", abilities);
    result.put("restrictions", restrictions);
    return result;
  }

  @GetMapping("/reference/cards")
  public List<Card> cards() {
    return games.cards();
  }

  @GetMapping("/reference/units")
  public List<Map<String, Object>> units() {
    return List.of(
        Map.of("id", "MILITIA", "attack", 1, "defense", 1, "cost", "1 Food"),
        Map.of("id", "INFANTRY", "attack", 2, "defense", 3, "cost", "1 Food + 1 Ore"),
        Map.of("id", "ARCHER", "attack", 2, "defense", 2, "cost", "1 Food + 1 Wood"),
        Map.of("id", "CAVALRY", "attack", 3, "defense", 1, "cost", "2 Food + 1 Gold"),
        Map.of("id", "MERCENARY", "attack", 2, "defense", 2, "cost", "2 Gold"));
  }

  @GetMapping("/reference/monsters")
  public List<Map<String, Object>> monsters() {
    return List.of(
        Map.of("name", "Bog Wyrm", "tier", "MINOR", "reward", "2 resources + Trophy"),
        Map.of("name", "Ash Drake", "tier", "STRONG", "reward", "3 resources + Trophy + card"),
        Map.of(
            "name", "Void Colossus", "tier", "ELITE", "reward", "3 resources + Artifact + Glory"));
  }

  private String title(String s) {
    return s.charAt(0) + s.substring(1).toLowerCase();
  }
}
