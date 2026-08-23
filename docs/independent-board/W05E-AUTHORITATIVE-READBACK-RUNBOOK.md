# W05E Authoritative Readback Runbook

## Status

This runbook governs the implemented and locally validated revision of Slice A. The default-off Java route, strict protocol filters, bidirectional wire contract, isolated-MySQL zero-write gate and offline Ed25519 authority tooling now exist. This revision still does not authorize SQL, Nginx activation, deployment, production probes, Node publisher recovery, connector changes or product credit.

The source baseline is `U3W-AI/U3W-AI@5d0769c2dad843b1c800c62cab8d9a5a91631b40`. Work for this slice is isolated in `E:\820\w05e-u3w-readback` on branch `codex/w05e-authoritative-readback-20260823`.

The governing machine-readable contract is `.fbs-engineering/w05e-authoritative-readback-contract.json`.

## Outcome

Add a default-off, signed, per-event readback that can distinguish four outcomes from the current primary database without writing business state:

| Outcome | HTTP | Meaning |
|---|---:|---|
| `COMMITTED_EXACT` | 200 | eventId, receiptId and eventDigest resolve to the same committed row |
| `NOT_FOUND_AUTHORITATIVE` | 404 | both keys are absent in one successful primary read transaction |
| `IDENTITY_COLLISION` | 409 | a key resolves but the exact tuple does not match |
| `READBACK_UNAVAILABLE` | 503 | the receiver cannot authoritatively establish any of the first three outcomes |

This closes the information gap behind response loss, malformed 2xx receipts and over-retention recovery. It does not decide Node outbox recovery state; Slice B consumes this evidence later.

## Non-goals and hard boundaries

- No database migration or new SQL file. `public_init_044` remains the minimum schema and is retained on rollback.
- No change to the existing `POST /internal/independent-board/attribution/events` v1 contract.
- No change to the identity registry or historical synthetic replay behavior.
- No change to connector `26.7.2` or `26.8.20`, listed runtime, expert packages or MCP tools.
- No readback-driven natural traffic or product credit promotion.
- No receiver self-asserted compatibility claim may unlock the Node publisher.
- Java changes stay inside the attribution config/controller/receipt/service boundary, its tests and `application.yml`; SQL, existing W0.5 release runners and production files remain frozen until separately contracted.

## Proposed wire contract

### Feature gate

```text
property:    fbsir.independent-board.attribution.authoritative-readback-enabled
environment: FBSIR_INDEPENDENT_BOARD_ATTRIBUTION_AUTHORITATIVE_READBACK_ENABLED
default:     false
```

When disabled, the route is not mounted. Enabling requires the attribution master gate and observation writer, but can never enable product credit.

### Route and request

```text
POST /internal/independent-board/attribution/events/readback
Content-Type: application/json
Accept: application/json
```

The signed body is bounded to 16 KiB and contains only:

```json
{
  "schemaVersion": "fbsir.independentBoardAttributionReadbackRequest.v1",
  "eventId": "<64 lowercase hex>",
  "receiptId": "<64 lowercase hex>",
  "eventDigest": "<64 lowercase hex>",
  "issuedAt": "<RFC3339 UTC>",
  "expiresAt": "<RFC3339 UTC, at most 60 seconds after issuedAt>",
  "nonce": "<bounded random text>",
  "keyId": "<current or previous attribution key id>",
  "signature": "<HMAC-SHA-256 hex>"
}
```

Canonical fields bind constant `method=POST` and the exact route path before the listed body fields, using the domain tag `FBSIR_INDEPENDENT_BOARD_READBACK_V1`. Reusing the attribution keyring is allowed only with this separate domain. Maximum clock skew is 30 seconds; timestamps use UTC `Z` with zero to three fractional digits. Unknown, duplicate or trailing JSON values are rejected, and an invalid signature fails before any data lookup. The body is streamed through a 16 KiB-plus-one fence before Jackson binding, including HTTP/2 or unknown-length requests.

This route is read-only, so nonce persistence is forbidden. The nonce provides freshness and response correlation, not a durable anti-replay claim. A short TTL, signed request, signed response, response minimization and public deny form the replay/enumeration boundary.

The process additionally enforces a default global concurrency budget of four and a per-verified-key budget of 120 verified attempts per minute. An attempt consumes the key budget before competing for a concurrency lease, so congestion cannot bypass or amplify the per-key ceiling. It stores only key-level counters, never event/receipt/digest/nonce values. Exhaustion returns a signed `503 READBACK_UNAVAILABLE` with `Retry-After`; it does not replace listener, firewall or external rate-limit evidence.

