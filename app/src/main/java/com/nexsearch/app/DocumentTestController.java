package com.nexsearch.app;

import com.nexsearch.app.dto.FetchPageRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.document.dto.DocumentResponse;
import com.nexsearch.document.dto.SaveDocumentCommand;
import com.nexsearch.document.service.DocumentService;
import com.nexsearch.fetcher.dto.PageFetchResult;
import com.nexsearch.fetcher.service.PageFetcherService;
import com.nexsearch.parser.dto.ParsedPage;
import com.nexsearch.parser.service.HtmlParserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentTestController {

    private final PageFetcherService pageFetcherService;
    private final HtmlParserService htmlParserService;
    private final DocumentService documentService;

    public DocumentTestController(
            PageFetcherService pageFetcherService,
            HtmlParserService htmlParserService,
            DocumentService documentService
    ) {
        this.pageFetcherService = pageFetcherService;
        this.htmlParserService = htmlParserService;
        this.documentService = documentService;
    }

    @PostMapping("/test-save")
    public ApiResponse<DocumentResponse> fetchParseAndSave(
            @Valid @RequestBody FetchPageRequest request
    ) {
        PageFetchResult fetchResult = pageFetcherService.fetch(request.url());

        ParsedPage parsedPage = htmlParserService.parse(
                fetchResult.html(),
                fetchResult.finalUrl()
        );

        SaveDocumentCommand command = new SaveDocumentCommand(
                fetchResult.requestedUrl(),
                fetchResult.finalUrl(),
                fetchResult.statusCode(),
                fetchResult.contentType(),
                parsedPage.title(),
                parsedPage.visibleText(),
                parsedPage.headings(),
                parsedPage.links(),
                parsedPage.metaDescription(),
                parsedPage.canonicalUrl(),
                parsedPage.language()
        );

        DocumentResponse savedDocument = documentService.save(command);

        return ApiResponse.success("Document saved successfully", savedDocument);
    }
}