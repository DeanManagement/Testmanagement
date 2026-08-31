# PRD-028 — MCP stdio Bridge (Clients Without HTTP Transport)

| | |
|---|---|
| **Status** | 📝 Draft — **blocked on confirming the trigger, §2.1** |
| **Author** | Engineering (Claude) |
| **Created** | 2026-08-31 |
| **Priority** | P3 — a connectivity gap, not a capability one |
| **Target** | v2.4 |
| **Related** | PRD-025 (§2 made stdio a non-goal, §9 deferred it "if clients without HTTP transport turn out to matter in practice" — this is that clause being tested), PRD-027 (execution tools, and the descriptor rework that came out of the same investigation), PRD-019 (secrets hardening — why the key is not an argv parameter) |

---

## 1. Summary

The MCP server speaks streamable-HTTP only. A good deal of local-model tooling registers **stdio**
servers only: you give it a command to spawn, it talks JSON-RPC over that process's stdin and
stdout. Point such a client at an HTTPS URL and it registers nothing — no error, no tools, silently.

The model then sees a URL in its context, no tools in its tool list, and does the only thing left:
shells out to `curl`. That symptom is what started this, and it reads like a prompting problem right
up until you check whether the tools are in the list at all.

This PRD scopes a **~100-line stdlib-only Python bridge**: read JSON-RPC lines from stdin, POST each
to `/api/mcp`, write the response to stdout. It is small because PRD-025 §3.1 chose `STATELESS`
streamable-HTTP — there is no session to track, no reconnect logic, no long-poll to hold open. One
line in, one POST, one line out.

**It is not obviously worth building, and §3.1 argues both sides.** `mcp-remote` already does this
and costs zero code. The case for a first-party bridge rests on things this project has repeatedly
decided it cares about — air-gapped installs, no npm at runtime, no third-party code pulled on every
launch — and not on capability.

## 2. Goals & Non-Goals

**Goals**

- A stdio-only MCP client can use the server with no Node toolchain, no npm access and no network
  egress beyond the instance itself.
- One file, no dependencies, copyable into an air-gapped environment.
- Faithful pipe: it adds no behaviour. Every tool, error and schema is whatever the server said.
- The failure modes that make stdio bridges maddening — stray output on stdout, a hung read, a
  swallowed notification — are designed against explicitly, because each corrupts the protocol
  channel silently.

**Non-Goals**

- **OAuth.** API keys remain the auth story (PRD-025 §2). A bridge that negotiated OAuth would be a
  bigger program than the thing it bridges to.
- **Reconnection, retry, backoff, caching, tool filtering.** All of it belongs on one side or the
  other, not in the pipe. A bridge that retries turns one refused write into several.
- **Replacing the HTTP endpoint.** HTTP stays the primary transport; this is an adapter for clients
  that cannot reach it.
- **Shipping a packaged binary, an npm module, or a PyPI release.** A file in the repo, and
  optionally served by the instance (§3.4), is the whole distribution story.
- **Windows-first support.** It should work there and §4 covers the obvious traps, but it is tested
  on Linux and macOS.

### 2.1 The trigger is suspected, not confirmed — check this before building anything

PRD-025 §9 deferred stdio until clients without HTTP transport "turn out to matter in practice". The
evidence that they do is currently **one local model that shells out to curl**, and the diagnosis
that its client is stdio-only is a hypothesis I have not verified. The server itself was test-driven
end to end over HTTP and is fine: 28 tools listed, full execution loop, every guard firing.

So the first task is not code:

1. Ask the model to list its tools. If `get_project` and `search_test_cases` are there, stdio is not
   the problem and this PRD should be closed unbuilt.
2. If they are absent, identify the client and check whether it supports `type: "http"` MCP servers.
3. Try `mcp-remote` (§3.1, Option A). If that fixes it, the remaining question is only whether the
   npx dependency is acceptable — which is a much smaller question than this document.

Writing the PRD before the check is deliberate: it makes the cost visible, so the answer to "should
we?" is informed. Building before the check would not be.

## 3. Proposed Design

### 3.1 Options

**Option A — document `mcp-remote`, write nothing.** Already half-done in `MCP_SETUP.md`
troubleshooting. Costs nothing, works today.

```json
{ "mcpServers": { "testmanagement": {
  "command": "npx",
  "args": ["-y", "mcp-remote", "https://your-instance/api/mcp",
           "--header", "Authorization: Bearer tm_…"] } } }
```

Against it, and the reason this PRD exists at all:

