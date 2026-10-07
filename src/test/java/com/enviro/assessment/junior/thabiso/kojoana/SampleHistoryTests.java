package com.enviro.assessment.junior.thabiso.kojoana;

import static org.junit.jupiter.api.Assertions.*;

import com.enviro.assessment.junior.thabiso.kojoana.config.SeedData;
import com.enviro.assessment.junior.thabiso.kojoana.model.Investor;
import com.enviro.assessment.junior.thabiso.kojoana.model.ProductType;
import com.enviro.assessment.junior.thabiso.kojoana.model.WithdrawalNotice;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "app.sample-history.enabled=true",
      "spring.datasource.url=jdbc:h2:mem:sample-history-tests;DB_CLOSE_DELAY=-1"
    })
@Transactional
class SampleHistoryTests {
  @Autowired InvestorRepository investors;
  @Autowired WithdrawalRepository withdrawals;
  @Autowired SeedData seedData;

  @Test
  void sampleHistoryCoversEligibleProductsWithVariedAmountsAndNoDuplicates() {
    for (Investor investor : investors.findAll()) {
      List<WithdrawalNotice> notices =
          withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investor.getId());
      boolean thabo = investor.getName().equals("Thabo Dlamini");
      int expectedNotices = 2;
      int expectedProducts = 1;
      if (thabo) {
        expectedNotices = 3;
        expectedProducts = 2;
      }
      Set<BigDecimal> amounts = new HashSet<>();
      Set<ProductType> productTypes = new HashSet<>();
      for (WithdrawalNotice notice : notices) {
        amounts.add(notice.getAmount());
        productTypes.add(notice.getProduct().getType());
      }
      assertEquals(expectedNotices, notices.size());
      assertEquals(notices.size(), amounts.size());
      assertEquals(expectedProducts, productTypes.size());
      for (int index = 0; index < notices.size(); index++) {
        WithdrawalNotice notice = notices.get(index);
        assertEquals(LocalDate.now().minusMonths(index + 1), notice.getCreatedAt().toLocalDate());
        assertEquals(
            0,
            notice
                .getBalanceBefore()
                .subtract(notice.getAmount())
                .compareTo(notice.getRemainingBalance()));
        if (!thabo)
          assertEquals(
              com.enviro.assessment.junior.thabiso.kojoana.model.ProductType.SAVINGS,
              notice.getProduct().getType());
        WithdrawalNotice later = null;
        for (WithdrawalNotice candidate : notices) {
          if (candidate.getProduct().getId().equals(notice.getProduct().getId())
              && candidate.getCreatedAt().isAfter(notice.getCreatedAt())) {
            later = candidate;
            break;
          }
        }
        if (later != null)
          assertEquals(0, notice.getRemainingBalance().compareTo(later.getBalanceBefore()));
        else
          assertEquals(0, notice.getRemainingBalance().compareTo(notice.getProduct().getBalance()));
      }
    }
    seedData.run();
    assertEquals(7, withdrawals.count());
  }
}
