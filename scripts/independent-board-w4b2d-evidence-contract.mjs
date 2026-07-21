import crypto from 'node:crypto';

export const TARGET = Object.freeze({
  productId: 'fbsir-eight-seat-board',
  productVersion: '26.7.20',
  contractId: 'FBSIR_INDEPENDENT_BOARD_W4B2D',
  schema: 'fbs.independentBoard.attributionEvidence.v1',
  snapshotSchema: 'fbs.independentBoard.sealedSnapshot.v1',
  registrationSchema: 'fbs.independentBoard.productRegistration.v1',
  stages: Object.freeze(['whoami', 'scene_pack', 'consume', 'closure']),
});

const EVENT_KEYS = new Set([
  'eventId', 'receiptId', 'stage', 'outcome', 'productId', 'productVersion',
  'entrySurface', 'channelTrack', 'serverBindingId', 'challengeId', 'observedAt',
  'sequence', 'sampleCount', 'terminalId', 'clientVersion', 'tenantSubjectDigest',
]);
const SUSPICIOUS = /(authorization|cookie|token|password|secret|prompt|email|phone|mobile|subject|raw|content)/i;
const SENSITIVE_VALUE = /(?:^[^\s@]+@[^\s@]+\.[^\s@]+$|^\+?\d[\d\s-]{9,}$|^Bearer\s+)/i;
const RECEIPT_KEYS = new Set(['schema', 'receiptId', 'nonce', 'challengeId', 'serverBindingId', 'tenantSubjectDigest', 'productId', 'productVersion', 'entrySurface', 'channelTrack', 'stage', 'issuer', 'audience', 'kid', 'createdAt', 'expiresAt', 'sequence', 'signature']);
const API2_ACK_RECEIPT_KEYS = new Set(['schema', 'receiptId', 'nonce', 'ackId', 'challengeId', 'requestDigest', 'serverBindingId', 'tenantSubjectDigest', 'productId', 'productVersion', 'expertEntryId', 'packageId', 'entrySurface', 'issuer', 'audience', 'kid', 'createdAt', 'expiresAt', 'signature']);
const SNAPSHOT_KEYS = new Set(['schema', 'snapshotId', 'status', 'productId', 'productVersion', 'windowStart', 'windowEnd', 'retentionUntil', 'watermark', 'highWatermark', 'rowCount', 'parseErrorCount', 'gapCount', 'invalidCount', 'canonicalizationVersion', 'eventDigest', 'runtimeRelease', 'embeddedRelease', 'generatedAt', 'asOf', 'kid', 'signature']);

