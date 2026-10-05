package com.enviro.assessment.junior.thabiso.kojoana.service;
import com.enviro.assessment.junior.thabiso.kojoana.dto.*;
import com.enviro.assessment.junior.thabiso.kojoana.model.*;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import com.enviro.assessment.junior.thabiso.kojoana.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
public class PortfolioService {
    private final InvestorRepository investors;
    private final ProductRepository products;
    private final WithdrawalRepository withdrawals;
    private final WithdrawalRules rules;
    public PortfolioService(InvestorRepository investors, ProductRepository products, WithdrawalRepository withdrawals, WithdrawalRules rules) {
        this.investors = investors; this.products = products; this.withdrawals = withdrawals; this.rules = rules;
    }
    private Investor investor(Long id) {
        return investors.findById(id).orElseThrow(() -> new NotFoundException("Investor not found."));
    }
    private InvestorDto investorDto(Investor investor) {
        return new InvestorDto(investor.getId(), investor.getName(), investor.getDateOfBirth(), rules.age(investor.getDateOfBirth(), LocalDate.now()));
    }
    private WithdrawalDto noticeDto(WithdrawalNotice notice) {
        return new WithdrawalDto(notice.getId(), notice.getProduct().getId(), notice.getProduct().getName(), notice.getAmount(), notice.getBalanceBefore(), notice.getRemainingBalance(), notice.getCreatedAt());
    }
    @Transactional(readOnly = true)
    public List<InvestorDto> listInvestors() { return investors.findAll().stream().map(this::investorDto).toList(); }
    @Transactional(readOnly = true)
    public PortfolioDto portfolio(Long id) {
        Investor investor = investor(id);
        List<WithdrawalNotice> notices = withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(id);
        List<ProductDto> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, available = BigDecimal.ZERO;
        for (InvestmentProduct product : products.findByInvestorIdOrderById(id)) {
            boolean allowed = rules.allowed(product.getType(), investor.getDateOfBirth(), LocalDate.now());
            BigDecimal maximum = allowed ? rules.maximum(product.getBalance()) : BigDecimal.ZERO.setScale(2);
            total = total.add(product.getBalance()); available = available.add(maximum);
            List<BalancePointDto> history = new ArrayList<>();
            history.add(new BalancePointDto(product.getOpenedAt(), product.getOpeningBalance()));
            // Use recorded events only. Do not invent historical investment growth.
            notices.stream().filter(n -> n.getProduct().getId().equals(product.getId()))
                .sorted(Comparator.comparing(WithdrawalNotice::getCreatedAt).thenComparing(WithdrawalNotice::getId))
                .forEach(n -> history.add(new BalancePointDto(n.getCreatedAt(), n.getRemainingBalance())));
            result.add(new ProductDto(product.getId(), product.getName(), product.getType().name(), product.getBalance(), maximum, allowed,
                allowed ? "Up to 90% of the current balance." : "Retirement withdrawals require an age above 65.", history));
        }
        return new PortfolioDto(investorDto(investor), total, available, notices.size(), result);
    }
    @Transactional(readOnly = true)
    public WithdrawalDto withdrawal(Long investorId, Long noticeId) {
        investor(investorId);
        WithdrawalNotice notice = withdrawals.findById(noticeId).orElseThrow(() -> new NotFoundException("Withdrawal notice not found."));
        if (!notice.getProduct().getInvestor().getId().equals(investorId)) throw new NotFoundException("Withdrawal notice does not belong to this investor.");
        return noticeDto(notice);
    }
    @Transactional
    public WithdrawalDto createWithdrawal(Long investorId, WithdrawalRequest request) {
        Investor investor = investor(investorId);
        InvestmentProduct product = products.findForWithdrawal(request.productId()).orElseThrow(() -> new NotFoundException("Product not found."));
        if (!product.getInvestor().getId().equals(investorId)) throw new NotFoundException("Product does not belong to this investor.");
        rules.validate(product.getType(), investor.getDateOfBirth(), product.getBalance(), request.amount(), LocalDate.now());
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal before = product.getBalance();
        BigDecimal remaining = before.subtract(amount);
        product.setBalance(remaining);
        // Saving the notice and balance happens in one transaction: both succeed or both roll back.
        WithdrawalNotice notice = withdrawals.save(new WithdrawalNotice(product, amount, before, remaining, LocalDateTime.now()));
        return noticeDto(notice);
    }
    @Transactional(readOnly = true)
    public List<WithdrawalDto> history(Long investorId, Long productId, LocalDate from, LocalDate to) {
        investor(investorId);
        if (from != null && to != null && from.isAfter(to)) throw new ValidationException("from", "From date must be on or before To date.");
        if (productId != null) {
            InvestmentProduct product = products.findById(productId).orElseThrow(() -> new NotFoundException("Product not found."));
            if (!product.getInvestor().getId().equals(investorId)) throw new NotFoundException("Product does not belong to this investor.");
        }
        return withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investorId).stream()
            .filter(n -> productId == null || n.getProduct().getId().equals(productId))
            .filter(n -> from == null || !n.getCreatedAt().toLocalDate().isBefore(from))
            .filter(n -> to == null || !n.getCreatedAt().toLocalDate().isAfter(to))
            .map(this::noticeDto).toList();
    }
    public String csv(List<WithdrawalDto> notices) {
        // Tell Excel to use commas even when Windows uses a different list separator.
        StringBuilder csv = new StringBuilder("\uFEFFsep=,\r\nNotice ID,Recorded at,Product,Amount ZAR,Balance before ZAR,Remaining balance ZAR\r\n");
        for (WithdrawalDto notice : notices) {
            csv.append(notice.id()).append(',').append(notice.createdAt()).append(',').append(csvCell(notice.productName())).append(',')
                .append(notice.amount().toPlainString()).append(',').append(notice.balanceBefore().toPlainString()).append(',')
                .append(notice.remainingBalance().toPlainString()).append("\r\n");
        }
        return csv.toString();
    }
    private String csvCell(String text) {
        // Quote text fields and prevent spreadsheet formulas from being interpreted.
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
