package com.enviro.assessment.junior.thabiso.kojoana.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
public class WithdrawalNotice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InvestmentProduct product;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingBalance;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected WithdrawalNotice() { } // JPA needs an empty constructor.

    public WithdrawalNotice(InvestmentProduct product, BigDecimal amount, BigDecimal balanceBefore, BigDecimal remainingBalance, LocalDateTime createdAt) {
        this.product = product;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.remainingBalance = remainingBalance;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public InvestmentProduct getProduct() { return product; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceBefore() { return balanceBefore; }
    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public LocalDateTime getCreatedAt() { return createdAt; }

}
