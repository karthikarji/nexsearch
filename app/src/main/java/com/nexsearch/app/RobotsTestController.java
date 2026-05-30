package com.nexsearch.app;

import com.nexsearch.app.dto.RobotsCheckRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.robots.dto.RobotsCheckResult;
import com.nexsearch.robots.service.RobotsTxtService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/robots")
public class RobotsTestController {

    private final RobotsTxtService robotsTxtService;

    public RobotsTestController(RobotsTxtService robotsTxtService) {
        this.robotsTxtService = robotsTxtService;
    }

    @PostMapping("/check")
    public ApiResponse<RobotsCheckResult> check(
            @Valid @RequestBody RobotsCheckRequest request
    ) {
        RobotsCheckResult result = robotsTxtService.check(request.url());

        return ApiResponse.success(
                "robots.txt check completed",
                result
        );
    }
}