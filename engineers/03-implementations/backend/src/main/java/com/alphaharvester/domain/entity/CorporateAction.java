package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CorporateActionType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("corporate_action")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(of = "id")
public class CorporateAction {

    @Id
    @Column("id")
    private UUID id;

    @Column("asset_id")
    private UUID assetId;

    @Column("ticker")
    private String ticker;

    @Column("action_type")
    private CorporateActionType actionType;

    @Column("effective_date")
    private LocalDateTime effectiveDate;

    @Column("split_to_shares")
    private Integer splitToShares;

    @Column("split_from_shares")
    private Integer splitFromShares;
}
