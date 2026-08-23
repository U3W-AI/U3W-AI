# W0.5 Independent Board receiver contract-drift repair

## Objective

Restore the U3W receiver's exact compatibility with both the historical replay identity and the currently deployed API2 publisher identities, then let the durable publisher drain its four pending events without editing the outbox.

## Proof gap

The active receiver JAR was built from `13c203a5c42d8a8f977be8a9834c2ad147b62bb8` and accepts only `WORKBUDDY + 26.7.21/26.7.20`. The active publisher emits `WORKBUDDY` or `WORKBUDDYAI + 26.8.19/26.8.19`. The original MySQL 043 CHECKs also accept only the legacy tuple, and the original journey secondary unique key collides across listed versions even though `sameBindingKey` includes listed version. The receiver has a controller-scoped exception handler with no explicit ordering, while the application-wide handler returns an HTTP-200 envelope for runtime exceptions.

The four pending rows add a separate time-bound gap: all are current-version `SYNTHETIC` rows with `requestSource=UNKNOWN`, `terminal=UNKNOWN`, and ages of approximately 135.33–135.38 hours, beyond the ordinary 26-hour receiver retention. Identity repair alone would therefore convert them into permanent rejects rather than drain them.

## Truth surface

- Source: this isolated worktree, based on `origin/codex/w1-official-attribution-closure` at `5838f8a5eaa3116bcf734511d8f90cd03af2130d`.
- Inherited delta: the base is 16 commits ahead of production and contains runtime changes that must all be declared in the candidate manifest; it is not a four-file hotfix relative to production.
- Production build receipt: source commit `13c203a5c42d8a8f977be8a9834c2ad147b62bb8`.
- Active production JAR: SHA-256 `5b3582e2e4f97ab8a09845a1b6940e334cb2452a2c1970a01aeaf40dd1cc5cc1`.
- Active production release: `/opt/fbsir/admin/releases/w1a-release-13c203a5c42d-20260725T040706Z`.
- API2 publisher/outbox: read-only until the receiver is released; no record is manually rewritten, deleted, or reclassified.

## Minimal implementation

1. Use one packaged machine-readable identity registry and make the verifier load it; a cross-contract test binds the SQL successor to the same profile set.
2. Keep all common product, package, agent, marketplace, schema, and surface fields exact.
3. Accept only the legacy WorkBuddy tuple and the current WorkBuddy/WorkBuddyAI tuple.
4. Distinguish common official-identity rejection from host/version-profile rejection.
5. Add `public_init_044` without changing historical 043 bytes: replace two exact CHECKs, align the journey identity unique key with versioned same-binding derivation, and retain all legacy rows.
6. Replace `INSERT IGNORE` with duplicate-key-only no-op semantics so CHECK and data errors cannot become warnings.
7. Give ingress advice highest precedence; add a path-scoped protocol filter so 405/406/415 cannot reach the global HTTP-200 wrapper; map known conflicts to 409, persistence outages to 503, and unknown runtime faults to a non-secret 500.
8. Add a default-off historical recovery gate restricted to `SYNTHETIC`, a maximum 168-hour age, an absolute `notAfter`, and an exact event-digest allowlist. Natural, probe, diagnostic, unlisted and expired events remain rejected.
9. Test the real global-advice competition, 043→044 upgrade, duplicate apply, partial-DDL recovery, legacy preservation and three-profile/four-negative matrix on MySQL 8.0.30 and 8.4.8.
10. Keep successful append/replay receipt shapes and `productCreditEligible=false` unchanged.

Current local gates after the repairs:

- focused Java/HTTP/config/persistence tests: current suite passes with zero failures, errors or skips;
- API2 adapter/publisher tests: 51/51;
- database manifest: 44 public steps, 44 managed SQL files, zero errors;
- public_init_044 dual MySQL: PASS on 8.0.30 and 8.4.8, exact CHECK hash `f6f7901b4b5a8a21bbacad458430bf48fe146451d1b3cb24943e57961fd7fd06`;
- production mutations: zero.

The versioned release path is implemented separately from the historical W1 runner. `u3w-w05-receiver-release.py` validates the candidate manifest, stable pending-set digest, database absence, 043/044 state and single-use authorization; applies forward-only 044; materializes the expiring replay environment; switches the release atomically; waits for natural publisher drain; and restores the previous application/drop-in on failure while retaining 044. `u3w-w05-receiver-shadow.py` owns the loopback MySQL 8.0.45, Redis, candidate JAR and old-JAR-on-044 rehearsal. Neither worker writes the API2 outbox.

Production read-only preflight observed MySQL 8.0.45, exact 043 public/internal receipts, the two old enforced CHECKs, the four-column predecessor unique index, four stable pending digests, and zero receiver DB matches by event, receipt, event digest or journey. The digest values are not published; the sorted LF-delimited set is bound only by count `4` and SHA-256 `64ff2c3d8e9ab0e7a279530cfaf464a9862fe76ff01099fd79a2d73733ca98e3`.

The historical all-control-plane verifier remains non-green on pre-existing July receipt/source hash misbindings outside W0.5. It is deliberately not a W0.5 release blocker; the failure must remain visible and be repaired as a separate historical-evidence batch. No report may describe the whole historical control plane as green on the strength of W0.5 gates.

## Release sequence

```text
source, cross-runtime and dual-MySQL tests
-> clean source commit
-> push receipt and remote-tip equality
-> deterministic JAR and manifest
-> second clean build with identical JAR hash
-> isolated shadow with production-equivalent configuration
-> 043-to-044 schema shadow and backup/restore proof
-> old/current/double-host/negative/idempotency/expired-grace matrix
-> single-use cutover authorization
-> new immutable release directory
-> production 043 exact read-only preflight
-> forward-only 044 apply
-> atomic current symlink switch and one service restart
-> health and receipt verification
-> existing publisher natural retry drain
-> grace-expiry negative probe
-> fixed observation window and rollback-readiness receipt
```

## Stop condition

W0.5 closes only when the pushed source, clean-build candidate and active bytes are equal; 044 and all exact wire cases pass; the four digest-authorized rows drain naturally without dead-lettering or outbox edits; publisher status is `ready`; the grace has expired; identity mismatch disappears in the fixed window; and product credit remains false. Schema rollback is deliberately forward-only: an old JAR may be restored while 044 remains, because deleting newly accepted immutable rows to restore 043 would be destructive. Publisher circuit-breaking/readback is a separate subsequent candidate.
