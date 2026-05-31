package com.nexsearch.connectors.stackoverflow.dto;

import com.nexsearch.document.dto.DocumentResponse;

import java.util.List;

public record StackOverflowQuestionImportResult(
        Long questionId,
        String title,
        String link,
        Integer score,
        Integer answerCount,
        Boolean isAnswered,
        Long acceptedAnswerId,
        int importedAnswerCount,
        List<String> tags,
        List<StackOverflowAnswerResponse> answers,
        DocumentResponse document
) {
}