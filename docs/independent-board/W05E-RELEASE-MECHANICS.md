# W05E release mechanics

Status: implementation-only. Production release remains disabled.

The candidate `ops/api2/nginx/api2-fbss-w05e.conf` is byte-bound to the active
snippet prefix (`3591` bytes,
`21f7f6d12c94d2e7437bc5878a8fbd48073acad17b37cf6a8d8fb047180b5cd5`) and one
appended exact location (`3896` bytes,
`357cfadc900f576ce0f0ab8ddc93f8e79e79469d075bb0203ea012d398e5cb32`):

`/prod-api/internal/independent-board/attribution/events/readback`

The location is method-agnostic, returns `404`, adds `Cache-Control: no-store`
and contains no `proxy_pass`, `proxy_*`, `alias`, wildcard or filesystem
directive. It wins over the broad `/prod-api/` proxy while the Java route is
still internal, signed and default-off.

## Runner contract

The Linux runner is `scripts/w05e-nginx-public-deny.py`; its pure adapter and
negative tests are `scripts/w05e_nginx_public_deny_test.py`.

- `plan --output FILE` reads the target, candidate and host/service identity;
  it never writes the target, reloads Nginx or probes public traffic.
- `apply --plan PLAN --authorization AUTH --execute-production` requires Linux
  root and a one-time authorization whose `issuedAt`/`expiresAt` window is no
  longer than 15 minutes. It rechecks the exact preimage and machine/host,
  Node, Java and Nginx PID/target/restart identity before writing.
- The preimage backup is atomic, mode `0600`, under a mode `0700` state root.
  Candidate writes use a temporary file, file `fsync`, atomic rename and
  parent-directory `fsync`. `nginx -t` must pass before reload.
- After reload, the runner hashes the effective `nginx -T` dump without saving
  or printing its contents, validates one exact deny block, and probes public
  GET and POST. Both must be `404` with `no-store`.
- Any write/test/reload/effective-dump/probe/identity failure automatically
  restores the exact preimage, reruns `nginx -t`, and reloads Nginx. Node/Java
  are never restarted. `verify` is read-only. `rollback` requires a separate
  one-time rollback authorization and the candidate preimage check.

Receipts contain hashes, bounded statuses, PIDs and restart counters only;
they never contain the effective dump, response bodies, credentials or other
secret configuration values. Natural traffic and product credit remain false.

## Release gates

The contract exposes both the existing static gate and the runner gate:

```text
node --test scripts/w05e-nginx-public-deny-contract.test.mjs
python scripts/w05e_nginx_public_deny_test.py
```

Before release, one pushed clean source commit must bind to:

1. two byte-identical JAR builds;
2. isolated MySQL readback and zero-write receipts;
3. this exact Nginx candidate, target `nginx -t`, effective dump hash and
   public GET/POST probes;
4. candidate/shadow/rollback artifacts and one forward restart;
5. active symlink, MainPID and physical JAR readback;
6. a current offline-Ed25519 authority receipt;
7. the MCP connector-package four-cell receipt.

The target Nginx parse/readback is still an external gate. The versioned
build/shadow/release/rollback gate, current authority receipt and MCP four-cell
receipt are still pending. Therefore
`productionReleaseAllowedAtThisRevision=false`; this work does not install,
reload, cut traffic or promote credit.
