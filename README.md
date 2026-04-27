# ledgermem-java

Official Java SDK for [LedgerMem](https://proofly.dev) — auditable memory for AI agents.

## Install

```xml
<dependency>
    <groupId>dev.proofly</groupId>
    <artifactId>ledgermem</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 17+.

## Quickstart

```java
import dev.proofly.ledgermem.LedgerMemClient;
import dev.proofly.ledgermem.model.*;
import java.util.Map;

LedgerMemClient client = LedgerMemClient.builder()
    .apiKey("lm_live_...")
    .workspaceId("ws_123")
    .build();

Memory mem = client.memories().add(
    new AddMemoryInput("User prefers dark mode.", null, null)
);

SearchResult result = client.search(
    new SearchInput("dark mode", 5, null)
);

System.out.println(mem.id() + " " + result.hits().size());
```

Configuration falls back to env vars: `LEDGERMEM_API_KEY`, `LEDGERMEM_WORKSPACE_ID`, `LEDGERMEM_API_URL`.

## API

| Method                          | Endpoint                  |
| ------------------------------- | ------------------------- |
| `client.search`                 | `POST /v1/search`         |
| `client.memories().add`         | `POST /v1/memories`       |
| `client.memories().update`      | `PATCH /v1/memories/:id`  |
| `client.memories().delete`      | `DELETE /v1/memories/:id` |
| `client.memories().list`        | `GET /v1/memories`        |

## License

MIT
