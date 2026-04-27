package dev.proofly.ledgermem.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchInput(
        String query,
        Integer limit,
        String actorId
) {}
