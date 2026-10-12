package com.alphaharvester.infrastructure.config;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EhcacheConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EhcacheConfig.class);

    @Test
    @DisplayName("Should cleanly initialize and close CacheManager and MonthlyQuoteCache without StateTransitionException")
    void shouldCleanlyCloseWithoutStateTransitionException() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("cacheManager");
            assertThat(context).hasBean("monthlyQuoteCache");

            CacheManager cacheManager = context.getBean(CacheManager.class);
            assertThat(cacheManager.getStatus()).isEqualTo(Status.AVAILABLE);

            Cache<?, ?> cache = context.getBean("monthlyQuoteCache", Cache.class);
            assertThat(cache).isNotNull();
        });
    }
}

