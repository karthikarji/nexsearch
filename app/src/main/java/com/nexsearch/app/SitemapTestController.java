package com.nexsearch.app;

import com.nexsearch.app.dto.SitemapDiscoveryRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.sitemap.dto.SitemapDiscoveryResult;
import com.nexsearch.sitemap.service.SitemapService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sitemap")
public class SitemapTestController {

    private static final int DEFAULT_MAX_URLS = 100;

    private final SitemapService sitemapService;

    public SitemapTestController(SitemapService sitemapService) {
        this.sitemapService = sitemapService;
    }

    @PostMapping("/discover")
    public ApiResponse<SitemapDiscoveryResult> discover(
            @Valid @RequestBody SitemapDiscoveryRequest request
    ) {
        int maxUrls = request.maxUrls() == null
                ? DEFAULT_MAX_URLS
                : request.maxUrls();

        SitemapDiscoveryResult result = sitemapService.discover(
                request.sitemapUrl(),
                maxUrls
        );

        return ApiResponse.success(
                "Sitemap discovery completed",
                result
        );
    }
}