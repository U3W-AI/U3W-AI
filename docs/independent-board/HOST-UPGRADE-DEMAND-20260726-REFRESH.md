# Host upgrade demand refresh — 2026-07-26

This is a read-only operational refresh. The listed `fbsir-eight-seat-board@26.7.21` package remains frozen; this document does not authorize package edits or a production credit/public-route change.

## Current finding

The fresh U3W and API2 reads show no proven official independent-board signal:

- U3W is healthy on release `w1a-release-13c203a5c42d-20260725T040706Z`; its ledger remains one probe journey and three probe events. No successful POST, append, receipt, natural event, or new identity mismatch was observed. The unauthenticated admin read remains `401` with `no-store`; the events probe is transport `200` with method-not-supported body.
- API2 is healthy on release `20260725-181736`; publisher backlog is zero, but listing receipt is `not_ready`, forwarding ACK is `observe_only_not_ready`, official `26.7.21` hits are zero, and the 24-hour decision board has natural `0` with eligible natural `0`.
- The two service observations are separate read-only windows. They do not prove a cross-service same-binding join. Keep product credit and public route disabled.

Evidence: `reports/independent-board/w1a-live-signal-refresh-20260725T2358Z.json`.

## Minimum next-host capability

The next WorkBuddy host release must, for the official listed entry, emit or forward only the following bounded fields to API2:

1. Exact listed identity: product/service/package `fbsir-eight-seat-board`, expert `board-convener`, marketplace `experts`, listed version `26.7.21`, host type `WorkBuddy`, and official channel.
2. A server-issued or server-verified `serverBindingId`; client claims, prompt text, cookies, tokens, and raw subject identifiers are not accepted as authority.
3. Route dimensions required by the six-dimensional ledger: product, version, channel, terminal, intent, and mode, with unknown values explicit rather than guessed.
4. A signed listing receipt and a v2 forwarding ACK bound to the same server binding, challenge id/nonce, request digest, listing-receipt digest, route-profile digest, issue/expiry window, and key id. The service must return a stable `hostForwardingSkippedReason` when forwarding cannot occur.
5. A real-entry sequence that can be observed without package self-instrumentation: entry observed, intent classified, first value completed, and subsequent use, each durable and idempotent.

## Acceptance gate

One real official WorkBuddy session must produce a trusted listing receipt, a trusted forwarding ACK, API2 publisher delivery, and the U3W three-event chain on the same `serverBindingId`; admin readback must expose the six dimensions with natural authority. Until all are present, the state remains record-only/unknown and credit/public promotion stays off.
