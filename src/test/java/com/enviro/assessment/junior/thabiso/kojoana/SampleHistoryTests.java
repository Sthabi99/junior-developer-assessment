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

    @Test void sampleHistoryCoversEligibleProductsWithVariedAmountsAndNoDuplicates() {
        for (var investor : investors.findAll()) {
            var notices = withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investor.getId());
            boolean thabo = investor.getName().equals("Thabo Dlamini");
            assertEquals(thabo ? 3 : 2, notices.size());
            assertEquals(notices.size(), notices.stream().map(n -> n.getAmount()).distinct().count());
            assertEquals(thabo ? 2 : 1, notices.stream().map(n -> n.getProduct().getType()).distinct().count());
            for (int index = 0; index < notices.size(); index++) {
                var notice = notices.get(index);
                assertEquals(LocalDate.now().minusMonths(index + 1), notice.getCreatedAt().toLocalDate());
                assertEquals(0, notice.getBalanceBefore().subtract(notice.getAmount()).compareTo(notice.getRemainingBalance()));
                if (!thabo) assertEquals(com.enviro.assessment.junior.thabiso.kojoana.model.ProductType.SAVINGS, notice.getProduct().getType());
                var later = notices.stream().filter(n -> n.getProduct().getId().equals(notice.getProduct().getId()) && n.getCreatedAt().isAfter(notice.getCreatedAt())).findFirst();
                if (later.isPresent()) assertEquals(0, notice.getRemainingBalance().compareTo(later.get().getBalanceBefore()));
                else assertEquals(0, notice.getRemainingBalance().compareTo(notice.getProduct().getBalance()));
            }
        }
        seedData.run();
        assertEquals(7, withdrawals.count());
    }
}
