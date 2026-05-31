package com.nexsearch.connectors.stackoverflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.connectors.core.SourceConnector;
import com.nexsearch.connectors.stackoverflow.dto.StackOverflowAnswerResponse;
import com.nexsearch.connectors.stackoverflow.dto.StackOverflowImportResult;
import com.nexsearch.connectors.stackoverflow.dto.StackOverflowQuestionImportResult;
import com.nexsearch.document.dto.DocumentResponse;
import com.nexsearch.document.dto.SaveDocumentCommand;
import com.nexsearch.document.service.DocumentService;
import com.nexsearch.parser.dto.ParsedPage;
import com.nexsearch.parser.service.HtmlParserService;
import com.nexsearch.source.dto.SourceResponse;
import com.nexsearch.source.service.SourceService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stack Overflow-specific connector.
 * <p>
 * Why this connector exists:
 * <p>
 * Stack Overflow is not a normal article website.
 * It contains structured Q&A data:
 * - question title
 * - question body
 * - tags
 * - score
 * - answer count
 * - accepted answer
 * - all answers
 * <p>
 * A generic HTML crawler would only extract visible text.
 * This connector uses the Stack Exchange API so we can preserve useful Q&A metadata.
 * <p>
 * Current PR scope:
 * - Import Stack Overflow questions by tag
 * - Fetch answers for those questions
 * - Convert question + answers into a searchable document
 * - Save using DocumentService
 * <p>
 * Future improvements:
 * - Store answers in separate normalized tables
 * - Import comments
 * - Import users/author metadata
 * - Add async jobs for larger imports
 * - Add checkpointing and retry support
 */
@Service
public class StackOverflowConnectorService implements SourceConnector {

    /*
     * This must match the source_key stored in source.sources table.
     *
     * In V5 seed migration we inserted:
     * source_key = stack-overflow
     */
    private static final String SOURCE_KEY = "stack-overflow";

    /*
     * Stack Exchange API base URL.
     *
     * We use this API instead of crawling Stack Overflow HTML pages directly.
     */
    private static final String STACK_EXCHANGE_API_BASE_URL = "https://api.stackexchange.com/2.3";

    /*
     * Stack Exchange API supports multiple sites.
     *
     * For Stack Overflow, the site parameter must be:
     * site=stackoverflow
     */
    private static final String SITE = "stackoverflow";

    /*
     * Identifies our application when calling external APIs.
     *
     * Later, we can improve this to:
     * NexSearchBot/1.0 (+https://nexsearch.com/bot-info)
     */
    private static final String USER_AGENT = "NexSearchBot/1.0";

    /*
     * Prevents API calls from waiting forever.
     */
    private static final int TIMEOUT_SECONDS = 10;

    /*
     * Because this endpoint is synchronous, we keep page size small.
     *
     * Example:
     * pageSize = 2 means:
     * fetch 2 questions for the given tag.
     */
    private static final int MAX_PAGE_SIZE = 20;

    /*
     * Answers are fetched separately.
     *
     * Stack Exchange API is paginated, so this controls how many answers we ask
     * for per API page.
     */
    private static final int ANSWERS_PAGE_SIZE = 100;

    /*
     * Safety limit for answer pagination.
     *
     * Example:
     * 100 answers per page * 3 pages = max 300 answers for one request.
     *
     * This protects our synchronous endpoint from running too long.
     */
    private static final int MAX_ANSWER_PAGES = 3;

