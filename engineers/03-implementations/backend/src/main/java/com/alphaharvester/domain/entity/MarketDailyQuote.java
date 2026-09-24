package com.alphaharvester.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("market_daily_quote")
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

    public MarketDailyQuote() {
    }

    public MarketDailyQuote(UUID id, UUID assetId, UUID benchmarkId, String ticker, LocalDateTime tradeDate,
                            BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                            Long volumeShares, BigDecimal tradeValueTwd, BigDecimal netAssetValue,
                            BigDecimal discountPremiumPercentage) {
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
        this.netAssetValue = netAssetValue;
        this.discountPremiumPercentage = discountPremiumPercentage;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public UUID getBenchmarkId() {
        return benchmarkId;
    }

    public void setBenchmarkId(UUID benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public LocalDateTime getTradeDate() {
        return tradeDate;
    }

    public void setTradeDate(LocalDateTime tradeDate) {
        this.tradeDate = tradeDate;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getHighPrice() {
        return highPrice;
    }

    public void setHighPrice(BigDecimal highPrice) {
        this.highPrice = highPrice;
    }

    public BigDecimal getLowPrice() {
        return lowPrice;
    }

    public void setLowPrice(BigDecimal lowPrice) {
        this.lowPrice = lowPrice;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public void setClosePrice(BigDecimal closePrice) {
        this.closePrice = closePrice;
    }

    public Long getVolumeShares() {
        return volumeShares;
    }

    public void setVolumeShares(Long volumeShares) {
        this.volumeShares = volumeShares;
    }

    public BigDecimal getTradeValueTwd() {
        return tradeValueTwd;
    }

    public void setTradeValueTwd(BigDecimal tradeValueTwd) {
        this.tradeValueTwd = tradeValueTwd;
    }

    public BigDecimal getNetAssetValue() {
        return netAssetValue;
    }

    public void setNetAssetValue(BigDecimal netAssetValue) {
        this.netAssetValue = netAssetValue;
    }

    public BigDecimal getDiscountPremiumPercentage() {
        return discountPremiumPercentage;
    }

    public void setDiscountPremiumPercentage(BigDecimal discountPremiumPercentage) {
        this.discountPremiumPercentage = discountPremiumPercentage;
    }
}

