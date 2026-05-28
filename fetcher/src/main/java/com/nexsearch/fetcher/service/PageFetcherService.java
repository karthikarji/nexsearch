package com.nexsearch.fetcher.service;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.fetcher.dto.PageFetchResult;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PageFetcherService {

    private static final String USER_AGENT = "NexSearchBot/1.0";
    private static final int TIMEOUT_MS = 10_000;

    public PageFetchResult fetch(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute();

            return new PageFetchResult(
                    url,
                    response.url().toString(),
                    response.statusCode(),
                    response.contentType(),
                    response.body()
            );

        } catch (IOException ex) {
            throw new AppException(
                    ErrorCode.PAGE_FETCH_FAILED,
                    "Failed to fetch page: " + url
            );
        }
    }
}