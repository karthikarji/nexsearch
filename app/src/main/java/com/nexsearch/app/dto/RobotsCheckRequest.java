package com.nexsearch.app.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record RobotsCheckRequest(
        @NotBlank(message = "URL is required")
        @URL(message = "URL must be valid")
        String url
) {
}