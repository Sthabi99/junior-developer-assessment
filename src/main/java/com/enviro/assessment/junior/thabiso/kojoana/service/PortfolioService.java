package com.enviro.assessment.junior.thabiso.kojoana.service;

import com.enviro.assessment.junior.thabiso.kojoana.dto.*;
import com.enviro.assessment.junior.thabiso.kojoana.exception.*;
import com.enviro.assessment.junior.thabiso.kojoana.model.*;
import com.enviro.assessment.junior.thabiso.kojoana.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// Repository records are converted to response DTOs here; withdrawal saves also update the balance.
public class PortfolioService {
  private final InvestorRepository investors;
  private final ProductRepository products;
  private final WithdrawalRepository withdrawals;
  private final WithdrawalRules rules;

  public PortfolioService(
      InvestorRepository investors,
      ProductRepository products,
      WithdrawalRepository withdrawals,
      WithdrawalRules rules) {
    this.investors = investors;
    this.products = products;
    this.withdrawals = withdrawals;
    this.rules = rules;
  }

  private Investor investor(Long id) {
    Optional<Investor> found = investors.findById(id);
    if (found.isEmpty()) {
      throw new NotFoundException("Investor not found.");
    }
    return found.get();
  }

  private InvestorDto investorDto(Investor investor) {
    return new InvestorDto(
        investor.getId(),
        investor.getName(),
        investor.getDateOfBirth(),
        rules.age(investor.getDateOfBirth(), LocalDate.now()));
  }

