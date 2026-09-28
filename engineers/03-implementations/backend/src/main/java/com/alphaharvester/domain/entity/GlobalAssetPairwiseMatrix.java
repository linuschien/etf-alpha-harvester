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

@Table("global_asset_pairwise_matrix")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class GlobalAssetPairwiseMatrix {

    @Id
    @Column("id")
    private UUID id;

    @Column("evaluation_date")
    private LocalDateTime evaluationDate;

    @Column("asset_class")
    private CandidateAssetClass assetClass;

    @Column("base_ticker")
    private String baseTicker;

    @Column("target_ticker")
    private String targetTicker;

    @Column("r_squared")
    private BigDecimal rSquared;

    @Column("correlation_coefficient")
    private BigDecimal correlationCoefficient;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public GlobalAssetPairwiseMatrix(UUID id, LocalDateTime evaluationDate, CandidateAssetClass assetClass,
                                     String baseTicker, String targetTicker,
                                     BigDecimal rSquared, BigDecimal correlationCoefficient) {
        this.id = id;
        this.evaluationDate = evaluationDate;
        this.assetClass = assetClass;
        this.baseTicker = baseTicker;
        this.targetTicker = targetTicker;
        this.rSquared = rSquared;
        this.correlationCoefficient = correlationCoefficient;
        this.createdAt = LocalDateTime.now();
    }
}
