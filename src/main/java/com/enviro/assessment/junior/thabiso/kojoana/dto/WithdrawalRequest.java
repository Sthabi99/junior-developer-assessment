package com.enviro.assessment.junior.thabiso.kojoana.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// Checks the product and amount sent by the withdrawal form.
public record WithdrawalRequest(
    @NotNull(message = "Choose an investment product.")
        @Positive(message = "Choose a valid product.")
        Long productId,
    @NotNull(message = "Enter an amount.")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
        @Digits(
            integer = 13,
            fraction = 2,
            message = "Use at most 13 whole digits and two decimal places.")
        BigDecimal amount) {}
