package dev.proofly.getmnemo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchInput(
        String query,
        Integer limit,
        String actorId
) {}