    private final SourceService sourceService;
    private final HtmlParserService htmlParserService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    public StackOverflowConnectorService(
            SourceService sourceService,
            HtmlParserService htmlParserService,
            DocumentService documentService
    ) {
        this.sourceService = sourceService;
        this.htmlParserService = htmlParserService;
        this.documentService = documentService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns the source key handled by this connector.
     * <p>
     * This allows future connector registry logic to identify which connector
     * should handle which source.
     */
    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * Imports Stack Overflow questions by tag.
     * <p>
     * Example request:
     * tag = java
     * pageSize = 2
     * <p>
     * Flow:
     * 1. Validate request
     * 2. Read Stack Overflow source config from database
     * 3. Fetch questions from Stack Exchange API
     * 4. Fetch answers for those questions
     * 5. Convert each question + answers into a document
     * 6. Save using DocumentService
     */
    public StackOverflowImportResult importQuestionsByTag(String tag, Integer pageSize) {
        if (tag == null || tag.isBlank()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Stack Overflow tag is required"
            );
        }

        int safePageSize = pageSize == null ? 5 : pageSize;

        if (safePageSize < 1 || safePageSize > MAX_PAGE_SIZE) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "pageSize must be between 1 and " + MAX_PAGE_SIZE
            );
        }

        /*
         * Load source configuration from DB.
         *
         * This uses the source module we built earlier.
         * It prevents hardcoding all source settings inside connector code.
         */
        SourceResponse source = sourceService.findBySourceKey(SOURCE_KEY);

        if (!source.enabled()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Stack Overflow source is disabled"
            );
        }

        /*
         * Build API URL to fetch questions for the provided tag.
         *
         * Example:
         * https://api.stackexchange.com/2.3/questions?...&tagged=java
         */
        String apiUrl = buildQuestionsByTagUrl(tag.trim(), safePageSize);

        /*
         * Fetch question data first.
         *
         * This gives us question title, body, tags, score, answer count,
         * accepted answer id, etc.
         */
        List<StackOverflowApiQuestion> apiQuestions = fetchQuestions(apiUrl);

        /*
         * Fetch answers separately for all fetched question IDs.
         *
         * Important:
         * This fetches all answers returned by the API pages we read,
         * not only the accepted / green-tick answer.
         */
        Map<Long, List<StackOverflowApiAnswer>> answersByQuestionId =
                fetchAnswersForQuestions(apiQuestions);

        /*
         * Save each question as a NexSearch document.
         *
         * The document text includes:
         * - question title
         * - question body
         * - answer bodies
         * - tags
         * - score/answer metadata
         */
        List<StackOverflowQuestionImportResult> importedQuestions = apiQuestions.stream()
                .map(question -> saveQuestionAsDocument(
                        question,
                        answersByQuestionId.getOrDefault(question.questionId(), List.of())
                ))
                .toList();

        return new StackOverflowImportResult(
                tag,
                safePageSize,
                importedQuestions.size(),
                importedQuestions
        );
    }

    /**
     * Calls the Stack Exchange API to fetch questions.
     */
    private List<StackOverflowApiQuestion> fetchQuestions(String apiUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new AppException(
                        ErrorCode.STACK_OVERFLOW_FETCH_FAILED,
                        "Failed to fetch Stack Overflow questions with status: " + response.statusCode()
                );
            }

            return parseQuestionsResponse(response.body());

        } catch (AppException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new AppException(
                    ErrorCode.STACK_OVERFLOW_FETCH_FAILED,
                    "Failed to fetch Stack Overflow questions"
            );
        }
    }

    /**
     * Fetches answers for multiple questions.
     * <p>
     * Stack Exchange API accepts semicolon-separated question IDs:
     * <p>
     * /questions/123;456;789/answers
     * <p>
     * We group the answers by questionId so later each question can be saved
     * with its own answer list.
     */
    private Map<Long, List<StackOverflowApiAnswer>> fetchAnswersForQuestions(
            List<StackOverflowApiQuestion> questions
    ) {
        if (questions == null || questions.isEmpty()) {
            return Map.of();
        }

        List<Long> questionIds = questions.stream()
                .map(StackOverflowApiQuestion::questionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (questionIds.isEmpty()) {
            return Map.of();
        }

        List<StackOverflowApiAnswer> allAnswers = new ArrayList<>();

        int page = 1;
        boolean hasMore = true;

        /*
         * Read answer pages with a safety limit.
         *
         * We stop when:
         * - API says has_more = false
         * - or we reach MAX_ANSWER_PAGES
         */
        while (hasMore && page <= MAX_ANSWER_PAGES) {
            String apiUrl = buildAnswersByQuestionIdsUrl(questionIds, page);

            StackOverflowAnswersPage answersPage = fetchAnswers(apiUrl);

            allAnswers.addAll(answersPage.answers());
            hasMore = answersPage.hasMore();
            page++;
        }

        /*
         * Convert:
         * List<Answer>
         *
         * into:
         * Map<QuestionId, List<Answer>>
         */
        return allAnswers.stream()
                .collect(Collectors.groupingBy(StackOverflowApiAnswer::questionId));
    }

    /**
     * Calls Stack Exchange API to fetch answers.
     */
    private StackOverflowAnswersPage fetchAnswers(String apiUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new AppException(
                        ErrorCode.STACK_OVERFLOW_FETCH_FAILED,
                        "Failed to fetch Stack Overflow answers with status: " + response.statusCode()
                );
            }

            return parseAnswersResponse(response.body());

        } catch (AppException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new AppException(
                    ErrorCode.STACK_OVERFLOW_FETCH_FAILED,
                    "Failed to fetch Stack Overflow answers"
            );
        }
    }

    /**
     * Parses question API response.
     * <p>
     * Expected JSON shape:
     * <p>
     * {
     * "items": [
     * {
     * "question_id": 123,
     * "accepted_answer_id": 456,
     * "title": "...",
     * "body": "<p>...</p>",
     * "link": "https://stackoverflow.com/questions/...",
     * "tags": ["java", "spring"],
     * "score": 10,
     * "answer_count": 2,
     * "is_answered": true
     * }
     * ]
     * }
     */
    private List<StackOverflowApiQuestion> parseQuestionsResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode items = root.path("items");

            if (!items.isArray()) {
                throw new AppException(
                        ErrorCode.STACK_OVERFLOW_PARSE_FAILED,
                        "Stack Overflow API response does not contain items array"
                );
            }

            List<StackOverflowApiQuestion> questions = new ArrayList<>();

            for (JsonNode item : items) {
                Long questionId = item.path("question_id").isNumber()
                        ? item.path("question_id").asLong()
                        : null;

                Long acceptedAnswerId = item.path("accepted_answer_id").isNumber()
                        ? item.path("accepted_answer_id").asLong()
                        : null;

                String title = item.path("title").asText("");
                String body = item.path("body").asText("");
                String link = item.path("link").asText("");

                Integer score = item.path("score").isNumber()
                        ? item.path("score").asInt()
                        : null;

                Integer answerCount = item.path("answer_count").isNumber()
                        ? item.path("answer_count").asInt()
                        : null;

                Boolean isAnswered = item.path("is_answered").isBoolean()
                        ? item.path("is_answered").asBoolean()
                        : null;

                List<String> tags = new ArrayList<>();
                JsonNode tagNodes = item.path("tags");

                if (tagNodes.isArray()) {
                    for (JsonNode tagNode : tagNodes) {
                        tags.add(tagNode.asText());
                    }
                }

                /*
                 * Skip invalid/incomplete records.
                 *
                 * We need at least:
                 * - questionId
                 * - title
                 * - link
                 */
                if (questionId == null || title.isBlank() || link.isBlank()) {
                    continue;
                }

                questions.add(new StackOverflowApiQuestion(
                        questionId,
                        acceptedAnswerId,
                        title,
                        body,
                        link,
                        score,
                        answerCount,
                        isAnswered,
                        tags
                ));
            }

            return questions;

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.STACK_OVERFLOW_PARSE_FAILED,
                    "Failed to parse Stack Overflow API response"
            );
        }
    }

    /**
     * Parses answer API response.
     * <p>
     * Expected JSON shape:
     * <p>
     * {
     * "items": [
     * {
     * "answer_id": 111,
     * "question_id": 123,
     * "is_accepted": true,
     * "score": 50,
     * "creation_date": 1234567890,
     * "body": "<p>Answer text...</p>"
     * }
     * ],
     * "has_more": false
     * }
     */
    private StackOverflowAnswersPage parseAnswersResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode items = root.path("items");

            if (!items.isArray()) {
                throw new AppException(
                        ErrorCode.STACK_OVERFLOW_PARSE_FAILED,
                        "Stack Overflow answers response does not contain items array"
                );
            }

            List<StackOverflowApiAnswer> answers = new ArrayList<>();

            for (JsonNode item : items) {
                Long answerId = item.path("answer_id").isNumber()
                        ? item.path("answer_id").asLong()
                        : null;

                Long questionId = item.path("question_id").isNumber()
                        ? item.path("question_id").asLong()
                        : null;

                Boolean isAccepted = item.path("is_accepted").isBoolean()
                        ? item.path("is_accepted").asBoolean()
                        : false;

                Integer score = item.path("score").isNumber()
                        ? item.path("score").asInt()
                        : null;

                Long creationDate = item.path("creation_date").isNumber()
                        ? item.path("creation_date").asLong()
                        : null;

                String body = item.path("body").asText("");

                if (answerId == null || questionId == null) {
                    continue;
                }

                answers.add(new StackOverflowApiAnswer(
                        answerId,
                        questionId,
                        isAccepted,
                        score,
                        creationDate,
                        body
                ));
            }

            boolean hasMore = root.path("has_more").isBoolean()
                    && root.path("has_more").asBoolean();

            return new StackOverflowAnswersPage(
                    answers,
                    hasMore
            );

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.STACK_OVERFLOW_PARSE_FAILED,
                    "Failed to parse Stack Overflow answers response"
            );
        }
    }

    /**
     * Saves one Stack Overflow question as a NexSearch document.
     * <p>
     * Important:
     * For this PR, we are not creating separate tables for questions/answers.
     * Instead, we convert question + answers into one searchable document.
     * <p>
     * Later, we can normalize Stack Overflow-specific data into separate tables.
     */
    private StackOverflowQuestionImportResult saveQuestionAsDocument(
            StackOverflowApiQuestion question,
            List<StackOverflowApiAnswer> answers
    ) {
        /*
         * Build synthetic HTML because our parser already knows how to parse HTML.
         *
         * This lets us reuse HtmlParserService instead of writing a completely
         * separate text extraction pipeline right now.
         */
        String syntheticHtml = buildSyntheticHtml(question, answers);

        ParsedPage parsedPage = htmlParserService.parse(
                syntheticHtml,
                question.link()
        );

        /*
         * Convert Stack Overflow data into generic SaveDocumentCommand.
         *
         * DocumentService does not need to know this came from Stack Overflow.
         */
        SaveDocumentCommand command = new SaveDocumentCommand(
                question.link(),
                question.link(),
                200,
                "text/html; charset=UTF-8",
                question.title(),
                parsedPage.visibleText(),
                parsedPage.headings(),
                parsedPage.links(),
                buildMetaDescription(question, answers),
                question.link(),
                "en"
        );

        DocumentResponse document = documentService.save(command);

        /*
         * Return answer metadata in the API response.
         *
         * We do not return full answer bodies here to avoid a huge API response.
         */
        List<StackOverflowAnswerResponse> answerResponses = answers.stream()
                .map(answer -> new StackOverflowAnswerResponse(
                        answer.answerId(),
                        answer.questionId(),
                        answer.isAccepted(),
                        answer.score(),
                        answer.creationDate()
                ))
                .toList();

        return new StackOverflowQuestionImportResult(
                question.questionId(),
                question.title(),
                question.link(),
                question.score(),
                question.answerCount(),
                question.isAnswered(),
                question.acceptedAnswerId(),
                answerResponses.size(),
                question.tags(),
                answerResponses,
                document
        );
    }

    /**
     * Builds API URL for tag-based question import.
     * <p>
     * filter=withbody is important because default Stack Exchange responses
     * may not include question body.
     */
    private String buildQuestionsByTagUrl(String tag, int pageSize) {
        String encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8);

        return STACK_EXCHANGE_API_BASE_URL
                + "/questions"
                + "?order=desc"
                + "&sort=votes"
                + "&site=" + SITE
                + "&tagged=" + encodedTag
                + "&pagesize=" + pageSize
                + "&filter=withbody";
    }

    /**
     * Builds answer API URL for multiple question IDs.
     * <p>
     * Example:
     * questionIds = [123, 456]
     * <p>
     * URL:
     * /questions/123;456/answers
     */
    private String buildAnswersByQuestionIdsUrl(List<Long> questionIds, int page) {
        String ids = questionIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(";"));

        return STACK_EXCHANGE_API_BASE_URL
                + "/questions/"
                + ids
                + "/answers"
                + "?order=desc"
                + "&sort=votes"
                + "&site=" + SITE
                + "&pagesize=" + ANSWERS_PAGE_SIZE
                + "&page=" + page
                + "&filter=withbody";
    }

    /**
     * Creates a small HTML document from Stack Overflow structured data.
     * <p>
     * Why synthetic HTML?
     * <p>
     * Our HtmlParserService already extracts:
     * - title
     * - visible text
     * - headings
     * - links
     * - meta description
     * <p>
     * By converting Stack Overflow data into HTML, we reuse the same parser.
     */
    private String buildSyntheticHtml(
            StackOverflowApiQuestion question,
            List<StackOverflowApiAnswer> answers
    ) {
        return """
                <html lang="en">
                  <head>
                    <title>%s</title>
                    <meta name="description" content="%s">
                    <link rel="canonical" href="%s">
                  </head>
                  <body>
                    <h1>%s</h1>
                    <section>
                      <h2>Question</h2>
                      %s
                    </section>
                    <section>
                      <h2>Answers</h2>
                      %s
                    </section>
                    <section>
                      <h2>Tags</h2>
                      <p>%s</p>
                    </section>
                    <section>
                      <h2>Question Metadata</h2>
                      <p>Score: %s</p>
                      <p>Answer count: %s</p>
                      <p>Answered: %s</p>
                      <p>Accepted answer id: %s</p>
                    </section>
                  </body>
                </html>
                """.formatted(
                escapeHtml(question.title()),
                escapeHtml(buildMetaDescription(question, answers)),
                question.link(),
                escapeHtml(question.title()),
                question.body() == null ? "" : question.body(),
                buildAnswersHtml(answers),
                escapeHtml(String.join(", ", question.tags())),
                question.score(),
                question.answerCount(),
                question.isAnswered(),
                question.acceptedAnswerId()
        );
    }

    /**
     * Builds HTML for all imported answers.
     * <p>
     * Accepted answer is marked in the heading, but all answers are included.
     */
    private String buildAnswersHtml(List<StackOverflowApiAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return "<p>No answers imported.</p>";
        }

        StringBuilder html = new StringBuilder();

        for (StackOverflowApiAnswer answer : answers) {
            html.append("<article>")
                    .append("<h3>Answer ")
                    .append(answer.answerId())
                    .append(Boolean.TRUE.equals(answer.isAccepted()) ? " Accepted" : "")
                    .append("</h3>")
                    .append("<p>Score: ")
                    .append(answer.score())
                    .append("</p>")
                    .append(answer.body() == null ? "" : answer.body())
                    .append("</article>");
        }

        return html.toString();
    }

    /**
     * Builds a short description useful for search result previews later.
     */
    private String buildMetaDescription(
            StackOverflowApiQuestion question,
            List<StackOverflowApiAnswer> answers
    ) {
        int importedAnswers = answers == null ? 0 : answers.size();

        return "Stack Overflow question tagged "
                + String.join(", ", question.tags())
                + ". Score: "
                + question.score()
                + ", answers imported: "
                + importedAnswers
                + ", accepted answer id: "
                + question.acceptedAnswerId();
    }

    /**
     * Minimal escaping for values we insert into synthetic HTML.
     * <p>
     * We escape our own values like title/tags.
     * Stack Overflow body fields are already HTML from the API,
     * so we do not escape question.body() or answer.body().
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Internal representation of one Stack Overflow question.
     * <p>
     * This is not exposed outside the connector.
     */
    private record StackOverflowApiQuestion(
            Long questionId,
            Long acceptedAnswerId,
            String title,
            String body,
            String link,
            Integer score,
            Integer answerCount,
            Boolean isAnswered,
            List<String> tags
    ) {
    }

    /**
     * Internal representation of one Stack Overflow answer.
     * <p>
     * Includes answer body so it can be added to searchable document text.
     */
    private record StackOverflowApiAnswer(
            Long answerId,
            Long questionId,
            Boolean isAccepted,
            Integer score,
            Long creationDate,
            String body
    ) {
    }

    /**
     * Internal representation of one page of answers from the API.
     * <p>
     * hasMore tells us whether another API page is available.
     */
    private record StackOverflowAnswersPage(
            List<StackOverflowApiAnswer> answers,
            boolean hasMore
    ) {
    }
}