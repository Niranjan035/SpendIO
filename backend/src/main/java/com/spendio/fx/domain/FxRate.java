package com.spendio.fx.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "fx_rates")
public class FxRate {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "base_currency_code", nullable = false, length = 3)
    private String baseCurrencyCode;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name = "quote_currency_code", nullable = false, length = 3)
    private String quoteCurrencyCode;

  @Column(nullable = false, precision = 20, scale = 10)
  private BigDecimal rate;

  @Column(name = "rate_date", nullable = false)
  private LocalDate rateDate;

  @Column(nullable = false, length = 40)
  private String provider;

  @Column(name = "is_manual", nullable = false)
  private boolean isManual;

  @Column(name = "fetched_at", nullable = false, updatable = false, columnDefinition = "DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)")
  private Instant fetchedAt;

  @PrePersist
  public void onCreate() {
    if (fetchedAt == null) fetchedAt = Instant.now();
  }

  public Long getId() { return id; }
  public String getBaseCurrencyCode() { return baseCurrencyCode; }
  public String getQuoteCurrencyCode() { return quoteCurrencyCode; }
  public BigDecimal getRate() { return rate; }
  public LocalDate getRateDate() { return rateDate; }
  public String getProvider() { return provider; }
  public boolean isManual() { return isManual; }
  public Instant getFetchedAt() { return fetchedAt; }

  public void setBaseCurrencyCode(String c) { this.baseCurrencyCode = c; }
  public void setQuoteCurrencyCode(String c) { this.quoteCurrencyCode = c; }
  public void setRate(BigDecimal r) { this.rate = r; }
  public void setRateDate(LocalDate d) { this.rateDate = d; }
  public void setProvider(String p) { this.provider = p; }
  public void setManual(boolean m) { this.isManual = m; }
}