package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.util.List;

// One portfolio response contains the account heading, product list and combined balances.
public record PortfolioDto(
    InvestorDto investor,
    BigDecimal totalBalance,
    BigDecimal availableToWithdraw,
    long noticeCount,
    List<ProductDto> products) {}
