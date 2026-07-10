package com.hexboundrealms.domain.game;

import java.util.Map;

public record DomainEvent(String type, Map<String, Object> publicPayload) {}
