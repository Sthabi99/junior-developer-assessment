package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Only these fields are sent to the browser, not the JPA entities.
public record WithdrawalDto(Long id, Long productId, String productName, BigDecimal amount, BigDecimal balanceBefore, BigDecimal remainingBalance, LocalDateTime createdAt) { }
