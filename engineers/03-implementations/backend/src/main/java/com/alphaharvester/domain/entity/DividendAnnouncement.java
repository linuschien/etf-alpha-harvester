package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.TaxTag;
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

@Table("dividend_announcement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class DividendAnnouncement {

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("ticker")
    private String ticker;

    @Column("ex_date")
    private LocalDateTime exDate;

    @Column("payment_date")
    private LocalDateTime paymentDate;

    @Column("dividend_per_share")
    private BigDecimal dividendPerShare;

    @Column("tax_tag")
    private TaxTag taxTag;
}
