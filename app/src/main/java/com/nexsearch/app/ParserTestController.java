package com.nexsearch.app;

import com.nexsearch.app.dto.FetchPageRequest;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.fetcher.dto.PageFetchResult;
import com.nexsearch.fetcher.service.PageFetcherService;
import com.nexsearch.parser.dto.ParsedPage;
import com.nexsearch.parser.service.HtmlParserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parser")
public class ParserTestController {

    private final PageFetcherService pageFetcherService;
    private final HtmlParserService htmlParserService;

    public ParserTestController(
            PageFetcherService pageFetcherService,
            HtmlParserService htmlParserService
    ) {
        this.pageFetcherService = pageFetcherService;
        this.htmlParserService = htmlParserService;
    }

    @PostMapping("/test")
    public ApiResponse<ParsedPage> parse(@Valid @RequestBody FetchPageRequest request) {
        PageFetchResult fetchResult = pageFetcherService.fetch(request.url());

        ParsedPage parsedPage = htmlParserService.parse(
                fetchResult.html(),
                fetchResult.finalUrl()
        );

        return ApiResponse.success("Page parsed successfully", parsedPage);
    }
}