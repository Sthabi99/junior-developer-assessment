package com.enviro.assessment.junior.thabiso.kojoana.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity
public class InvestmentProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Investor investor;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private LocalDateTime openedAt;

    protected InvestmentProduct() { } // JPA needs an empty constructor.

    public InvestmentProduct(Investor investor, String name, ProductType type, BigDecimal balance, BigDecimal openingBalance, LocalDateTime openedAt) {
        this.investor = investor;
        this.name = name;
        this.type = type;
        this.balance = balance;
        this.openingBalance = openingBalance;
        this.openedAt = openedAt;
    }

    public Long getId() { return id; }
    public Investor getInvestor() { return investor; }
    public String getName() { return name; }
    public ProductType getType() { return type; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public LocalDateTime getOpenedAt() { return openedAt; }

    public void setOpeningDetails(BigDecimal openingBalance, LocalDateTime openedAt) {
        this.openingBalance = openingBalance;
        this.openedAt = openedAt;
    }

    public void setBalance(BigDecimal balance) { this.balance = balance; }

}