function fail(message) { throw new Error(`W4b2d_CONTRACT:${message}`); }
function isObj(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function assertString(value, name, max = 256) {
  const opaqueDigestField = /(?:^|_)(?:eventId|receiptId|challengeId|snapshotId|registrationId|ackId|nonce|serverBindingId)$|api2_service_receipt_(?:id|nonce|binding|tenant)$|Digest|Hash|signature/i.test(name);
  if (typeof value !== 'string' || value.length === 0 || value.length > max || /[\u0000-\u001f]/.test(value) || (SENSITIVE_VALUE.test(value) && !opaqueDigestField)) fail(`${name}_invalid_or_sensitive`);
  return value;
}
function assertHex(value, name, bytes = 32) {
  assertString(value, name, bytes * 2);
  if (!new RegExp(`^[a-f0-9]{${bytes * 2}}$`).test(value)) fail(`${name}_invalid`);
  return value;
}
function assertNumber(value, name) {
  if (typeof value !== 'number' || !Number.isFinite(value) || Object.is(value, -0)) fail(`${name}_invalid`);
  return value;
}
function canonical(value) {
  if (value === null || typeof value === 'string' || typeof value === 'boolean') return JSON.stringify(value);
  if (typeof value === 'number') { if (!Number.isFinite(value) || Object.is(value, -0)) fail('non_i_json_number'); return JSON.stringify(value); }
  if (Array.isArray(value)) return `[${value.map(canonical).join(',')}]`;
  if (isObj(value)) return `{${Object.keys(value).sort().map(k => `${JSON.stringify(k)}:${canonical(value[k])}`).join(',')}}`;
  fail('unsupported_value');
}
function digest(value) { return crypto.createHash('sha256').update(canonical(value)).digest('hex'); }
function mac(value, key) { return crypto.createHmac('sha256', key).update(canonical(value)).digest('hex'); }
function safeEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string' || left.length !== right.length) return false;
  return crypto.timingSafeEqual(Buffer.from(left), Buffer.from(right));
}
function key(keyring, kid, purpose) {
  const value = keyring?.[purpose]?.[kid];
  if (typeof value !== 'string' || value.length < 16) fail(`${purpose}_key_unavailable`);
  return value;
}
function signedBody(value) { const { signature, ...body } = value; return body; }
function assertAllowedKeys(value, allowed, name) {
  if (Object.keys(value).some(field => !allowed.has(field))) fail(`${name}_unknown_field`);
}
function assertBindingId(value, name) {
  assertString(value, name, 96);
  if (!(/^[a-f0-9]{64}$/.test(value) || /^srv_[A-Za-z0-9_-]{12}$/.test(value))) fail(`${name}_invalid`);
  return value;
}
function verifyMac(value, keyring, purpose) {
  const expected = mac(signedBody(value), key(keyring, value.kid, purpose));
  if (!safeEqual(expected, value.signature)) fail(`${purpose}_signature_invalid`);
}
const SAFE_DIGEST_KEYS = new Set(['tenantSubjectDigest', 'serverBindingId', 'eventId', 'receiptId', 'challengeId', 'snapshotId']);
function suspicious(value) {
  if (typeof value === 'string') return SUSPICIOUS.test(value);
  if (Array.isArray(value)) return value.some(suspicious);
  if (isObj(value)) return Object.entries(value).some(([k, v]) => (SUSPICIOUS.test(k) && !SAFE_DIGEST_KEYS.has(k)) || suspicious(v));
  return false;
}

export function normalizeEvent(input) {
  if (!isObj(input) || suspicious(input)) fail('event_sensitive_or_not_object');
  const keys = Object.keys(input);
  if (keys.some(k => !EVENT_KEYS.has(k))) fail('event_unknown_field');
  for (const required of ['eventId', 'receiptId', 'stage', 'outcome', 'productId', 'productVersion', 'entrySurface', 'channelTrack', 'serverBindingId', 'challengeId', 'observedAt', 'sequence', 'sampleCount', 'tenantSubjectDigest']) {
    if (!(required in input)) fail(`event_${required}_missing`);
  }
  assertHex(input.eventId, 'eventId'); assertHex(input.receiptId, 'receiptId');
  assertBindingId(input.serverBindingId, 'serverBindingId'); assertHex(input.challengeId, 'challengeId');
  assertString(input.stage, 'stage', 32); assertString(input.outcome, 'outcome', 64);
  assertString(input.productId, 'productId', 96); assertString(input.productVersion, 'productVersion', 32);
  assertString(input.entrySurface, 'entrySurface', 64); assertString(input.channelTrack, 'channelTrack', 64);
  assertString(input.observedAt, 'observedAt', 40); assertNumber(input.sequence, 'sequence');
  assertNumber(input.sampleCount, 'sampleCount');
  if (!TARGET.stages.includes(input.stage) || input.productId !== TARGET.productId || input.productVersion !== TARGET.productVersion) fail('event_target_mismatch');
  if (input.entrySurface !== 'official_entry' || input.outcome !== 'success' || input.sampleCount !== 1 || !Number.isSafeInteger(input.sequence) || input.sequence < 1) fail('event_gate_failed');
  assertHex(input.tenantSubjectDigest, 'tenantSubjectDigest');
  if (input.terminalId !== undefined) assertString(input.terminalId, 'terminalId', 96);
  if (input.clientVersion !== undefined) assertString(input.clientVersion, 'clientVersion', 64);
  const observedMs = Date.parse(input.observedAt);
  if (!Number.isFinite(observedMs)) fail('event_observedAt_invalid');
  return Object.freeze({ ...input });
}

