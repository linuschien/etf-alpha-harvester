package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.OrthogonalStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("global_asset_score")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
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

    @Column("fund_size_twd")
    private BigDecimal fundSizeTwd;

    @Transient
    private OrthogonalStatus orthogonalStatus;

    @Transient
    private String collisionDetail;

    @Column("r_squared")
    private BigDecimal rSquared;

    @Column("momentum_12m")
    private BigDecimal momentum12m;

    @Column("kaufman_er")
    private BigDecimal kaufmanEr;

    @Column("sharpe_ratio")
    private BigDecimal sharpeRatio;

    @Column("volatility_90d")
    private BigDecimal volatility90d;

    @Column("ytm")
    private BigDecimal ytm;

    @Column("dca_rank")
    private Integer dcaRank;

    public GlobalAssetScore(UUID id, UUID assetId, String ticker, LocalDateTime evaluationDate,
                            CandidateAssetClass assetClass, Integer classRank,
                            BigDecimal compositeScore, BigDecimal fundSizeTwd) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.evaluationDate = evaluationDate;
        this.assetClass = assetClass;
        this.classRank = classRank;
        this.compositeScore = compositeScore;
        this.fundSizeTwd = fundSizeTwd;
    }
}
