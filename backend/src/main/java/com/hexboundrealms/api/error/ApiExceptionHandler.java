package com.hexboundrealms.api.error;

import com.hexboundrealms.domain.game.DomainException;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  public ProblemDetail domain(DomainException ex) {
    HttpStatus status =
        switch (ex.code()) {
          case "GAME_NOT_FOUND" -> HttpStatus.NOT_FOUND;
          case "VERSION_CONFLICT", "DUPLICATE_COMMAND" -> HttpStatus.CONFLICT;
          case "INVALID_ACCESS_TOKEN" -> HttpStatus.FORBIDDEN;
          default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    ProblemDetail p = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    p.setTitle(ex.code());
    p.setType(URI.create("https://hexboundrealms.dev/problems/" + ex.code().toLowerCase()));
    p.setProperty("code", ex.code());
    return p;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail validation(MethodArgumentNotValidException ex) {
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    p.setTitle("INVALID_REQUEST");
    p.setProperty("code", "INVALID_REQUEST");
    return p;
  }
}
