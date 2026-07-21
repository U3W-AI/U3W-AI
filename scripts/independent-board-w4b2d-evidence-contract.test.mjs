import assert from 'node:assert/strict';
import test from 'node:test';
import {
  TARGET, deriveServerBindingId, evaluateReport, normalizeApi2HostForwardingAck, normalizeEvent, sha256Canonical, signFixture,
} from './independent-board-w4b2d-evidence-contract.mjs';

const keyring = { receipt: { k1: 'receipt-secret-for-local-fixture-123' }, snapshot: { s1: 'snapshot-secret-for-local-fixture-456' } };
const now = Date.parse('2026-07-21T02:00:00.000Z');
const tenantSubjectDigest = 'a'.repeat(64);
const binding = deriveServerBindingId({ tenantSubjectDigest, hostSessionId: 'host-session-1' }, 'binding-secret-for-local-fixture-789');
const challengeId = 'b'.repeat(64);
const baseEvent = (stage, sequence, receiptId) => ({
  eventId: (String(sequence).padStart(2, '0') + 'c'.repeat(62)), receiptId, stage, outcome: 'success',
  productId: TARGET.productId, productVersion: TARGET.productVersion, entrySurface: 'official_entry',
  channelTrack: 'workbuddy_official', serverBindingId: binding, challengeId,
  observedAt: `2026-07-20T0${sequence}:00:00.000Z`, sequence, sampleCount: 1,
  terminalId: 'terminal-a', clientVersion: '1.29.0', tenantSubjectDigest,
});
const receipts = TARGET.stages.map((stage, i) => signFixture({
  schema: TARGET.schema, receiptId: (String(i + 1).padStart(2, '0') + 'd'.repeat(62)), nonce: (String(i + 1).padStart(2, '0') + 'e'.repeat(62)),
  challengeId, serverBindingId: binding, productId: TARGET.productId, productVersion: TARGET.productVersion,
  entrySurface: 'official_entry', channelTrack: 'workbuddy_official', stage, issuer: 'api2.u3w.com', audience: 'independent-board-attribution', kid: 'k1',
  createdAt: '2026-07-20T01:00:00.000Z', expiresAt: '2026-07-22T01:00:00.000Z', sequence: i + 1,
}, keyring, 'receipt'));
const events = receipts.map((r, i) => baseEvent(r.stage, i + 1, r.receiptId));
const normalized = events.map(normalizeEvent).sort((a, b) => `${a.observedAt}|${a.sequence}|${a.eventId}`.localeCompare(`${b.observedAt}|${b.sequence}|${b.eventId}`));
const snapshot = signFixture({
  schema: TARGET.snapshotSchema, snapshotId: 'f'.repeat(64), status: 'SEALED_REPORT_ONLY', productId: TARGET.productId,
  productVersion: TARGET.productVersion, windowStart: '2026-07-20T00:00:00.000Z', windowEnd: '2026-07-21T00:00:00.000Z',
  retentionUntil: '2026-07-22T03:00:00.000Z', watermark: '2026-07-21T00:00:00.000Z', highWatermark: '2026-07-21T00:00:00.000Z',
  rowCount: normalized.length, parseErrorCount: 0, gapCount: 0, invalidCount: 0, runtimeRelease: 'api2-runtime-cleanroom-1', embeddedRelease: 'host-26.7.20-immutable',
  canonicalizationVersion: 'jcs-lite-v1', eventDigest: sha256Canonical(normalized), generatedAt: '2026-07-21T01:00:00.000Z', asOf: '2026-07-21T01:00:00.000Z', kid: 's1',
}, keyring, 'snapshot');

