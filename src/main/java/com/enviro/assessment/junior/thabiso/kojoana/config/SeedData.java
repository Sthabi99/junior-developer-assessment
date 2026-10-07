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
        for (Investor investor : investors.findAll()) {
            var history = withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investor.getId());
            boolean isThabo = investor.getName().equals("Thabo Dlamini");
            boolean isNaledi = investor.getName().equals("Naledi Mokoena");
            boolean isSipho = investor.getName().equals("Sipho Nkosi");
            if (!isThabo && !isNaledi && !isSipho) continue;
            int targetCount = isThabo ? 3 : 2;
            int remaining = targetCount - history.size();
            if (remaining <= 0) continue;

            // Only seed products without history, so saved withdrawals stay untouched.
            var investorProducts = products.findByInvestorIdOrderById(investor.getId());
            for (InvestmentProduct product : investorProducts) {
                boolean hasHistory = history.stream()
                    .anyMatch(notice -> notice.getProduct().getId().equals(product.getId()));
                if (hasHistory || remaining <= 0) continue;
                if (product.getType() == ProductType.RETIREMENT) {
                    int age = Period.between(investor.getDateOfBirth(), LocalDate.now()).getYears();
                    if (age <= 65) continue;
                    addProductHistory(product, new String[]{"1800.00"}, 3);
                    remaining--;
                } else {
                    String[] amounts = isThabo ? new String[]{"450.00", "900.00"}
                        : isNaledi ? new String[]{"350.00", "1250.00"}
                        : new String[]{"800.00", "2100.00"};
                    int count = Math.min(remaining, amounts.length);
                    addProductHistory(product, java.util.Arrays.copyOf(amounts, count), count);
                    remaining -= count;
                }
            }
        }
    }

    private void addProductHistory(InvestmentProduct product, String[] amounts, int firstMonthAgo) {
        LocalDateTime today = LocalDate.now().atTime(10, 0);
        BigDecimal balance = product.getBalance();
        for (String amount : amounts) balance = balance.add(new BigDecimal(amount));
        product.setOpeningDetails(balance, today.minusMonths(firstMonthAgo + 1));
        for (int index = 0; index < amounts.length; index++) {
            BigDecimal amount = new BigDecimal(amounts[index]);
            BigDecimal remaining = balance.subtract(amount);
            withdrawals.save(new WithdrawalNotice(product, amount, balance, remaining,
                today.minusMonths(firstMonthAgo - index)));
            balance = remaining;
        }
        // Earlier balances account for the withdrawals; the current balance stays the same.
        products.save(product);
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

