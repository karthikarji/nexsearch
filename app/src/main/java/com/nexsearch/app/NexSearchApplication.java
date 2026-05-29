package com.nexsearch.app;

import com.nexsearch.document.model.DocumentEntity;
import com.nexsearch.document.repository.DocumentRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.nexsearch")
@EntityScan(basePackageClasses = DocumentEntity.class)
@EnableJpaRepositories(basePackageClasses = DocumentRepository.class)
public class NexSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexSearchApplication.class, args);
    }
}