export function deriveServerBindingId({ tenantSubjectDigest, productId = TARGET.productId, productVersion = TARGET.productVersion, hostSessionId, keyVersion = 'v1' }, bindingKey) {
  assertHex(tenantSubjectDigest, 'tenantSubjectDigest'); assertString(hostSessionId, 'hostSessionId', 128);
  assertString(keyVersion, 'keyVersion', 32);
  return mac({ domain: 'fbs.w4b2d.binding.v1', tenantSubjectDigest, productId, productVersion, hostSessionId, keyVersion }, bindingKey);
}

// API2 现行 host-forwarding 验证结果适配器；不把 boolean 或旧别名当作回执。
export function normalizeApi2HostForwardingAck(row) {
  if (!isObj(row)) fail('api2_row_invalid');
  const ack = row.serverVerifiedHostForwardingAck ?? row.opsEvidence?.transport?.serverVerifiedHostForwardingAck;
  if (!isObj(ack) || ack.schemaVersion !== 'fbss.hostForwardingAckVerification.v1' || ack.verified !== true || ack.cryptographicallyVerified !== true || ack.serverVerificationState !== 'verified' || ack.serverVerificationSource !== 'server_dispatch_ack_hmac' || ack.challengeJoinState !== 'finalized') fail('api2_ack_not_verified');
  for (const field of ['ackId', 'challengeId', 'requestDigest']) assertString(ack[field], `api2_ack_${field}`, 256);
  const authority = row.trafficClassificationAuthority ?? row.opsEvidence?.transport?.trafficClassificationAuthority;
  const trust = row.serverObservedHostForwardingEvidenceTrust ?? row.opsEvidence?.transport?.serverObservedHostForwardingEvidenceTrust;
  const group = row.trafficGroup ?? row.sourceTrafficGroup;
  const trafficClass = row.trafficClass ?? row.sourceTrafficClass;
  if (authority !== 'server_verified_host_forwarding_ack' || trust !== 'server_dispatch_ack_verified' || group !== 'natural' || trafficClass !== 'host_forwarding_ack_verified') fail('api2_ack_authority_mismatch');
  const serverBindingId = row.serverBindingId;
  if (typeof serverBindingId !== 'string' || !(/^[a-f0-9]{64}$/.test(serverBindingId) || /^srv_[A-Za-z0-9_-]{12}$/.test(serverBindingId)) || !['derived_identity_seed', 'session_token_seeded', 'continuity_rescued', 'server_continuity_rescued'].includes(row.bindingIdentitySource)) fail('api2_binding_not_server_derived');
  if (ack.serverBindingId !== undefined && ack.serverBindingId !== serverBindingId) fail('api2_binding_ack_mismatch');
  if (row.idempotencyKey !== undefined && ack.coveredEventId !== undefined && row.idempotencyKey !== ack.coveredEventId) fail('api2_ack_event_mismatch');
  const productId = row.productSignatureProductId ?? row.productId;
  if (productId !== TARGET.productId || row.productSignatureStrength !== 'strict_listed_product_signature' || row.productSignatureDiagnosticOnly !== false || row.productSignatureProductCreditCandidate !== true) fail('api2_target_not_registered');
  return Object.freeze({ ackId: ack.ackId, challengeId: ack.challengeId, requestDigest: ack.requestDigest, serverBindingId, authority, trust, productId });
}

// 仅当 API2 注册表签发了独立回执时，才允许把 entry/package 身份接入产品归因。
// 当前线上尚无该回执，因此默认保持 PENDING_HOST_REGISTRATION。
export function verifyExactProductRegistration(registration, { keyring, now = Date.now() } = {}) {
  if (!isObj(registration) || registration.schema !== TARGET.registrationSchema || registration.status !== 'REGISTERED_EXACT') fail('registration_not_exact');
  for (const field of ['registrationId', 'productId', 'productVersion', 'hostType', 'connectorType', 'entrySurface', 'expertEntryId', 'packageId', 'issuer', 'kid', 'registeredAt', 'expiresAt', 'signature']) assertString(registration[field], `registration_${field}`, 256);
  assertHex(registration.registrationId, 'registrationId');
  const registered = Date.parse(registration.registeredAt); const expires = Date.parse(registration.expiresAt);
  if (!Number.isFinite(registered) || !Number.isFinite(expires) || expires <= registered || now < registered - 30000 || now >= expires) fail('registration_time_invalid');
  if (registration.productId !== TARGET.productId || registration.productVersion !== TARGET.productVersion || registration.hostType !== 'WORKBUDDY' || registration.connectorType !== 'fbs-connector' || registration.entrySurface !== 'official_entry') fail('registration_target_mismatch');
  if (registration.candidateEnabled !== false || registration.publicRouteEnabled !== false || registration.authoritativeCreditEnabled !== false) fail('registration_default_off_violation');
  verifyMac(registration, keyring, 'registration');
  return Object.freeze({ ...registration, registeredMs: registered, expiresMs: expires });
}

