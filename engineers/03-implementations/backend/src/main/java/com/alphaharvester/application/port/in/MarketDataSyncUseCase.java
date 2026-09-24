package com.alphaharvester.application.port.in;

import com.alphaharvester.application.dto.MarketDataSyncRequest;
import com.alphaharvester.application.dto.MarketDataSyncResponse;
import reactor.core.publisher.Mono;

public interface MarketDataSyncUseCase {
    Mono<MarketDataSyncResponse> syncMarketData(MarketDataSyncRequest request);
}

