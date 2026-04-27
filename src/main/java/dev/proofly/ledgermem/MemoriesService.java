package dev.proofly.ledgermem;

import dev.proofly.ledgermem.model.AddMemoryInput;
import dev.proofly.ledgermem.model.ListMemoriesInput;
import dev.proofly.ledgermem.model.ListMemoriesResult;
import dev.proofly.ledgermem.model.Memory;
import dev.proofly.ledgermem.model.UpdateMemoryInput;

import java.io.IOException;
import java.util.Map;

/** Memory CRUD operations. */
public final class MemoriesService {

    private final LedgerMemClient client;

    MemoriesService(LedgerMemClient client) {
        this.client = client;
    }

    public Memory add(AddMemoryInput input) throws IOException, InterruptedException {
        return client.request("POST", "/v1/memories", Map.of(), input, Memory.class);
    }

    public Memory update(String id, UpdateMemoryInput input) throws IOException, InterruptedException {
        return client.request("PATCH", "/v1/memories/" + id, Map.of(), input, Memory.class);
    }

    public void delete(String id) throws IOException, InterruptedException {
        client.request("DELETE", "/v1/memories/" + id, Map.of(), null, Void.class);
    }

    public ListMemoriesResult list(ListMemoriesInput input) throws IOException, InterruptedException {
        Map<String, String> query = LedgerMemClient.queryMap();
        if (input.limit() != null) query.put("limit", input.limit().toString());
        if (input.cursor() != null) query.put("cursor", input.cursor());
        if (input.actorId() != null) query.put("actorId", input.actorId());
        return client.request("GET", "/v1/memories", query, null, ListMemoriesResult.class);
    }
}
