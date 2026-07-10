package com.hexboundrealms.infrastructure.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventJpaRepository extends JpaRepository<GameEventEntity, UUID> {
  Optional<GameEventEntity> findByGameIdAndCommandId(UUID gameId, UUID commandId);

  List<GameEventEntity> findByGameIdOrderBySequenceNumber(UUID gameId);

  long countByGameId(UUID gameId);
}
