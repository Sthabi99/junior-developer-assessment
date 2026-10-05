package com.enviro.assessment.junior.thabiso.kojoana.config;
import com.enviro.assessment.junior.thabiso.kojoana.model.*;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;

@Component
public class SeedData implements CommandLineRunner {
    private final InvestorRepository investors;
    private final ProductRepository products;
    private final WithdrawalRepository withdrawals;
    @Value("${app.sample-history.enabled:true}")
    private boolean sampleHistoryEnabled;
    public SeedData(InvestorRepository investors, ProductRepository products, WithdrawalRepository withdrawals) {
        this.investors = investors;
        this.products = products;
        this.withdrawals = withdrawals;
    }
    @Override @Transactional
    public void run(String... args) {
        if (investors.count() == 0) {
            add("Thabo Dlamini", 67, "100000.00", "20000.00");
            add("Naledi Mokoena", 65, "80000.00", "15000.00");
            add("Sipho Nkosi", 40, "50000.00", "30000.00");
        }
        if (sampleHistoryEnabled) addSampleHistory();
    }

    private void addSampleHistory() {
        Investor thabo = investors.findAll().stream()
            .filter(investor -> investor.getName().equals("Thabo Dlamini"))
            .findFirst().orElse(null);
        if (thabo == null) return;
        // Leave existing history alone, including withdrawals entered through the UI.
        if (!withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId()).isEmpty()) return;
        InvestmentProduct savings = products.findByInvestorIdOrderById(thabo.getId()).stream()
            .filter(product -> product.getType() == ProductType.SAVINGS)
            .findFirst().orElseThrow();

        // Do not duplicate sample history or overwrite existing savings withdrawals.
        boolean hasSavingsHistory = withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(thabo.getId()).stream()
            .anyMatch(notice -> notice.getProduct().getId().equals(savings.getId()));
        if (hasSavingsHistory) return;

        LocalDateTime today = LocalDate.now().atTime(10, 0);
        BigDecimal balance = savings.getBalance().add(new BigDecimal("1800.00"));
        savings.setOpeningDetails(balance, today.minusMonths(4));
        String[] amounts = {"500.00", "600.00", "700.00"};
        for (int index = 0; index < amounts.length; index++) {
            BigDecimal amount = new BigDecimal(amounts[index]);
            BigDecimal remaining = balance.subtract(amount);
            withdrawals.save(new WithdrawalNotice(savings, amount, balance, remaining,
                today.minusMonths(3 - index)));
            balance = remaining;
        }
        // The last historical balance matches the balance already shown in the portfolio.
        products.save(savings);
    }

    private void add(String name, int age, String retirement, String savings) {
        Investor investor = investors.save(new Investor(name, LocalDate.now().minusYears(age)));
        addProduct(investor, "Retirement investment", ProductType.RETIREMENT, retirement);
        addProduct(investor, "Savings investment", ProductType.SAVINGS, savings);
    }
    private void addProduct(Investor investor, String name, ProductType type, String amount) {
        BigDecimal balance = new BigDecimal(amount);
        products.save(new InvestmentProduct(investor, name, type, balance, balance, LocalDateTime.now()));
    }
}

