package com.enviro.assessment.junior.thabiso.kojoana;
import com.enviro.assessment.junior.thabiso.kojoana.service.WithdrawalRules;
import com.enviro.assessment.junior.thabiso.kojoana.model.ProductType;
import com.enviro.assessment.junior.thabiso.kojoana.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawalRulesTest {
    private final WithdrawalRules rules = new WithdrawalRules();
    private final LocalDate today = LocalDate.of(2026, 10, 8);
    @ParameterizedTest @CsvSource({"66,true", "65,false", "40,false"})
    void retirementRequiresAnAgeAbove65(int age, boolean allowed) {
        assertEquals(allowed, rules.allowed(ProductType.RETIREMENT, today.minusYears(age), today));
    }
    @Test void theDayBeforeThe66thBirthdayIsStillIneligible() {
        assertFalse(rules.allowed(ProductType.RETIREMENT, today.minusYears(66).plusDays(1), today));
    }
    @Test void savingsDoesNotHaveTheRetirementAgeRule() {
        assertTrue(rules.allowed(ProductType.SAVINGS, today.minusYears(40), today));
    }
    @Test void exactly90PercentIsAllowed() {
        assertDoesNotThrow(() -> validate("90000.00"));
    }
    @Test void oneCentAbove90PercentIsRejected() {
        assertThrows(ValidationException.class, () -> validate("90000.01"));
    }
    @Test void anAmountAboveTheBalanceIsRejected() {
        assertThrows(ValidationException.class, () -> validate("100000.01"));
    }
    @ParameterizedTest @CsvSource({"0", "-1", "0.001"})
    void invalidAmountsAreRejected(String amount) {
        assertThrows(ValidationException.class, () -> validate(amount));
    }
    @Test void theMaximumRoundsDownToCents() {
        assertEquals(new BigDecimal("0.00"), rules.maximum(new BigDecimal("0.01")));
        assertEquals(new BigDecimal("90.00"), rules.maximum(new BigDecimal("100.01")));
    }
    private void validate(String amount) {
        rules.validate(ProductType.RETIREMENT, today.minusYears(67), new BigDecimal("100000.00"), new BigDecimal(amount), today);
    }
}
