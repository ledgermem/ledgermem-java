package dev.proofly.getmnemo;

import dev.proofly.getmnemo.model.AddMemoryInput;
import dev.proofly.getmnemo.model.ListMemoriesInput;
import dev.proofly.getmnemo.model.ListMemoriesResult;
import dev.proofly.getmnemo.model.Memory;
import dev.proofly.getmnemo.model.UpdateMemoryInput;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Memory CRUD operations. */
public final class MemoriesService {

    private final MnemoClient client;

    MemoriesService(MnemoClient client) {
        this.client = client;
    }

    public Memory add(AddMemoryInput input) throws IOException, InterruptedException {
        return client.request("POST", "/v1/memories", Map.of(), input, Memory.class);
    }

    public Memory update(String id, UpdateMemoryInput input) throws IOException, InterruptedException {
        return client.request("PATCH", "/v1/memories/" + encodeId(id), Map.of(), input, Memory.class);
    }

    public void delete(String id) throws IOException, InterruptedException {
        client.request("DELETE", "/v1/memories/" + encodeId(id), Map.of(), null, Void.class);
    }

    private static String encodeId(String id) {
        // URLEncoder targets form-encoding (space -> "+"); for path segments
        // we replace "+" back to "%20" so caller-supplied ids cannot break the URL.
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public ListMemoriesResult list(ListMemoriesInput input) throws IOException, InterruptedException {
        Map<String, String> query = MnemoClient.queryMap();
        if (input.limit() != null) query.put("limit", input.limit().toString());
        if (input.cursor() != null) query.put("cursor", input.cursor());
        if (input.actorId() != null) query.put("actorId", input.actorId());
        return client.request("GET", "/v1/memories", query, null, ListMemoriesResult.class);
    }
}
