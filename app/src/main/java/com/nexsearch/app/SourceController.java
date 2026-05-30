package com.nexsearch.app;

import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.source.dto.SourceResponse;
import com.nexsearch.source.service.SourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @GetMapping
    public ApiResponse<List<SourceResponse>> getSources(
            @RequestParam(defaultValue = "false") boolean enabledOnly
    ) {
        List<SourceResponse> sources = enabledOnly
                ? sourceService.findEnabled()
                : sourceService.findAll();

        return ApiResponse.success(
                "Sources fetched successfully",
                sources
        );
    }

    @GetMapping("/{sourceKey}")
    public ApiResponse<SourceResponse> getSource(
            @PathVariable String sourceKey
    ) {
        SourceResponse source = sourceService.findBySourceKey(sourceKey);

        return ApiResponse.success(
                "Source fetched successfully",
                source
        );
    }
}