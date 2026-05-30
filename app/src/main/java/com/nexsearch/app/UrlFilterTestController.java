package com.nexsearch.app;

import com.nexsearch.app.dto.UrlFilterRequest;
import com.nexsearch.app.dto.UrlFilterResponse;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.common.util.UrlFilter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/url")
public class UrlFilterTestController {

    @PostMapping("/filter")
    public ApiResponse<UrlFilterResponse> filter(
            @Valid @RequestBody UrlFilterRequest request
    ) {
        boolean crawlable = UrlFilter.isCrawlable(request.url());

        return ApiResponse.success(
                "URL filter check completed",
                new UrlFilterResponse(request.url(), crawlable)
        );
    }
}