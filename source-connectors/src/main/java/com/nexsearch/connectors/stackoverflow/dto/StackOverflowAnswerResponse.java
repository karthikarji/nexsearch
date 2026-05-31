package com.nexsearch.connectors.stackoverflow.dto;

public record StackOverflowAnswerResponse(
        Long answerId,
        Long questionId,
        Boolean isAccepted,
        Integer score,
        Long creationDate
) {
}