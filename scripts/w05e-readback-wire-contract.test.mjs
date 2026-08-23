import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import {
  REQUEST_SCHEMA,
  RESPONSE_SCHEMA,
  canonicalRequest,
  signRequest,
  signResponse,
  verifyResponse
} from './w05e-readback-wire-contract.mjs'

const SECRET = Buffer.from(
  '0123456789abcdef0123456789abcdef', 'utf8')
const KEY_ID = 'readback-k1'
const RELEASE = 'w05e-readback-test'
const JAR = 'a'.repeat(64)
const NOW = Date.parse('2026-08-23T08:40:30Z')

function request() {
  return signRequest({
    schemaVersion: REQUEST_SCHEMA,
    eventId: '1'.repeat(64),
    receiptId: '2'.repeat(64),
    eventDigest: '3'.repeat(64),
    issuedAt: '2026-08-23T08:40:00Z',
    expiresAt: '2026-08-23T08:41:00Z',
    nonce: 'readback-nonce-0001',
    keyId: KEY_ID,
    signature: ''
  }, { keyId: KEY_ID, secret: SECRET })
}

function response(status = 'COMMITTED_EXACT', httpStatus = 200) {
  const wire = request()
  return signResponse({
    schemaVersion: RESPONSE_SCHEMA,
    status,
    eventId: wire.eventId,
    receiptId: wire.receiptId,
    eventDigest: wire.eventDigest,
    authoritativeRead: httpStatus !== 503,
    httpStatus,
    requestDigest: createHash('sha256')
      .update(canonicalRequest(wire)).digest('hex'),
    requestNonceHash: createHash('sha256')
      .update(wire.nonce).digest('hex'),
    readAt: '2026-08-23T08:40:30Z',
    expiresAt: '2026-08-23T08:41:30Z',
    receiverReleaseId: RELEASE,
    receiverJarSha256: JAR,
    productCreditEligible: false,
    keyId: KEY_ID,
    signature: ''
  }, { keyId: KEY_ID, secret: SECRET })
}

test('verifies all four signed authoritative response outcomes', () => {
  for (const [status, httpStatus] of [
    ['COMMITTED_EXACT', 200],
    ['NOT_FOUND_AUTHORITATIVE', 404],
    ['IDENTITY_COLLISION', 409],
    ['READBACK_UNAVAILABLE', 503]
  ]) {
    const result = verifyResponse(response(status, httpStatus), {
      request: request(),
      httpStatus,
      keyring: { [KEY_ID]: SECRET },
      now: NOW,
      receiverReleaseId: RELEASE,
      receiverJarSha256: JAR
    })
    assert.equal(result.status, status)
    assert.equal(result.productCreditEligible, false)
  }
})

test('rejects unsigned feature-off 404 and status/body confusion', () => {
  assert.throws(() => verifyResponse({}, {
    request: request(),
    httpStatus: 404,
    keyring: { [KEY_ID]: SECRET },
    now: NOW,
    receiverReleaseId: RELEASE,
    receiverJarSha256: JAR
  }), /contract_invalid/)

  for (const httpStatus of [400, 404, 500, 502, 504]) {
    assert.throws(() => verifyResponse({
      status: 'UNKNOWN_RETRY',
      reason: 'generic'
    }, {
      request: request(),
      httpStatus,
      keyring: { [KEY_ID]: SECRET },
      now: NOW,
      receiverReleaseId: RELEASE,
      receiverJarSha256: JAR
    }), /contract_invalid/)
  }

  const wrong = { ...response('NOT_FOUND_AUTHORITATIVE', 404), httpStatus: 200 }
  assert.throws(() => verifyResponse(wrong, {
    request: request(),
    httpStatus: 404,
    keyring: { [KEY_ID]: SECRET },
    now: NOW,
    receiverReleaseId: RELEASE,
    receiverJarSha256: JAR
  }), /contract_invalid/)
})

test('rejects tuple request release jar time credit and signature tampering', () => {
  const base = response()
  const changes = [
    { eventDigest: '4'.repeat(64) },
    { requestDigest: '4'.repeat(64) },
    { requestNonceHash: '4'.repeat(64) },
    { receiverReleaseId: 'other' },
    { receiverJarSha256: 'b'.repeat(64) },
    { productCreditEligible: true },
    { readAt: '2026-08-23T16:40:30+08:00' },
    { readAt: '2026-08-23T08:40:30.0000Z' },
    { expiresAt: '2026-08-23T08:42:00Z' },
    { signature: '0'.repeat(64) }
  ]
  for (const change of changes) {
    assert.throws(() => verifyResponse({ ...base, ...change }, {
      request: request(),
      httpStatus: 200,
      keyring: { [KEY_ID]: SECRET },
      now: NOW,
      receiverReleaseId: RELEASE,
      receiverJarSha256: JAR
    }))
  }
})

test('keeps every signed decision response below the 16 KiB wire ceiling', () => {
  for (const [status, httpStatus] of [
    ['COMMITTED_EXACT', 200],
    ['NOT_FOUND_AUTHORITATIVE', 404],
    ['IDENTITY_COLLISION', 409],
    ['READBACK_UNAVAILABLE', 503]
  ]) {
    assert.ok(Buffer.byteLength(
      JSON.stringify(response(status, httpStatus)), 'utf8') <= 16 * 1024)
  }
})

test('rejects unknown response fields', () => {
  assert.throws(() => verifyResponse({ ...response(), extra: 'x' }, {
    request: request(),
    httpStatus: 200,
    keyring: { [KEY_ID]: SECRET },
    now: NOW,
    receiverReleaseId: RELEASE,
    receiverJarSha256: JAR
  }), /contract_invalid/)
})

test('verifies the shared Java and Node golden vector', () => {
  const vector = JSON.parse(readFileSync(new URL(
    '../FBSir-business/src/test/resources/' +
      'independent-board-attribution-readback-v1-golden-vector.json',
    import.meta.url
  ), 'utf8'))
  const encoded = vector.encodedSecret
  const secret = Buffer.from(encoded.slice('utf8:'.length), 'utf8')
  const generated = signRequest({
    ...vector.request,
    signature: ''
  }, { keyId: vector.request.keyId, secret })
  assert.equal(generated.signature, vector.request.signature)
  const result = verifyResponse(vector.response, {
    request: vector.request,
    httpStatus: 200,
    keyring: { [vector.request.keyId]: secret },
    now: Date.parse(vector.verificationClock),
    receiverReleaseId: vector.response.receiverReleaseId,
    receiverJarSha256: vector.response.receiverJarSha256
  })
  assert.equal(result.status, 'COMMITTED_EXACT')
})
