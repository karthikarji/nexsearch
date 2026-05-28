package com.nexsearch.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.nexsearch")
public class NexSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexSearchApplication.class, args);
    }

}
