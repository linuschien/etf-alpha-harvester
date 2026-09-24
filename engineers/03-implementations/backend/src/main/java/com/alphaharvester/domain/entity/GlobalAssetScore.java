package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CandidateAssetClass;
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

    @Column("total_expense_ratio")
    private BigDecimal totalExpenseRatio;

    @Column("fund_size_twd")
    private BigDecimal fundSizeTwd;

    @Column("is_qualified")
    private Boolean isQualified;

    @Column("disqualification_reason")
    private String disqualificationReason;
}
