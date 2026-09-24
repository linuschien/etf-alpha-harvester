package com.alphaharvester;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories(basePackages = "com.alphaharvester.adapter.out.persistence")
public class AlphaHarvesterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlphaHarvesterApplication.class, args);
    }
}