export function verifyApi2ServerVerifiedReceipt(receipt, { keyring, now = Date.now() } = {}) {
  if (!isObj(receipt) || receipt.schema !== 'fbs.api2.serverVerifiedHostForwardingReceipt.v1') fail('api2_service_receipt_schema_invalid');
  assertAllowedKeys(receipt, API2_ACK_RECEIPT_KEYS, 'api2_service_receipt');
  for (const field of ['receiptId', 'nonce', 'ackId', 'challengeId', 'requestDigest', 'serverBindingId', 'tenantSubjectDigest', 'productId', 'productVersion', 'expertEntryId', 'packageId', 'entrySurface', 'issuer', 'audience', 'kid', 'createdAt', 'expiresAt', 'signature']) assertString(receipt[field], `api2_service_receipt_${field}`, 256);
  assertHex(receipt.receiptId, 'api2_service_receipt_id'); assertHex(receipt.nonce, 'api2_service_receipt_nonce'); assertBindingId(receipt.serverBindingId, 'api2_service_receipt_binding'); assertHex(receipt.tenantSubjectDigest, 'api2_service_receipt_tenant');
  const created = Date.parse(receipt.createdAt); const expires = Date.parse(receipt.expiresAt);
  if (!Number.isFinite(created) || !Number.isFinite(expires) || expires <= created || now < created - 30000 || now >= expires) fail('api2_service_receipt_time_invalid');
  if (receipt.productId !== TARGET.productId || receipt.productVersion !== TARGET.productVersion || receipt.entrySurface !== 'official_entry') fail('api2_service_receipt_target_mismatch');
  verifyMac(receipt, keyring, 'api2Ack');
  return Object.freeze({ ...receipt, createdMs: created, expiresMs: expires });
}

export function normalizeRegisteredIndependentBoardRow(row, { registration, serverReceipt, keyring, now = Date.now() } = {}) {
  const verifiedRegistration = verifyExactProductRegistration(registration, { keyring, now });
  const verifiedServiceReceipt = verifyApi2ServerVerifiedReceipt(serverReceipt, { keyring, now });
  const ack = normalizeApi2HostForwardingAck(row);
  if (ack.ackId !== verifiedServiceReceipt.ackId || ack.challengeId !== verifiedServiceReceipt.challengeId || ack.requestDigest !== verifiedServiceReceipt.requestDigest || ack.serverBindingId !== verifiedServiceReceipt.serverBindingId) fail('api2_service_receipt_ack_mismatch');
  if (verifiedServiceReceipt.expertEntryId !== verifiedRegistration.expertEntryId || verifiedServiceReceipt.packageId !== verifiedRegistration.packageId || row.tenantSubjectDigest !== verifiedServiceReceipt.tenantSubjectDigest) fail('api2_service_receipt_registration_mismatch');
  if (row.productId !== verifiedRegistration.productId || row.productVersion !== verifiedRegistration.productVersion || row.expertEntryId !== verifiedRegistration.expertEntryId || row.packageId !== verifiedRegistration.packageId) fail('registered_row_identity_mismatch');
  return Object.freeze({ ...ack, registrationId: verifiedRegistration.registrationId, expertEntryId: verifiedRegistration.expertEntryId, packageId: verifiedRegistration.packageId });
}

