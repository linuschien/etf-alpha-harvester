package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.DistributionFrequency;
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

import java.util.UUID;

@Table("dca_popularity_rank")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
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

    @Transient
    private String name;

    @Transient
    private DistributionFrequency distributionFrequency;

    public DcaPopularityRank(UUID id, UUID assetId, String ticker, Integer rankingYear, Integer rankingMonth, Integer rankPosition, Integer regularInvestorCount) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.rankingYear = rankingYear;
        this.rankingMonth = rankingMonth;
        this.rankPosition = rankPosition;
        this.regularInvestorCount = regularInvestorCount;
    }
}
