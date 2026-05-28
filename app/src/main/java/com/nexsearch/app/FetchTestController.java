package com.nexsearch.app;

import com.nexsearch.app.dto.FetchPageRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.fetcher.dto.PageFetchResult;
import com.nexsearch.fetcher.service.PageFetcherService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fetch")
public class FetchTestController {

    private final PageFetcherService pageFetcherService;

    public FetchTestController(PageFetcherService pageFetcherService) {
        this.pageFetcherService = pageFetcherService;
    }

    @PostMapping("/test")
    public ApiResponse<PageFetchResult> fetch(@Valid @RequestBody FetchPageRequest request) {
        PageFetchResult result = pageFetcherService.fetch(request.url());
        return ApiResponse.success("Page fetched successfully", result);
    }
}