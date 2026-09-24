package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.TaxTag;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("dividend_announcement")
public class DividendAnnouncement {

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("ticker")
    private String ticker;

    @Column("ex_date")
    private LocalDateTime exDate;

    @Column("payment_date")
    private LocalDateTime paymentDate;

    @Column("dividend_per_share")
    private BigDecimal dividendPerShare;

    @Column("tax_tag")
    private TaxTag taxTag;

    public DividendAnnouncement() {
    }

    public DividendAnnouncement(UUID id, UUID assetId, String ticker, LocalDateTime exDate,
                                LocalDateTime paymentDate, BigDecimal dividendPerShare, TaxTag taxTag) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.exDate = exDate;
        this.paymentDate = paymentDate;
        this.dividendPerShare = dividendPerShare;
        this.taxTag = taxTag;
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

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public LocalDateTime getExDate() {
        return exDate;
    }

    public void setExDate(LocalDateTime exDate) {
        this.exDate = exDate;
    }

    public LocalDateTime getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDateTime paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BigDecimal getDividendPerShare() {
        return dividendPerShare;
    }

    public void setDividendPerShare(BigDecimal dividendPerShare) {
        this.dividendPerShare = dividendPerShare;
    }

    public TaxTag getTaxTag() {
        return taxTag;
    }

    public void setTaxTag(TaxTag taxTag) {
        this.taxTag = taxTag;
    }
}

