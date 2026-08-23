# W05E Java partial-receiver service-profile remediation

## Decision

The W05E Java service is a partial receiver deployment. Its production database
is intentionally constrained to the Independent Board attribution schema and
does not contain `wc_webhook_url` or `wc_webhook_delivery`. The legacy webhook
delivery reconciliation scheduler therefore has no valid data surface in this
profile and must be disabled with exactly one non-secret environment setting:

```text
FBSIR_WEBHOOK_SWEEP_ENABLED=false
```

This is a service-profile correction, not an application release. It must not
switch `/opt/fbsir/admin/current`, change the active JAR, write the database,
restart or modify `fbss-phase1.service`, alter Node files, touch connector
packages, or promote product credit.

## Truth and safety boundary

The worker binds all of the following before a write:

- target `api2.u3w.com`, local machine-id digest and `fbsir-admin.service`;
- active symlink target, active JAR SHA-256, source manifest SHA-256 and exact
  active source commit `5d0769c2dad843b1c800c62cab8d9a5a91631b40`;
- `/etc/u3w/fbsir-admin.env` physical SHA-256, line-ending style and target-key
  occurrence count;
- the exact preimage of `30-w05-replay.conf`, when present;
- a read-only MySQL snapshot scoped to applied `public_init_043` and
  `public_init_044`, absence of both webhook tables, and zero authoritative
  product-credit rows;
- the Node current target and `fbss-phase1.service` process snapshot.

The replay drop-in is removed only when its exact hash still matches the plan,
its syntax is exactly the expected `EnvironmentFile` form, its referenced file
is inside the active release, and its `NOT_AFTER` instant is expired. A missing
drop-in stays missing; a non-expired drop-in stays byte-identical.

## Transaction sequence

The worker has four explicit modes:

1. `plan` performs read-only inspection and emits a canonical plan. It does not
   authorize a change.
2. `apply` requires an unexpired, root-owned, mode-0600, single-use
   authorization bound to the plan SHA-256, host identity and unit. It backs up
   exact preimages under a mode-0700 directory with mode-0600 files, changes
   only the allowed key, conditionally removes the expired replay drop-in,
   executes one daemon reload and at most one forward Java restart, then runs
   all postconditions.
   The authorization is consumed after preflight and backups but immediately
   before the first write. If the key is already exactly `false` and no expired
   drop-in needs removal, apply is an idempotent no-op and performs no restart.
   A real mutating CLI run enforces a 65–120 second post-action journal window
   (70 seconds by default), so the one-minute sweep cadence cannot be bypassed
   with a zero-duration production check.
3. `verify` is read-only and checks an apply receipt against current state.
4. `rollback` requires its own single-use authorization. Automatic rollback is
   attempted whenever a post-write apply condition fails. Rollback restores
   byte-exact preimages, executes one daemon reload and at most one rollback
   Java restart, and never changes the active application symlink or database.

The worker redacts exception detail from machine receipts. Environment values,
database passwords and process environment are never printed.

## Command shapes

These commands are documentation only. This implementation revision does not
run them against production.

```bash
sudo python3 scripts/u3w-w05e-service-profile-remediation.py plan \
  --expected-machine-id-sha256 <sha256> --output /root/w05e-profile-plan.json

sudo python3 scripts/u3w-w05e-service-profile-remediation.py apply \
  --plan /root/w05e-profile-plan.json \
  --authorization /root/w05e-profile-apply-authorization.json \
  --execute-production

sudo python3 scripts/u3w-w05e-service-profile-remediation.py verify \
  --plan /root/w05e-profile-plan.json \
  --receipt /opt/fbsir/admin/state/w05e-service-profile-remediation/receipts/<id>.apply.json

sudo python3 scripts/u3w-w05e-service-profile-remediation.py rollback \
  --plan /root/w05e-profile-plan.json \
  --receipt /opt/fbsir/admin/state/w05e-service-profile-remediation/receipts/<id>.apply.json \
  --authorization /root/w05e-profile-rollback-authorization.json \
  --execute-production
```

Authorization schema:

```json
{
  "schemaVersion": "fbsir.w05eServiceProfileRemediationAuthorization.v1",
  "authorizationId": "w05e-profile-apply-<unique-id>",
  "action": "apply",
  "planSha256": "<canonical plan file sha256>",
  "targetHost": "api2.u3w.com",
  "targetUnit": "fbsir-admin.service",
  "expectedMachineIdSha256": "<sha256>",
  "issuedAt": "2026-08-23T00:00:00Z",
  "expiresAt": "2026-08-23T00:15:00Z",
  "singleUse": true,
  "approvedBy": "<human owner>",
  "status": "AUTHORIZED"
}
```

For rollback, set `action` to `rollback` and bind `planSha256` to the original
plan identified by the apply receipt. Authorizations are never reusable.

## Verification and rollback interpretation

A passing apply or verify receipt proves only that the scoped profile
transaction and its declared invariants passed at the receipt cutoff. It does
not prove connector adoption, natural invocation, same-binding attribution,
product credit, or business closure. Any preimage drift, duplicate target key,
mixed line endings, non-expired replay removal request, restart-budget breach,
Node drift, database drift, new webhook reconciliation error, service failure,
or credit-bearing row fails closed.

The local implementation gate is:

```powershell
python scripts/u3w_w05e_service_profile_remediation_test.py
python -m py_compile scripts/u3w-w05e-service-profile-remediation.py scripts/u3w_w05e_service_profile_remediation_test.py
```
