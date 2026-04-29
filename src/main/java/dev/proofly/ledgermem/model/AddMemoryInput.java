package dev.proofly.getmnemo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddMemoryInput(
        String content,
        Map<String, Object> metadata,
        String actorId
) {}
