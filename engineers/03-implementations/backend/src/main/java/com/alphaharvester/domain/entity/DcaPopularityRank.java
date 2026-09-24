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
}
