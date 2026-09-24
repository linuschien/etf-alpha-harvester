package com.alphaharvester.domain.entity;

import com.alphaharvester.domain.model.CorporateActionType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("corporate_action")
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

    public CorporateAction() {
    }

    public CorporateAction(UUID id, UUID assetId, String ticker, CorporateActionType actionType,
                           LocalDateTime effectiveDate, Integer splitToShares, Integer splitFromShares) {
        this.id = id;
        this.assetId = assetId;
        this.ticker = ticker;
        this.actionType = actionType;
        this.effectiveDate = effectiveDate;
        this.splitToShares = splitToShares;
        this.splitFromShares = splitFromShares;
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

    public CorporateActionType getActionType() {
        return actionType;
    }

    public void setActionType(CorporateActionType actionType) {
        this.actionType = actionType;
    }

    public LocalDateTime getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDateTime effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public Integer getSplitToShares() {
        return splitToShares;
    }

    public void setSplitToShares(Integer splitToShares) {
        this.splitToShares = splitToShares;
    }

    public Integer getSplitFromShares() {
        return splitFromShares;
    }

    public void setSplitFromShares(Integer splitFromShares) {
        this.splitFromShares = splitFromShares;
    }
}

