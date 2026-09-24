package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("global_asset_metadata")
public class GlobalAssetMetadata {

    @Id
    @Column("id")
    private UUID id;

    @Column("ticker")
    private String ticker;

    @Column("name")
    private String name;

    @Column("listing_date")
    private LocalDateTime listingDate;

    @Column("underlying_index")
    private String underlyingIndex;

    @Column("issuer")
    private String issuer;

    @Column("total_expense_ratio")
    private BigDecimal totalExpenseRatio;

    @Column("fund_size_twd")
    private BigDecimal fundSizeTwd;

    @Column("asset_class")
    private CandidateAssetClass assetClass;

    @Column("distribution_frequency")
    private DistributionFrequency distributionFrequency;

    @Version
    @Column("version")
    private Integer version;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;

    public GlobalAssetMetadata() {
    }

    public GlobalAssetMetadata(UUID id, String ticker, String name, LocalDateTime listingDate,
                               String underlyingIndex, String issuer, BigDecimal totalExpenseRatio,
                               BigDecimal fundSizeTwd, CandidateAssetClass assetClass,
                               DistributionFrequency distributionFrequency, Integer version,
                               LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.ticker = ticker;
        this.name = name;
        this.listingDate = listingDate;
        this.underlyingIndex = underlyingIndex;
        this.issuer = issuer;
        this.totalExpenseRatio = totalExpenseRatio;
        this.fundSizeTwd = fundSizeTwd;
        this.assetClass = assetClass;
        this.distributionFrequency = distributionFrequency;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getListingDate() {
        return listingDate;
    }

    public void setListingDate(LocalDateTime listingDate) {
        this.listingDate = listingDate;
    }

    public String getUnderlyingIndex() {
        return underlyingIndex;
    }

    public void setUnderlyingIndex(String underlyingIndex) {
        this.underlyingIndex = underlyingIndex;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public BigDecimal getTotalExpenseRatio() {
        return totalExpenseRatio;
    }

    public void setTotalExpenseRatio(BigDecimal totalExpenseRatio) {
        this.totalExpenseRatio = totalExpenseRatio;
    }

    public BigDecimal getFundSizeTwd() {
        return fundSizeTwd;
    }

    public void setFundSizeTwd(BigDecimal fundSizeTwd) {
        this.fundSizeTwd = fundSizeTwd;
    }

    public CandidateAssetClass getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(CandidateAssetClass assetClass) {
        this.assetClass = assetClass;
    }

    public DistributionFrequency getDistributionFrequency() {
        return distributionFrequency;
    }

    public void setDistributionFrequency(DistributionFrequency distributionFrequency) {
        this.distributionFrequency = distributionFrequency;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

