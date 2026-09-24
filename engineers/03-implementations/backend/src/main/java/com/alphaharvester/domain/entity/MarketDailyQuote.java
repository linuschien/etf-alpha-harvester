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

@Table("market_daily_quote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class MarketDailyQuote {

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

    @Column("net_asset_value")
    private BigDecimal netAssetValue;

    @Column("discount_premium_percentage")
    private BigDecimal discountPremiumPercentage;
}
