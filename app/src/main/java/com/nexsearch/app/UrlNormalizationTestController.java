package com.nexsearch.app;

import com.nexsearch.app.dto.NormalizeUrlRequest;
import com.nexsearch.app.dto.NormalizeUrlResponse;
import com.nexsearch.common.response.ApiResponse;
import com.nexsearch.common.util.UrlNormalizer;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/url")
public class UrlNormalizationTestController {

    @PostMapping("/normalize")
    public ApiResponse<NormalizeUrlResponse> normalize(
            @Valid @RequestBody NormalizeUrlRequest request
    ) {
        String normalizedUrl = UrlNormalizer.normalize(
                request.url(),
                request.baseUrl()
        );

        return ApiResponse.success(
                "URL normalized successfully",
                new NormalizeUrlResponse(
                        request.url(),
                        request.baseUrl(),
                        normalizedUrl
                )
        );
    }
}