Every verified 200/404/409/503 response is HMAC signed under the distinct domain `FBSIR_INDEPENDENT_BOARD_READBACK_RESPONSE_V1`. It binds the actual HTTP status, request canonical digest, request nonce hash, exact lookup tuple, authoritative flag, response validity window, receiver release, physical JAR SHA-256 and zero-credit flag. Response timestamps use canonical UTC `Z` with zero to three fractional digits, and every serialized decision response must remain at or below 16 KiB. Node must verify that signature and the independent authority receipt before changing any outbox state. An ordinary unsigned feature-off 404 is never authoritative.

Every response outside that verified four-status set, including unsigned 400/404/500/502/504 responses, transport failure, malformed JSON, expired evidence or signature failure, has exactly one downstream meaning: `UNKNOWN_RETRY`. Node must retain the outbox item and apply a bounded retry/backoff policy; it must not delete, acknowledge, dead-letter as a business decision, infer not-found, or promote product credit from such a response.

The response reuses the active event HMAC key only through this distinct domain. That proves transport integrity; it is not independent receiver identity. Independent identity comes only from the separately signed authority receipt. At startup, the configured JAR path must realpath-match the actual packaged Spring Boot `ApplicationHome` source and the bytes must match the returned JAR SHA-256; exploded classes cannot enable the route.

### Exact read algorithm

1. Validate method, content type, size, canonical text, TTL, keyId and signature.
2. Open one primary-datasource `REPEATABLE_READ` transaction, require the server transaction-read-only state to equal one, and keep MyBatis cache disabled.
3. Query the existing `selectEventByEventId` and `selectEventByReceiptId` mappings.
4. Return exact only if both keys identify the same row and all three request digests match.
5. Return authoritative not-found only if both lookups are absent in that successful transaction.
6. Return collision if either key exists but the tuple is not exact. Do not disclose which field collided.
7. Return unavailable for datasource, mapper or receiver-state failure. Do not convert exceptions to HTTP 200.

Every response is JSON, at most 16 KiB, and carries `Cache-Control: no-store, no-cache, must-revalidate, max-age=0`, `Pragma: no-cache`, and `Expires: 0`.

The isolated MySQL gate must also prove a write attempted inside the same readback transaction fails with MySQL error 1792. A Spring read-only hint without server read-only evidence is insufficient.

## Zero-write proof

The readback request must produce zero writes to:

- `fbs_board_attr_event_v1`;
- `fbs_board_attr_journey_v1`;
- authoritative product credit fields or ledgers;
- `u3w_schema_migration`;
- Redis, filesystem state, publisher outbox, Runtime journal or Business ledger.

Operational access logs may exist, but they must not contain event, receipt, tenant, binding, nonce, signature or payload identifiers.

The isolated MySQL shadow gate must compare before and after:

- event and journey row counts;
- journey head versions;
- event high watermark;
- every authoritative product credit bit;
- 043/044 migration receipt set.

All values must be identical for exact, not-found, collision, invalid signature and unavailable fixtures.

## Public deny

The Java signature check is mandatory even over loopback. Before enabling, effective Nginx configuration must explicitly deny the public alias:

```text
/prod-api/internal/independent-board/attribution/events/readback
```

There is no unsigned GET, query-string lookup or public enumeration mode. The response excludes tenant/binding fields, raw event content, database IDs, raw request nonces and internal exceptions. Its HMAC signature and signer key id are required protocol evidence, not secrets.

## Legacy POST compatibility

The existing ingress remains byte-semantic compatible:

- first append: HTTP 202 + `APPENDED_REPORT_ONLY`;
- exact replay: HTTP 200 + `IDEMPOTENT_REPLAY`, `idempotentReplay=true`;
- identity conflict: HTTP 409 with a stable generic reason;
- other validation: HTTP 400;
- persistence unavailable: real HTTP 503;
- unexpected runtime failure: real HTTP 500;
- no-store headers and POST-only protocol unchanged;
- event schema remains `fbsir.independentBoardAttributionEvent.v1`;
- historical synthetic replay remains digest-bound, time-bound, report-only and zero-credit.

## Authority receipt

Java returning HTTP 200 is not by itself an independently trusted service receipt. Before Node activation, a separate offline authority must sign and the activation verifier must validate `fbsir.independentBoardAuthoritativeReadbackAuthorityReceipt.v2` with Ed25519.

The private key remains outside API2, the Java receiver and the Node publisher; runtime and activation surfaces receive only the pinned public key and SHA-256 SPKI fingerprint. HMAC is retained only for request/response transport integrity and is forbidden as independent authority because an HMAC verifier can also forge receipts.