- **`npx -y` fetches and executes third-party code from npm on every launch.** For a self-hosted
  test-management tool whose deployment story is explicitly air-gap-friendly (PRD-010, PRD-024), a
  connection method that requires live npm access and trusts a package's latest version is a poor
  default. It is a supply-chain dependency in the credential path — the process it launches is
  handed an API key.
- Needs a Node toolchain on every client machine.
- `mcp-remote` is built primarily for OAuth bridging; header auth is a secondary path.

**Option B — a first-party stdlib-only Python bridge.** One file, no `pip install`, no network
access beyond the instance. Python is present on virtually every machine that is running a local
model. ~100 lines, because the transport is stateless.

**Option C — a mode of the existing jar** (`java -jar testmanagement.jar --mcp-stdio`). Rejected:
it means putting the entire backend artifact on the client machine to act as a pipe, and it drags a
JVM startup into every client launch.

**Recommendation: B, with A documented alongside** as the zero-install option for anyone who does
not care about the npm dependency. B is small enough that maintaining both costs little, and the two
answer different constraints.

### 3.2 What the bridge does

The whole program, and the measurements that make it this short — all taken against a live instance
rather than assumed:

| Behaviour | Measured | Consequence for the bridge |
|---|---|---|
| Response content type | `application/json` | **No SSE parsing.** The `Accept` header offers `text/event-stream` and the server does not use it for these responses |
| `Accept: application/json` alone | **400** | Both types must be sent on every request, always |
| Notification (no `id`) | `202`, `Content-Length: 0` | Emit **nothing** on stdout. Parsing an empty body is the bug here |
| Session | none — `STATELESS` | No session id to carry, no `Mcp-Session-Id` handling, no reconnect |

So:

1. Read a line from stdin. MCP's stdio transport is newline-delimited JSON — messages must not
   contain embedded newlines, so a line is a message. (Not LSP's `Content-Length` framing; getting
   this wrong is the classic first bug.)
2. POST it verbatim to `/api/mcp` with `Content-Type: application/json`,
   `Accept: application/json, text/event-stream`, and the auth header.
3. If the response body is empty (a `202` from a notification), write nothing. Otherwise write the
   body as exactly one line to stdout, followed by a newline, and flush.
4. Everything else — diagnostics, connection errors, the startup banner if any — goes to **stderr**.
5. On a transport-level failure for a request that had an `id`, synthesise a JSON-RPC error response
   so the client sees a reply rather than hanging. A request that gets no answer at all is the worst
   outcome available: the client waits forever with no indication why.

That is the entire specification. It should stay that short; a bridge that grows features is a
bridge that grows bugs in a channel nobody can observe.

### 3.3 Auth and configuration

The key comes from the **environment**, not `argv`:

```json
{ "mcpServers": { "testmanagement": {
  "command": "python3",
  "args": ["/path/to/testmanagement-mcp-stdio.py"],
  "env": { "TESTMANAGEMENT_URL": "https://your-instance/api/mcp",
           "TESTMANAGEMENT_API_KEY": "tm_…" } } } }
```

Arguments are visible in `ps` to every user on the machine and land in shell history; an API key is
a credential and PRD-019 already set the standard for this. The bridge refuses to start — with a
message on stderr, naming both variables — rather than falling back to an argv key.

### 3.4 Distribution, and whether the instance should serve it

The file lives at `tools/testmanagement-mcp-stdio.py` in the repo.

**Additionally, and separably: serve it at `GET /api/mcp/bridge`**, unauthenticated, alongside the
existing descriptor route. The argument for it is good — someone with a local model has a URL and a
key, not a git checkout, and a bridge fetched from the instance is version-matched to the server by
construction:

```bash
curl -o testmanagement-mcp-stdio.py https://your-instance/api/mcp/bridge
```

The argument against is that it makes the instance a distributor of executable code. That is
mitigated but not erased by the file containing no secrets, being served over the same TLS
connection the client already trusts with its API key, and being identical to the copy in the repo —
but it deserves a decision rather than an assumption, so §7 makes it its own acceptance criterion
that can be dropped without dropping the bridge.

The discovery descriptor (reworked in PRD-027) gains a `stdioClientConfiguration` next to the
existing `clientConfiguration`, so an agent or human landing on `GET /api/mcp` sees both connection
shapes.

## 4. Edge Cases

- **Anything printed to stdout that is not a JSON-RPC message corrupts the stream.** No prints, no
  tracebacks, no warnings. A bare `print()` added later is a protocol bug that manifests as the
  client silently disconnecting. Worth a comment in the file saying so.
- **A response containing a newline** would break the one-message-per-line contract. Server
  responses are compact JSON, but the bridge normalises rather than trusting it.
