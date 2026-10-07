package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// The history table and CSV use these notice amounts, balances and timestamps.
public record WithdrawalDto(
    Long id,
    Long productId,
    String productName,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal remainingBalance,
    LocalDateTime createdAt) {}
