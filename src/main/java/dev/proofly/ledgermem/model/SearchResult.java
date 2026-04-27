package dev.proofly.ledgermem.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SearchResult(List<SearchHit> hits) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchHit(String id, String content, double score) {}
}
