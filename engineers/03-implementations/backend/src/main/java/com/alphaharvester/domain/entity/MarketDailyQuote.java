package com.alphaharvester.domain.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("market_daily_quote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class MarketDailyQuote implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("benchmark_id")
    private UUID benchmarkId;

    @Column("ticker")
    private String ticker;

    @Column("trade_date")
    private LocalDateTime tradeDate;

    @Column("open_price")
    private BigDecimal openPrice;

    @Column("high_price")
    private BigDecimal highPrice;

    @Column("low_price")
    private BigDecimal lowPrice;

    @Column("close_price")
    private BigDecimal closePrice;

    @Column("volume_shares")
    private Long volumeShares;

    @Column("trade_value_twd")
    private BigDecimal tradeValueTwd;

    @Transient
    private BigDecimal ma20;

    @Transient
    private BigDecimal ma60;

    @Transient
    private BigDecimal ma120;

    @Transient
    private BigDecimal ma240;

    @Transient
    private BigDecimal bbUpper;

    @Transient
    private BigDecimal bbMiddle;

    @Transient
    private BigDecimal bbLower;

    /**
     * Backwards-compatible 11-argument constructor for existing callers and tests.
     */
    public MarketDailyQuote(UUID id, UUID assetId, UUID benchmarkId, String ticker,
                            LocalDateTime tradeDate, BigDecimal openPrice, BigDecimal highPrice,
                            BigDecimal lowPrice, BigDecimal closePrice, Long volumeShares,
                            BigDecimal tradeValueTwd) {
        this.id = id;
        this.assetId = assetId;
        this.benchmarkId = benchmarkId;
        this.ticker = ticker;
        this.tradeDate = tradeDate;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volumeShares = volumeShares;
        this.tradeValueTwd = tradeValueTwd;
    }
}
