package com.alphaharvester.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("macro_yield_snapshot")
public class MacroYieldSnapshot {

    @Id
    @Column("id")
    private UUID id;

    @Column("record_date")
    private LocalDateTime recordDate;

    @Column("us_corporate_bond_effective_yield")
    private BigDecimal usCorporateBondEffectiveYield;

    @Column("us_10_year_treasury_yield")
    private BigDecimal us10YearTreasuryYield;

    @Column("us_20_year_treasury_yield")
    private BigDecimal us20YearTreasuryYield;

    @Column("yield_spread_10y_minus_2y")
    private BigDecimal yieldSpread10yMinus2y;

    public MacroYieldSnapshot() {
    }

    public MacroYieldSnapshot(UUID id, LocalDateTime recordDate, BigDecimal usCorporateBondEffectiveYield,
                              BigDecimal us10YearTreasuryYield, BigDecimal us20YearTreasuryYield,
                              BigDecimal yieldSpread10yMinus2y) {
        this.id = id;
        this.recordDate = recordDate;
        this.usCorporateBondEffectiveYield = usCorporateBondEffectiveYield;
        this.us10YearTreasuryYield = us10YearTreasuryYield;
        this.us20YearTreasuryYield = us20YearTreasuryYield;
        this.yieldSpread10yMinus2y = yieldSpread10yMinus2y;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDateTime recordDate) {
        this.recordDate = recordDate;
    }

    public BigDecimal getUsCorporateBondEffectiveYield() {
        return usCorporateBondEffectiveYield;
    }

    public void setUsCorporateBondEffectiveYield(BigDecimal usCorporateBondEffectiveYield) {
        this.usCorporateBondEffectiveYield = usCorporateBondEffectiveYield;
    }

    public BigDecimal getUs10YearTreasuryYield() {
        return us10YearTreasuryYield;
    }

    public void setUs10YearTreasuryYield(BigDecimal us10YearTreasuryYield) {
        this.us10YearTreasuryYield = us10YearTreasuryYield;
    }

    public BigDecimal getUs20YearTreasuryYield() {
        return us20YearTreasuryYield;
    }

    public void setUs20YearTreasuryYield(BigDecimal us20YearTreasuryYield) {
        this.us20YearTreasuryYield = us20YearTreasuryYield;
    }

    public BigDecimal getYieldSpread10yMinus2y() {
        return yieldSpread10yMinus2y;
    }

    public void setYieldSpread10yMinus2y(BigDecimal yieldSpread10yMinus2y) {
        this.yieldSpread10yMinus2y = yieldSpread10yMinus2y;
    }
}

