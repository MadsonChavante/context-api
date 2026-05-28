package com.contextapi.records;


public record HandleAnswerResult(String AnswerType, String response, Long nextContextId, String next) {}