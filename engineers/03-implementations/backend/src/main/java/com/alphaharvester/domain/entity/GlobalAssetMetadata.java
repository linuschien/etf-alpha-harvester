package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CandidateAssetClass;
import com.alphaharvester.domain.model.DistributionFrequency;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Table("global_asset_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
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
}
