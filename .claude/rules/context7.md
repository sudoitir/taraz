# Rule: Library & framework docs via context7 (never from memory)

Any question about a library, framework, SDK, API, or CLI tool — including ones you think you know (Spring Boot, Maven, JUnit 5, Java 21 concurrency, virtual threads, AssertJ, …) — is answered through the **context7 MCP**, not from training memory. Training data may be stale.

## Procedure

1. Call `resolve-library-id` with the official library name (`Spring Boot`, not `springboot`) and a query describing what you need.
2. Pick the best match by name similarity, source reputation, snippet coverage, and benchmark score.
3. Call `query-docs` with the resolved `/org/project` ID and a **single, specific** concept per call.
4. One question spanning multiple distinct concepts → one `query-docs` call per concept.

## Constraints

- **Max 3 calls per question** — if unresolved after 3, use the best available result and say so.
- Prefer context7 over web search for documentation.
- Never include secrets, credentials, personal data, or proprietary code in queries.
- If the user supplies a library ID in `/org/project` format directly, skip `resolve-library-id`.
