package com.alphaharvester.infrastructure.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Value("${spring.flyway.url:jdbc:h2:mem:alphaharvester;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1}")
    private String flywayUrl;

    @Value("${spring.flyway.user:sa}")
    private String flywayUser;

    @Value("${spring.flyway.password:}")
    private String flywayPassword;

    @Bean(initMethod = "migrate")
    public Flyway flyway() {
        log.info("Initializing Flyway database migration on: {}", flywayUrl);
        return Flyway.configure()
                .dataSource(flywayUrl, flywayUser, flywayPassword)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }
}