export function verifyReceipt(receipt, { keyring, now = Date.now(), usedNonces = new Set(), usedReceiptIds = new Set() } = {}) {
  if (!isObj(receipt) || receipt.schema !== TARGET.schema) fail('receipt_schema_invalid');
  assertAllowedKeys(receipt, RECEIPT_KEYS, 'receipt');
  for (const field of ['receiptId', 'nonce', 'challengeId', 'serverBindingId', 'productId', 'productVersion', 'entrySurface', 'channelTrack', 'stage', 'issuer', 'audience', 'kid', 'createdAt', 'expiresAt', 'signature']) assertString(receipt[field], field, 256);
  assertHex(receipt.receiptId, 'receiptId'); assertHex(receipt.nonce, 'nonce'); assertHex(receipt.challengeId, 'challengeId'); assertBindingId(receipt.serverBindingId, 'serverBindingId'); assertHex(receipt.tenantSubjectDigest, 'tenantSubjectDigest');
  assertNumber(receipt.sequence, 'sequence');
  if (!Number.isSafeInteger(receipt.sequence) || receipt.sequence < 1 || receipt.sequence > TARGET.stages.length) fail('receipt_sequence_invalid');
  const created = Date.parse(receipt.createdAt); const expires = Date.parse(receipt.expiresAt);
  if (!Number.isFinite(created) || !Number.isFinite(expires) || expires <= created || now < created - 30000 || now >= expires) fail('receipt_time_invalid');
  if (receipt.productId !== TARGET.productId || receipt.productVersion !== TARGET.productVersion || receipt.entrySurface !== 'official_entry' || !TARGET.stages.includes(receipt.stage)) fail('receipt_target_mismatch');
  if (usedNonces.has(receipt.nonce) || usedReceiptIds.has(receipt.receiptId)) fail('receipt_replay');
  verifyMac(receipt, keyring, 'receipt');
  usedNonces.add(receipt.nonce); usedReceiptIds.add(receipt.receiptId);
  return Object.freeze({ ...receipt, createdMs: created, expiresMs: expires });
}

export function createReplayState() {
  return { usedNonces: new Set(), usedReceiptIds: new Set() };
}

export function verifySnapshot(snapshot, rows, { keyring, now = Date.now(), expectedRuntimeRelease, expectedEmbeddedRelease } = {}) {
  if (!isObj(snapshot) || snapshot.schema !== TARGET.snapshotSchema || snapshot.status !== 'SEALED_REPORT_ONLY') fail('snapshot_not_sealed');
  assertAllowedKeys(snapshot, SNAPSHOT_KEYS, 'snapshot');
  for (const field of ['snapshotId', 'productId', 'productVersion', 'windowStart', 'windowEnd', 'retentionUntil', 'watermark', 'runtimeRelease', 'embeddedRelease', 'canonicalizationVersion', 'eventDigest', 'generatedAt', 'asOf', 'kid', 'signature']) assertString(snapshot[field], field, 256);
  assertHex(snapshot.snapshotId, 'snapshotId'); assertHex(snapshot.eventDigest, 'eventDigest');
  for (const field of ['rowCount', 'parseErrorCount', 'gapCount', 'invalidCount', 'highWatermark']) assertNumber(snapshot[field], field);
  if (!['rowCount', 'parseErrorCount', 'gapCount', 'invalidCount', 'highWatermark'].every(field => Number.isSafeInteger(snapshot[field]) && snapshot[field] >= 0)) fail('snapshot_integer_invalid');
  if (snapshot.productId !== TARGET.productId || snapshot.productVersion !== TARGET.productVersion || snapshot.canonicalizationVersion !== 'jcs-lite-v1') fail('snapshot_target_mismatch');
  const start = Date.parse(snapshot.windowStart); const end = Date.parse(snapshot.windowEnd); const retain = Date.parse(snapshot.retentionUntil); const asOf = Date.parse(snapshot.asOf);
  if (![start, end, retain, asOf].every(Number.isFinite) || end - start !== 86400000 || retain < end + 26 * 3600000 || asOf < end) fail('snapshot_window_or_retention_invalid');
  if (now < asOf - 30000) fail('snapshot_future');
  if (snapshot.rowCount !== rows.length || snapshot.parseErrorCount !== 0 || snapshot.gapCount !== 0 || snapshot.invalidCount !== 0) fail('snapshot_quality_gate_failed');
  if (expectedRuntimeRelease && snapshot.runtimeRelease !== expectedRuntimeRelease) fail('snapshot_runtime_release_mismatch');
  if (expectedEmbeddedRelease && snapshot.embeddedRelease !== expectedEmbeddedRelease) fail('snapshot_embedded_release_mismatch');
  const normalized = rows.map(normalizeEvent).sort((a, b) => `${a.observedAt}|${a.sequence}|${a.eventId}`.localeCompare(`${b.observedAt}|${b.sequence}|${b.eventId}`));
  if (normalized.some(row => Date.parse(row.observedAt) < start || Date.parse(row.observedAt) >= end || row.sequence > snapshot.highWatermark)) fail('snapshot_event_out_of_window');
  const actualDigest = digest(normalized);
  if (actualDigest !== snapshot.eventDigest) fail('snapshot_digest_mismatch');
  verifyMac(snapshot, keyring, 'snapshot');
  return Object.freeze({ ...snapshot, normalizedRows: normalized });
}