test('valid exact chain is report-only and credit remains zero', () => {
  const report = evaluateReport({ receipts, events, snapshot }, { keyring, now, expectedRuntimeRelease: 'api2-runtime-cleanroom-1', expectedEmbeddedRelease: 'host-26.7.20-immutable' });
  assert.equal(report.status, 'REPORT_ONLY_CANDIDATE'); assert.equal(report.canPromote, false); assert.equal(report.authoritativeProductCredit, 0); assert.equal(report.denominatorWeight, 0);
});
test('unsigned self-declared server_verified fields fail closed', () => {
  assert.throws(() => evaluateReport({ receipts: receipts.map(({ signature, ...r }) => r), events, snapshot }, { keyring, now }), /signature/);
});
test('receipt mutation, unknown key, expiry and replay fail', () => {
  const changed = receipts.map(r => ({ ...r })); changed[1].stage = 'closure';
  assert.throws(() => evaluateReport({ receipts: changed, events, snapshot }, { keyring, now }), /signature/);
  const unknown = receipts.map(r => ({ ...r })); unknown[0].kid = 'unknown';
  assert.throws(() => evaluateReport({ receipts: unknown, events, snapshot }, { keyring, now }), /key_unavailable/);
  const expired = receipts.map(r => ({ ...r })); expired[0].expiresAt = '2026-07-20T00:01:00.000Z';
  assert.throws(() => evaluateReport({ receipts: expired, events, snapshot }, { keyring, now }), /time_invalid/);
  assert.throws(() => evaluateReport({ receipts: [...receipts, receipts[0]], events, snapshot }, { keyring, now }), /replay/);
});
test('cross-binding, wrong product, non-official entry and sample amplification do not join', () => {
  const wrongBinding = events.map((e, i) => i === 2 ? { ...e, serverBindingId: '1'.repeat(64) } : e);
  assert.equal(evaluateReport({ receipts, events: wrongBinding, snapshot }, { keyring, now }).status, 'REPORT_ONLY_REJECTED');
  const wrongProduct = receipts.map(r => r === receipts[0] ? signFixture(Object.fromEntries(Object.entries({ ...r, productId: 'workbuddy_board_secretary_assistant' }).filter(([k]) => k !== 'signature')), keyring, 'receipt') : r);
  assert.throws(() => evaluateReport({ receipts: wrongProduct, events, snapshot }, { keyring, now }), /target_mismatch/);
  assert.throws(() => normalizeEvent({ ...events[0], entrySurface: 'my-experts' }), /gate_failed/);
  assert.throws(() => normalizeEvent({ ...events[0], sampleCount: 100 }), /gate_failed/);
});
test('snapshot is immutable evidence: digest, window, release, retention and quality are mandatory', () => {
  assert.throws(() => evaluateReport({ receipts, events: [{ ...events[0], outcome: 'tampered' }, ...events.slice(1)], snapshot }, { keyring, now }), /digest_mismatch/);
  for (const field of ['gapCount', 'parseErrorCount', 'invalidCount']) {
    const bad = signFixture({ ...snapshot, [field]: 1 }, keyring, 'snapshot');
    assert.throws(() => evaluateReport({ receipts, events, snapshot: bad }, { keyring, now }), /quality_gate_failed/);
  }
  const shortRetention = signFixture({ ...snapshot, retentionUntil: '2026-07-21T01:00:00.000Z' }, keyring, 'snapshot');
  assert.throws(() => evaluateReport({ receipts, events, snapshot: shortRetention }, { keyring, now }), /retention_invalid/);
  const drift = signFixture({ ...snapshot, runtimeRelease: 'forged-runtime' }, keyring, 'snapshot');
  assert.throws(() => evaluateReport({ receipts, events, snapshot: drift }, { keyring, now, expectedRuntimeRelease: 'api2-runtime-cleanroom-1' }), /release_mismatch/);
});
test('sensitive and unknown fields are rejected before evidence digest', () => {
  assert.throws(() => normalizeEvent({ ...events[0], authorization: 'Bearer secret' }), /sensitive/);
  assert.throws(() => normalizeEvent({ ...events[0], prompt: 'raw user prompt' }), /sensitive/);
  assert.throws(() => normalizeEvent({ ...events[0], arbitrary: 'value' }), /unknown_field/);
  assert.throws(() => normalizeEvent({ ...events[0], tenantSubjectDigest: 'short' }), /tenantSubjectDigest_invalid/);
});
test('API2 host-forwarding ack must use the live object contract and a registered exact product', () => {
  const row = {
    serverVerifiedHostForwardingAck: { schemaVersion: 'fbss.hostForwardingAckVerification.v1', verified: true, cryptographicallyVerified: true, serverVerificationState: 'verified', serverVerificationSource: 'server_dispatch_ack_hmac', challengeJoinState: 'finalized', ackId: 'ack-1', challengeId: 'challenge-1', requestDigest: 'digest-1' },
    trafficClassificationAuthority: 'server_verified_host_forwarding_ack', serverObservedHostForwardingEvidenceTrust: 'server_dispatch_ack_verified', trafficGroup: 'natural', trafficClass: 'host_forwarding_ack_verified',
    serverBindingId: binding, bindingIdentitySource: 'derived_identity_seed', productSignatureProductId: TARGET.productId, productSignatureStrength: 'strict_listed_product_signature', productSignatureDiagnosticOnly: false, productSignatureProductCreditCandidate: true,
  };
  assert.equal(normalizeApi2HostForwardingAck(row).productId, TARGET.productId);
  assert.throws(() => normalizeApi2HostForwardingAck({ ...row, serverVerifiedHostForwardingAck: true }), /ack_not_verified/);
  assert.throws(() => normalizeApi2HostForwardingAck({ ...row, productSignatureProductId: 'workbuddy_board_secretary_assistant' }), /target_not_registered/);
  assert.throws(() => normalizeApi2HostForwardingAck({ ...row, trafficClassificationAuthority: 'server_verified' }), /authority_mismatch/);
  assert.throws(() => normalizeApi2HostForwardingAck({ ...row, bindingIdentitySource: 'traceparent' }), /binding_not_server_derived/);
});
