package com.hexboundrealms.domain.game;

public final class DomainException extends RuntimeException {
  private final String code;

  public DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static DomainException of(String code, String message) {
    return new DomainException(code, message);
  }
}
