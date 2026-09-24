package com.alphaharvester.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("dca_popularity_rank")
public class DcaPopularityRank {

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("ticker")
    private String ticker;

    @Column("ranking_year")
    private Integer rankingYear;

    @Column("ranking_month")
    private Integer rankingMonth;

    @Column("rank_position")
    private Integer rankPosition;

    @Column("regular_investor_count")
    private Integer regularInvestorCount;

    public DcaPopularityRank() {
    }

    public DcaPopularityRank(UUID id, UUID assetId, String ticker, Integer rankingYear,
                             Integer rankingMonth, Integer rankPosition, Integer regularInvestorCount) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.rankingYear = rankingYear;
        this.rankingMonth = rankingMonth;
        this.rankPosition = rankPosition;
        this.regularInvestorCount = regularInvestorCount;
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

    public Integer getRankingYear() {
        return rankingYear;
    }

    public void setRankingYear(Integer rankingYear) {
        this.rankingYear = rankingYear;
    }

    public Integer getRankingMonth() {
        return rankingMonth;
    }

    public void setRankingMonth(Integer rankingMonth) {
        this.rankingMonth = rankingMonth;
    }

    public Integer getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(Integer rankPosition) {
        this.rankPosition = rankPosition;
    }

    public Integer getRegularInvestorCount() {
        return regularInvestorCount;
    }

    public void setRegularInvestorCount(Integer regularInvestorCount) {
        this.regularInvestorCount = regularInvestorCount;
    }
}

