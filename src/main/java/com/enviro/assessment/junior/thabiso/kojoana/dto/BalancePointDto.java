package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Only these fields are sent to the browser, not the JPA entities.
// Keeps the date and balance of a recorded product event.
public record BalancePointDto(LocalDateTime recordedAt, BigDecimal balance) {}