export function evaluateReport({ receipts, events, snapshot }, options = {}) {
  if (!Array.isArray(receipts) || !Array.isArray(events) || events.length !== TARGET.stages.length) return report('event_count_invalid');
  if (!options.expectedRuntimeRelease || !options.expectedEmbeddedRelease) return report('expected_release_required');
  if (!options.replayState || !(options.replayState.usedNonces instanceof Set) || !(options.replayState.usedReceiptIds instanceof Set)) return report('replay_store_required');
  const { usedNonces, usedReceiptIds } = options.replayState;
  const verified = receipts.map(r => verifyReceipt(r, { ...options, usedNonces, usedReceiptIds }));
  if (!verified.length || verified.length !== TARGET.stages.length) return report('receipt_count_invalid');
  const ordered = [...verified].sort((a, b) => a.sequence - b.sequence);
  if (ordered.map(r => r.stage).join('|') !== TARGET.stages.join('|') || ordered.some((r, i) => r.sequence !== i + 1)) return report('stage_order_invalid');
  const binding = ordered[0].serverBindingId;
  const tenant = ordered[0].tenantSubjectDigest;
  if (ordered.some(r => r.serverBindingId !== binding || r.tenantSubjectDigest !== tenant || r.challengeId !== ordered[0].challengeId || r.productId !== TARGET.productId || r.productVersion !== TARGET.productVersion)) return report('chain_binding_invalid');
  const normalizedEvents = events.map(normalizeEvent);
  if (normalizedEvents.some(e => e.serverBindingId !== binding || e.tenantSubjectDigest !== tenant || !verified.some(r => r.receiptId === e.receiptId && r.stage === e.stage))) return report('event_receipt_binding_invalid');
  if (new Set(normalizedEvents.map(e => e.stage)).size !== TARGET.stages.length || new Set(normalizedEvents.map(e => e.sequence)).size !== TARGET.stages.length || normalizedEvents.some(e => e.sequence < 1 || e.sequence > TARGET.stages.length)) return report('event_stage_sequence_invalid');
  verifySnapshot(snapshot, normalizedEvents, options);
  return { status: 'REPORT_ONLY_CANDIDATE', canPromote: false, productionEligible: false, authoritativeProductCredit: 0, denominatorWeight: 0, productId: TARGET.productId, productVersion: TARGET.productVersion, bindingPresent: true, stageCount: ordered.length, eventCount: normalizedEvents.length };
}

function report(reason) { return { status: 'REPORT_ONLY_REJECTED', canPromote: false, productionEligible: false, authoritativeProductCredit: 0, denominatorWeight: 0, rejectionReason: reason }; }

// Test fixture helpers only. Production code must obtain receipts/snapshots from API2.
export function signFixture(value, keyring, purpose) {
  const { signature: _ignoredSignature, ...unsignedBody } = value;
  const body = { ...unsignedBody };
  body.signature = mac(body, key(keyring, body.kid, purpose));
  return body;
}
export const sha256Canonical = digest;
