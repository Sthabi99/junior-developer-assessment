package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.util.List;

// Only these fields are sent to the browser, not the JPA entities.
public record ProductDto(Long id, String name, String type, BigDecimal balance, BigDecimal maximumWithdrawal, boolean withdrawalAllowed, String eligibilityMessage, List<BalancePointDto> history) { }
