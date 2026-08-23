# W05E release mechanics

Status: implementation-only, production release remains disabled.

The active API2 Nginx server includes `/etc/nginx/snippets/api2-fbss.conf`
before its broad `/prod-api/` Java proxy. The W05E candidate preserves the
current snippet bytes exactly and appends one exact location for the internal
readback path. It returns 404 with `Cache-Control: no-store` for every public
method and has no upstream. This can be installed before Java activation, but
this revision does not authorize installation or reload.

Before release, the target lane must bind one pushed clean source commit to:

1. two byte-identical JAR builds;
2. isolated MySQL readback and zero-write receipts;
3. this Nginx candidate, `nginx -t`, effective `nginx -T` hash and public
   GET/POST 404 probes;
4. candidate/shadow/rollback artifacts and one forward restart;
5. active symlink, MainPID and physical JAR readback;
6. a current offline-Ed25519 authority receipt;
7. the MCP protocol by connector-package four-cell receipt.

The Java route remains internal, signed and default-off. Nginx denial, Java
signature validation and the independent authority receipt are separate
layers. None of them promotes natural traffic or product credit.
