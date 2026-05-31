package com.nexsearch.app;

import com.nexsearch.app.dto.StackOverflowImportByTagRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.connectors.stackoverflow.dto.StackOverflowImportResult;
import com.nexsearch.connectors.stackoverflow.service.StackOverflowConnectorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stackoverflow")
public class StackOverflowController {

    private final StackOverflowConnectorService stackOverflowConnectorService;

    public StackOverflowController(StackOverflowConnectorService stackOverflowConnectorService) {
        this.stackOverflowConnectorService = stackOverflowConnectorService;
    }

    @PostMapping("/import/by-tag")
    public ApiResponse<StackOverflowImportResult> importByTag(
            @Valid @RequestBody StackOverflowImportByTagRequest request
    ) {
        StackOverflowImportResult result = stackOverflowConnectorService.importQuestionsByTag(
                request.tag(),
                request.pageSize()
        );

        return ApiResponse.success(
                "Stack Overflow questions imported successfully",
                result
        );
    }
}