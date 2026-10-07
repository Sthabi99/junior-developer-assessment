package com.enviro.assessment.junior.thabiso.kojoana.service;

import com.enviro.assessment.junior.thabiso.kojoana.exception.ValidationException;
import com.enviro.assessment.junior.thabiso.kojoana.model.ProductType;
import java.math.*;
import java.time.*;
import org.springframework.stereotype.Component;

@Component
// These rules are shared by portfolio eligibility checks and withdrawal validation.
public class WithdrawalRules {
  // Age uses completed years, so someone whose birthday is still coming up is not counted a year older.
  public int age(LocalDate birthDate, LocalDate today) {
    return Period.between(birthDate, today).getYears();
  }

  // Savings is available at any age; retirement needs an age greater than 65.
  public boolean allowed(ProductType type, LocalDate birthDate, LocalDate today) {
    return type != ProductType.RETIREMENT || age(birthDate, today) > 65;
  }

  public BigDecimal maximum(BigDecimal balance) {
    // Rounding down keeps the maximum within 90%, including balances that leave a fraction of a cent.
    return balance.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.DOWN);
  }

  public void validate(
      ProductType type,
      LocalDate birthDate,
      BigDecimal balance,
      BigDecimal amount,
      LocalDate today) {
    if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2)
      throw new ValidationException(
          "amount", "Enter a positive amount with at most two decimal places.");
    if (!allowed(type, birthDate, today))
      throw new ValidationException(
          "productId",
          "Retirement withdrawals are only allowed when the investor is older than 65.");
    if (amount.compareTo(balance) > 0)
      throw new ValidationException("amount", "The amount cannot exceed the product balance.");
    if (amount.compareTo(maximum(balance)) > 0)
      throw new ValidationException(
          "amount",
          "The amount cannot exceed 90% of the product balance. Maximum: R " + maximum(balance));
  }
}