It must bind:

- pushed source commit and clean tree;
- two reproducible builds with identical JAR SHA-256;
- request/response contract digest;
- default-off configuration digest;
- isolated-MySQL zero-write receipt;
- Nginx public-deny readback;
- candidate manifest and shadow receipt;
- active symlink, systemd process and deployed JAR readback;
- receiver release/JAR values returned by the readback route;
- validity window, independent signer fingerprint and `productCreditEligible=false`.

The receipt expires within 24 hours and is invalidated by any release, JAR, route, Nginx, schema-contract or keyring change. The existing receiver self-asserted compatibility receipt remains record-only and cannot satisfy this gate.

## Gate model

### Current executable commands

The machine-readable contract is the canonical command surface. At this implementation revision it includes focused and full Maven regressions, legacy and readback Java boundaries, Java/Node golden-vector verification, Ed25519 authority-tool tests, implementation writable-boundary enforcement, the 44-step database-manifest check, the stable dual-MySQL zero-write command, release-worker tests and the clean reproducible-build command. Representative direct invocations are:

```powershell
mvn -q -f pom.xml -pl FBSir-admin -am test

node --test scripts/independent-board-java-boundary-contract.test.mjs

node --test scripts/w05e-readback-wire-contract.test.mjs scripts/w05e-readback-java-boundary.test.mjs

py -3 -m unittest discover -s scripts -p 'w05e_authoritative_readback_authority_test.py'

py -3 -B scripts/w05e-implementation-boundary-gate.py

pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/verify-database-manifest.ps1 -Json

pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/run-independent-board-mysql-transaction-it.ps1 -AllowDestructiveTest

python scripts/u3w_w05_receiver_release_test.py

pwsh -NoLogo -NoProfile -ExecutionPolicy Bypass -File scripts/verify-w05-reproducible-build.ps1
```

The MySQL command is test-database-only. Passing these commands proves the implementation boundary; it does not authorize W05E release because the clean pushed candidate, reproducible W05E JAR receipt, public deny, candidate/shadow/active-runtime evidence, current independent authority receipt and external four-cell receipt do not yet exist.

### Evidence-dependent and external release gates

The following remain explicit planned items and are not misrepresented as already passing repository commands:

- a current Ed25519 authority receipt bound to the pushed commit, reproducible JAR, public deny, isolated shadow and active runtime;
- the external API2 MCP-by-connector four-cell harness and receipt.

The local receipt gate must first become a stable release command; the external gate must first gain a stable owner harness. Both must be promoted into the committed release contract and required evidence before cutover. Until then, `productionReleaseAllowedAtThisRevision=false` is authoritative.

## MCP and connector compatibility

The Java readback route is internal and is not an MCP tool. Nevertheless the release requires an external API2 receipt for all four cells:

| MCP protocol | Connector package |
|---|---|
| `2025-11-25` | `26.7.2` |
| `2026-07-28` | `26.7.2` |
| `2025-11-25` | `26.8.20` |
| `2026-07-28` | `26.8.20` |

Acceptance is 4/4 HTTP 200, unchanged public tool names and core input schemas, zero `lebao_drop` visibility, zero metadata leak, zero negative Runtime/Business/journal writes, and no product credit promotion.

Connector package versions are not the Independent Board `listedManifestVersion` or `embeddedContractVersion`; the two version families must remain separate.

## Implementation and release sequence

1. Implement the default-off Java route and tests in this exclusive worktree; do not add SQL.
2. Revise the contract so all planned tests are real command-backed blockers.
3. Run focused, full, Java boundary, database manifest and test-only MySQL zero-write gates.
4. Produce two clean identical JAR builds from a pushed clean commit.
5. Run Java shadow with isolated MySQL and zero production business writes.
6. Install the exact public deny and capture its effective-config readback.
7. Cut over Java first under one forward restart and automatic rollback.
8. Materialize and independently verify the short-lived authority receipt.
9. Only then may Node Slice B consume the readback contract.
10. Run the external API2 four-cell gate and a fixed post-cutover observation window without promoting natural traffic or credit.

Rollback target is `/opt/fbsir/admin/releases/w05-receiver-5d0769c2dad8-20260823T074133Z`; `public_init_044` remains in place because W05E has no database migration. Maximum forward and rollback restart count is one each.

## Stop conditions

Stop or roll back on any source/JAR/manifest/active-target drift, missing or expired authority receipt, absent public deny, any business-state write, any legacy POST regression, any four-cell failure, any product credit or natural promotion, unexpected service restart, or inability to bind a readback response to the active receiver release and JAR.
