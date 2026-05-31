package com.nexsearch.app;

import com.nexsearch.app.dto.WikipediaBatchImportRequest;
import com.nexsearch.app.dto.WikipediaImportFromDumpRequest;
import com.nexsearch.app.dto.WikipediaImportRequest;
import com.nexsearch.app.dto.WikipediaTitleDiscoveryRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.connectors.wikipedia.dto.WikipediaBatchImportResult;
import com.nexsearch.connectors.wikipedia.dto.WikipediaImportResult;
import com.nexsearch.connectors.wikipedia.dto.WikipediaTitleDiscoveryResult;
import com.nexsearch.connectors.wikipedia.service.WikipediaConnectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wikipedia")
public class WikipediaController {

    private final WikipediaConnectorService wikipediaConnectorService;

    public WikipediaController(WikipediaConnectorService wikipediaConnectorService) {
        this.wikipediaConnectorService = wikipediaConnectorService;
    }

    @PostMapping("/import")
    public ApiResponse<WikipediaImportResult> importArticle(
            @Valid @RequestBody WikipediaImportRequest request
    ) {
        WikipediaImportResult result = wikipediaConnectorService.importArticle(request.title());

        return ApiResponse.success(
                "Wikipedia article imported successfully",
                result
        );
    }

    @PostMapping("/import/batch")
    public ApiResponse<WikipediaBatchImportResult> importBatch(
            @Valid @RequestBody WikipediaBatchImportRequest request
    ) {
        WikipediaBatchImportResult result = wikipediaConnectorService.importArticles(
                request.titles(),
                request.delayMillis()
        );

        return ApiResponse.success(
                "Wikipedia batch import completed",
                result
        );
    }

    @PostMapping("/titles/discover")
    public ApiResponse<WikipediaTitleDiscoveryResult> discoverTitles(
            @Valid @RequestBody WikipediaTitleDiscoveryRequest request
    ) {
        WikipediaTitleDiscoveryResult result = wikipediaConnectorService.discoverTitles(
                request.maxTitles()
        );

        return ApiResponse.success(
                "Wikipedia titles discovered successfully",
                result
        );
    }

    @PostMapping("/import/from-titles-dump")
    public ApiResponse<WikipediaBatchImportResult> importFromTitlesDump(
            @Valid @RequestBody WikipediaImportFromDumpRequest request
    ) {
        WikipediaBatchImportResult result = wikipediaConnectorService.importFromTitlesDump(
                request.maxTitles(),
                request.delayMillis()
        );

        return ApiResponse.success(
                "Wikipedia import from titles dump completed",
                result
        );
    }
}