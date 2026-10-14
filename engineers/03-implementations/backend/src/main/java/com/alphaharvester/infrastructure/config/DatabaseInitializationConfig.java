package com.alphaharvester.infrastructure.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DatabaseInitializationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializationConfig.class);

    @Value("${spring.flyway.url}")
    private String flywayUrl;

    @Value("${spring.flyway.user}")
    private String flywayUser;

    @Value("${spring.flyway.password:}")
    private String flywayPassword;

    @Value("${spring.flyway.locations}")
    private String flywayLocations;

    @Bean(destroyMethod = "close")
    public DataSource cacheDataSource() {
        log.info("Initializing HikariCP DataSource for Flyway and Cache Persistence on: {}", flywayUrl);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(flywayUrl);
        config.setUsername(flywayUser);
        config.setPassword(flywayPassword);
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setPoolName("EhcacheHikariPool");
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource cacheDataSource) {
        log.info("Initializing Flyway database migration with DataSource on: {}", flywayUrl);
        return Flyway.configure()
                .dataSource(cacheDataSource)
                .locations(flywayLocations)
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @DependsOn("flyway")
    public JdbcTemplate cacheJdbcTemplate(DataSource cacheDataSource) {
        return new JdbcTemplate(cacheDataSource);
    }
}
