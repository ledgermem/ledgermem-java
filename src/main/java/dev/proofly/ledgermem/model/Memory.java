package dev.proofly.getmnemo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Memory(
        String id,
        String content,
        Map<String, Object> metadata,
        String createdAt
) {}
