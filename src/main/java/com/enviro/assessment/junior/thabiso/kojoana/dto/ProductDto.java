package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.util.List;

// The form uses the eligibility flag and maximum withdrawal to check the selected product.
public record ProductDto(
    Long id,
    String name,
    String type,
    BigDecimal balance,
    BigDecimal maximumWithdrawal,
    boolean withdrawalAllowed,
    String eligibilityMessage,
    List<BalancePointDto> history) {}
