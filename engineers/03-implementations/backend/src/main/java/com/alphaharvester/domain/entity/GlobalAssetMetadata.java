package com.alphaharvester.domain.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
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

    @Column("shares_outstanding")
    private Long sharesOutstanding;

    @Column("net_asset_value")
    private BigDecimal netAssetValue;

    @Column("fund_size_twd")
    private BigDecimal fundSizeTwd;

    @Version
    @Column("version")
    private Integer version;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("deleted_at")
    private LocalDateTime deletedAt;

    @PersistenceCreator
    public GlobalAssetMetadata(UUID id, String ticker, String name, LocalDateTime listingDate,
                               String underlyingIndex, Long sharesOutstanding, BigDecimal netAssetValue,
                               BigDecimal fundSizeTwd, Integer version, LocalDateTime createdAt,
                               LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.ticker = ticker;
        this.name = name;
        this.listingDate = listingDate;
        this.underlyingIndex = underlyingIndex;
        this.sharesOutstanding = sharesOutstanding;
        this.netAssetValue = netAssetValue;
        this.fundSizeTwd = fundSizeTwd;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public GlobalAssetMetadata(UUID id, String ticker, String name, LocalDateTime listingDate,
                               String underlyingIndex, Integer version, LocalDateTime createdAt,
                               LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this(id, ticker, name, listingDate, underlyingIndex, null, null, null, version, createdAt, updatedAt, deletedAt);
    }
}
