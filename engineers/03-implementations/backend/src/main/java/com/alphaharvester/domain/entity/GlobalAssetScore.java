package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CandidateAssetClass;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("global_asset_score")
public class GlobalAssetScore {

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("ticker")
    private String ticker;

    @Column("evaluation_date")
    private LocalDateTime evaluationDate;

    @Column("asset_class")
    private CandidateAssetClass assetClass;

    @Column("class_rank")
    private Integer classRank;

    @Column("composite_score")
    private BigDecimal compositeScore;

    @Column("total_expense_ratio")
    private BigDecimal totalExpenseRatio;

    @Column("fund_size_twd")
    private BigDecimal fundSizeTwd;

    @Column("is_qualified")
    private Boolean isQualified;

    @Column("disqualification_reason")
    private String disqualificationReason;

    public GlobalAssetScore() {
    }

    public GlobalAssetScore(UUID id, UUID assetId, String ticker, LocalDateTime evaluationDate,
                            CandidateAssetClass assetClass, Integer classRank, BigDecimal compositeScore,
                            BigDecimal totalExpenseRatio, BigDecimal fundSizeTwd, Boolean isQualified,
                            String disqualificationReason) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.evaluationDate = evaluationDate;
        this.assetClass = assetClass;
        this.classRank = classRank;
        this.compositeScore = compositeScore;
        this.totalExpenseRatio = totalExpenseRatio;
        this.fundSizeTwd = fundSizeTwd;
        this.isQualified = isQualified;
        this.disqualificationReason = disqualificationReason;
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

    public LocalDateTime getEvaluationDate() {
        return evaluationDate;
    }

    public void setEvaluationDate(LocalDateTime evaluationDate) {
        this.evaluationDate = evaluationDate;
    }

    public CandidateAssetClass getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(CandidateAssetClass assetClass) {
        this.assetClass = assetClass;
    }

    public Integer getClassRank() {
        return classRank;
    }

    public void setClassRank(Integer classRank) {
        this.classRank = classRank;
    }

    public BigDecimal getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(BigDecimal compositeScore) {
        this.compositeScore = compositeScore;
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

    public Boolean getIsQualified() {
        return isQualified;
    }

    public void setIsQualified(Boolean isQualified) {
        this.isQualified = isQualified;
    }

    public String getDisqualificationReason() {
        return disqualificationReason;
    }

    public void setDisqualificationReason(String disqualificationReason) {
        this.disqualificationReason = disqualificationReason;
    }
}