  private WithdrawalDto noticeDto(WithdrawalNotice notice) {
    return new WithdrawalDto(
        notice.getId(),
        notice.getProduct().getId(),
        notice.getProduct().getName(),
        notice.getAmount(),
        notice.getBalanceBefore(),
        notice.getRemainingBalance(),
        notice.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public List<InvestorDto> listInvestors() {
    List<InvestorDto> result = new ArrayList<>();
    for (Investor investor : investors.findAll()) {
      result.add(investorDto(investor));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public PortfolioDto portfolio(Long id) {
    Investor investor = investor(id);
    List<WithdrawalNotice> notices =
        withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(id);
    List<ProductDto> result = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.ZERO;
    // Notices arrive newest first. Reverse a copy to build the balance history oldest first.
    List<WithdrawalNotice> oldestFirst = new ArrayList<>(notices);
    Collections.reverse(oldestFirst);
    for (InvestmentProduct product : products.findByInvestorIdOrderById(id)) {
      boolean allowed =
          rules.allowed(product.getType(), investor.getDateOfBirth(), LocalDate.now());
      BigDecimal maximum = BigDecimal.ZERO.setScale(2);
      String eligibilityMessage = "Retirement withdrawals require an age above 65.";
      if (allowed) {
        maximum = rules.maximum(product.getBalance());
        eligibilityMessage = "Up to 90% of the current balance.";
      }
      total = total.add(product.getBalance());
      available = available.add(maximum);
      List<BalancePointDto> history = new ArrayList<>();
      history.add(new BalancePointDto(product.getOpenedAt(), product.getOpeningBalance()));
      // Balance history contains the opening balance and saved withdrawal events.
      for (WithdrawalNotice notice : oldestFirst) {
        if (notice.getProduct().getId().equals(product.getId())) {
          history.add(new BalancePointDto(notice.getCreatedAt(), notice.getRemainingBalance()));
        }
      }
      result.add(
          new ProductDto(
              product.getId(),
              product.getName(),
              product.getType().name(),
              product.getBalance(),
              maximum,
              allowed,
              eligibilityMessage,
              history));
    }
    return new PortfolioDto(investorDto(investor), total, available, notices.size(), result);
  }

  @Transactional(readOnly = true)
  public WithdrawalDto withdrawal(Long investorId, Long noticeId) {
    investor(investorId);
    Optional<WithdrawalNotice> found = withdrawals.findById(noticeId);
    if (found.isEmpty()) {
      throw new NotFoundException("Withdrawal notice not found.");
    }
    WithdrawalNotice notice = found.get();
    if (!notice.getProduct().getInvestor().getId().equals(investorId))
      throw new NotFoundException("Withdrawal notice does not belong to this investor.");
    return noticeDto(notice);
  }

  @Transactional
  public WithdrawalDto createWithdrawal(Long investorId, WithdrawalRequest request) {
    Investor investor = investor(investorId);
    // The product stays locked until this transaction finishes. Another request waits,
    // then uses the updated balance instead of spending the same balance twice.
    Optional<InvestmentProduct> found = products.findForWithdrawal(request.productId());
    if (found.isEmpty()) {
      throw new NotFoundException("Product not found.");
    }
    InvestmentProduct product = found.get();
    if (!product.getInvestor().getId().equals(investorId))
      throw new NotFoundException("Product does not belong to this investor.");
    // Validation runs before the stored balance changes.
    rules.validate(
        product.getType(),
        investor.getDateOfBirth(),
        product.getBalance(),
        request.amount(),
        LocalDate.now());
    BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
    // The notice includes the old balance so the withdrawal can be checked against it later.
    BigDecimal before = product.getBalance();
    BigDecimal remaining = before.subtract(amount);
    product.setBalance(remaining);
    // The balance change and notice belong to the same transaction.
    // If saving fails, Spring rolls back both changes so the account stays consistent.
    WithdrawalNotice notice =
        withdrawals.save(
            new WithdrawalNotice(product, amount, before, remaining, LocalDateTime.now()));
    return noticeDto(notice);
  }

  @Transactional(readOnly = true)
  public List<WithdrawalDto> history(
      Long investorId, Long productId, LocalDate from, LocalDate to) {
    investor(investorId);
    if (from != null && to != null && from.isAfter(to))
      throw new ValidationException("from", "From date must be on or before To date.");
    if (productId != null) {
      Optional<InvestmentProduct> found = products.findById(productId);
      if (found.isEmpty()) {
        throw new NotFoundException("Product not found.");
      }
      InvestmentProduct product = found.get();
      if (!product.getInvestor().getId().equals(investorId))
        throw new NotFoundException("Product does not belong to this investor.");
    }
    // Both selected dates are included. An empty product filter includes all products.
    List<WithdrawalDto> result = new ArrayList<>();
    List<WithdrawalNotice> notices =
        withdrawals.findByProductInvestorIdOrderByCreatedAtDescIdDesc(investorId);
    for (WithdrawalNotice notice : notices) {
      LocalDate recordedDate = notice.getCreatedAt().toLocalDate();
      if (productId != null && !notice.getProduct().getId().equals(productId)) {
        continue;
      }
      if (from != null && recordedDate.isBefore(from)) {
        continue;
      }
      if (to != null && recordedDate.isAfter(to)) {
        continue;
      }
      result.add(noticeDto(notice));
    }
    return result;
  }

  public String csv(List<WithdrawalDto> notices) {
    // The separator hint makes Excel split columns at commas, regardless of regional settings.
    StringBuilder csv =
        new StringBuilder(
            "\uFEFFsep=,\r\n"
                + "Notice ID,Recorded at,Product,Amount (R),Balance before (R),Remaining balance"
                + " (R)\r\n");
    for (WithdrawalDto notice : notices) {
      csv.append(notice.id())
          .append(',')
          .append(notice.createdAt())
          .append(',')
          .append(csvCell(notice.productName()))
          .append(',')
          .append(notice.amount().toPlainString())
          .append(',')
          .append(notice.balanceBefore().toPlainString())
          .append(',')
          .append(notice.remainingBalance().toPlainString())
          .append("\r\n");
    }
    return csv.toString();
  }

  private String csvCell(String text) {
    // Quoted text can contain commas. The apostrophe also stops Excel treating it as a formula.
    if (text.matches("^[=+@-].*")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }
}
