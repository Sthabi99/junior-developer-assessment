package com.enviro.assessment.junior.thabiso.kojoana.exception;

// Used when the requested investor, product or notice cannot be found.
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }
}
