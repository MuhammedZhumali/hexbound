package com.hexboundrealms.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_events")
public class GameEventEntity {
  @Id private UUID id;

  @Column(name = "game_id")
  private UUID gameId;

  @Column(name = "sequence_number")
  private long sequenceNumber;

  @Column(name = "command_id")
  private UUID commandId;

  @Column(name = "event_type")
  private String eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_json", columnDefinition = "jsonb")
  private String eventJson;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  protected GameEventEntity() {}

  public GameEventEntity(UUID gameId, long sequence, UUID commandId, String type, String json) {
    id = UUID.randomUUID();
    this.gameId = gameId;
    sequenceNumber = sequence;
    this.commandId = commandId;
    eventType = type;
    eventJson = json;
    createdAt = OffsetDateTime.now();
  }

  public long getSequenceNumber() {
    return sequenceNumber;
  }

  public String getEventType() {
    return eventType;
  }

  public String getEventJson() {
    return eventJson;
  }

  public UUID getCommandId() {
    return commandId;
  }
}
