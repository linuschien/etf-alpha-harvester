package com.alphaharvester.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("macro_yield_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
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
}
