package dev.proofly.ledgermem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListMemoriesResult(
        List<Memory> data,
        String nextCursor
) {}
