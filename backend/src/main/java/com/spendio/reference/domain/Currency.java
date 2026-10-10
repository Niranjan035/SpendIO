package com.spendio.reference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the ISO-4217 reference table seeded by {@code V3}.
 *
 * <p>Read-only: no application code writes or deletes currencies
 * (ARCHITECTURE.md D.4), which is why this entity carries no audit columns
 * and no optimistic lock.
 */
@Entity
@Table(name = "currencies")
public class Currency {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 3, nullable = false)
    private String code;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "minor_unit", nullable = false)
    private int minorUnit;

    @Column(name = "symbol", length = 8)
    private String symbol;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Currency() {
    }

    public Currency(String code, String name, int minorUnit, String symbol, boolean active, int sortOrder) {
        this.code = code;
        this.name = name;
        this.minorUnit = minorUnit;
        this.symbol = symbol;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getMinorUnit() {
        return minorUnit;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Currency currency && Objects.equals(code, currency.code);
    }

    @Override
    public int hashCode() {
        return code == null ? 0 : code.hashCode();
    }
}
