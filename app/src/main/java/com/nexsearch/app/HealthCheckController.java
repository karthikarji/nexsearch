package com.nexsearch.app;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

    @GetMapping("/api/health")
    public String health() {
        return "NexSearch backend is running";
    }
}