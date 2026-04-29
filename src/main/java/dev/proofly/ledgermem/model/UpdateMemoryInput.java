package dev.proofly.getmnemo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateMemoryInput(
        String content,
        Map<String, Object> metadata
) {}
