package dev.proofly.getmnemo.model;

public record ListMemoriesInput(
        Integer limit,
        String cursor,
        String actorId
) {}