- **stdin closes** (client shuts down) → exit 0 quietly.
- **SIGINT/SIGTERM** → exit without a traceback on stderr, which some clients surface as a crash.
- **Instance unreachable / DNS failure / TLS error** → JSON-RPC error response for requests with an
  `id`, detail on stderr. Never a hang.
- **401 from a revoked or wrong key** → surfaced as a JSON-RPC error whose message says the key was
  rejected, not a generic HTTP failure. This is the single most likely misconfiguration.
- **Non-JSON line on stdin** → JSON-RPC parse error `-32700` if an id can be recovered, else stderr.
- **A batched JSON-RPC array** — the spec permits it. The bridge forwards it unchanged and returns
  whatever comes back; no special handling.
- **Very large responses** (`tools/list` is ~30 KB with 28 tools) → single line, no chunking needed,
  but the read must not assume a small buffer.
- **Windows:** `python` versus `python3` on PATH, and stdout in text mode translating `\n` to
  `\r\n`, which corrupts framing. Open stdout in binary or set the newline mode explicitly.
- **Python version:** target 3.9+, stdlib only (`urllib.request`, `json`, `sys`, `os`). No f-string
  `=`, no `match`, nothing needing 3.10+.

## 5. Testing

The bridge is a separate artifact from the Maven build, so it needs its own small test file
(`pytest`, or plain `unittest` to stay dependency-free in CI).

- **Framing:** one JSON-RPC line in → exactly one line out, valid JSON, `id` preserved.
- **Notification in → nothing on stdout.** The assertion is on byte count, because "no reply" and
  "empty reply" look the same until something parses it.
- **stdout purity:** a run that produces diagnostics has them all on stderr, and stdout parses as
  JSON line-for-line. This is the test that catches a stray `print()` in a year's time.
- **Headers:** both `Accept` types on every request — asserted against a stub server, since the real
  one answers `400` and that is a confusing way to discover it.
- **Transport failure** (stub refuses connection) → a JSON-RPC error response, not a hang and not a
  traceback.
- **401** → an error message naming the key.
- **Missing env vars** → refuses to start, non-zero exit, both variable names on stderr.
- **Integration, manual:** register it in a real stdio-only client, confirm the tools list and one
  round trip. This is the only test that proves the premise; §2.1 means it happens first.

## 6. Effort & Risk

- **Effort:** ~1 day. Bridge and its tests half a day; docs, the descriptor field and the optional
  served route the rest. The confirmation in §2.1 is minutes and comes first.
- **Risk:** Low, with one exception.
  - *The premise may be wrong* (§2.1). Mitigated by checking before building. This is the whole
    risk of the PRD.
  - *A second connection path to document and support.* Mitigated by the bridge being a pipe with no
    behaviour of its own: a bug is either in the client or the server, and the bridge is
    inspectable in one screen.
  - *Silent protocol corruption* from stray stdout is the one failure mode that is genuinely nasty
    to debug, because the client just stops talking. Mitigated by the purity test in §5 and a
    comment in the file.
  - *Serving executable code* (§3.4) — a deliberate decision, separable, and the acceptance criteria
    let it be dropped alone.

## 7. Acceptance Criteria

- [ ] **§2.1 answered first:** the client's tool list has been checked, and if the tools are absent,
      the client has been confirmed to lack HTTP transport support. If they are present, this PRD is
      closed unbuilt.
- [ ] A stdio-only MCP client configured with the bridge lists all 28 tools and completes a
      round trip, with no Node toolchain installed.
- [ ] The bridge runs on a machine with no npm access and no internet route except to the instance.
- [ ] Notifications produce no stdout output; stdout parses as JSON for every line of a session that
      also wrote diagnostics.
- [ ] A revoked key produces a JSON-RPC error naming the credential, not a hang.
- [ ] The instance unreachable mid-session produces an error response per pending request, not a
      hang.
- [ ] The API key is read from the environment; passing it as an argument is refused.
- [ ] `mcp-remote` remains documented as the zero-install alternative.
- [ ] *(Separable)* `GET /api/mcp/bridge` serves the same file the repo contains, and the discovery
      descriptor advertises a stdio client configuration.

## 8. Future Work

- **Retire it** if the local-model ecosystem converges on HTTP transport, which it is drifting
  toward. This is an adapter for a transitional gap and should be allowed to die.
- **A `--check` mode** that validates URL, key and reachability and prints a human-readable verdict
  to stderr, for the case where a client reports only "server failed to start".
- **OAuth**, only if PRD-025 §9's OAuth work lands — at which point the bridge either grows a token
  flow or is replaced by `mcp-remote`, which already has one.
