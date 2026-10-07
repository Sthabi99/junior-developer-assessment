package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Each entry pairs an opening or withdrawal date with the balance recorded at that time.
public record BalancePointDto(LocalDateTime recordedAt, BigDecimal balance) {}
