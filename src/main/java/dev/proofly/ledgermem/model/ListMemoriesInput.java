package dev.proofly.ledgermem.model;

public record ListMemoriesInput(
        Integer limit,
        String cursor,
        String actorId
) {}
