package com.nexsearch.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.nexsearch")
@EntityScan(basePackages = "com.nexsearch")
@EnableJpaRepositories(basePackages = "com.nexsearch")
public class NexSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexSearchApplication.class, args);
    }
}