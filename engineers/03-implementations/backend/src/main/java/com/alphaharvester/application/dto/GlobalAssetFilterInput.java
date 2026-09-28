package com.alphaharvester.application.dto;

public record GlobalAssetFilterInput(
        String ticker
) {
    public GlobalAssetFilterInput() {
        this(null);
    }
}
