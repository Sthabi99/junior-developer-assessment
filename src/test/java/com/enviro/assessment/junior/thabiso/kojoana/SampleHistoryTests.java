package com.enviro.assessment.junior.thabiso.kojoana;

import com.enviro.assessment.junior.thabiso.kojoana.config.SeedData;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "app.sample-history.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:sample-history-tests;DB_CLOSE_DELAY=-1"
})
@Transactional
class SampleHistoryTests {
    @Autowired InvestorRepository investors;
    @Autowired WithdrawalRepository withdrawals;
    @Autowired SeedData seedData;

    @Test void monthlySampleWithdrawalsHaveMatchingBalancesAndAreNotDuplicated() {
        var thabo = investors.findAll().stream()
            .filter(investor -> investor.getName().equals("Thabo Dlamini")).findFirst().orElseThrow();
        var notices = withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId());
        assertEquals(3, notices.size());
        for (int index = 0; index < 3; index++) {
            var notice = notices.get(index);
            assertEquals(LocalDate.now().minusMonths(index + 1), notice.getCreatedAt().toLocalDate());
            assertEquals(0, notice.getBalanceBefore().subtract(notice.getAmount()).compareTo(notice.getRemainingBalance()));
            if (index < 2) assertEquals(0, notice.getBalanceBefore().compareTo(notices.get(index + 1).getRemainingBalance()));
        }
        assertEquals(0, notices.get(0).getRemainingBalance().compareTo(new BigDecimal("20000.00")));
        assertEquals(0, notices.get(0).getProduct().getBalance().compareTo(new BigDecimal("20000.00")));
        seedData.run();
        assertEquals(3, withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId()).size());
    }
}
