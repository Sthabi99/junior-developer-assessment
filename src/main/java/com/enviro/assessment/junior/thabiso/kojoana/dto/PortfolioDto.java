package com.enviro.assessment.junior.thabiso.kojoana.dto;

import java.math.BigDecimal;
import java.util.List;

// Only these fields are sent to the browser, not the JPA entities.
// Groups the investor, products and totals into one response.
public record PortfolioDto(
    InvestorDto investor,
    BigDecimal totalBalance,
    BigDecimal availableToWithdraw,
    long noticeCount,
    List<ProductDto> products) {}
