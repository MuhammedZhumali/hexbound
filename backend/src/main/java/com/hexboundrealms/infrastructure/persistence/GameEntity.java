package com.hexboundrealms.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "games")
public class GameEntity {
  @Id private UUID id;
  private long seed;
  private String status;
  private String phase;

  @Column(name = "round_number")
  private int roundNumber;

  @Version private long version;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "state_json", columnDefinition = "jsonb")
  private String stateJson;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  protected GameEntity() {}

  public GameEntity(UUID id, long seed, String status, String phase, int round, String json) {
    this.id = id;
    this.seed = seed;
    this.status = status;
    this.phase = phase;
    roundNumber = round;
    stateJson = json;
    createdAt = updatedAt = OffsetDateTime.now();
  }

  public UUID getId() {
    return id;
  }

  public long getVersion() {
    return version;
  }

  public String getStateJson() {
    return stateJson;
  }

  public void update(String status, String phase, int round, String json) {
    this.status = status;
    this.phase = phase;
    roundNumber = round;
    stateJson = json;
    updatedAt = OffsetDateTime.now();
  }
}
