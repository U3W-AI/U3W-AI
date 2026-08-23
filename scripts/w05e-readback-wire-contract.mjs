import {
  createHash,
  createHmac,
  timingSafeEqual
} from 'node:crypto'

export const REQUEST_SCHEMA =
  'fbsir.independentBoardAttributionReadbackRequest.v1'
export const RESPONSE_SCHEMA =
  'fbsir.independentBoardAttributionReadbackResponse.v1'
export const REQUEST_DOMAIN = 'FBSIR_INDEPENDENT_BOARD_READBACK_V1'
export const RESPONSE_DOMAIN =
  'FBSIR_INDEPENDENT_BOARD_READBACK_RESPONSE_V1'
export const METHOD = 'POST'
export const PATH =
  '/internal/independent-board/attribution/events/readback'

const HEX64 = /^[0-9a-f]{64}$/
const KEY_ID = /^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$/
const NONCE = /^[A-Za-z0-9][A-Za-z0-9._:-]{15,127}$/
const UTC_INSTANT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/
const REQUEST_KEYS = Object.freeze([
  'schemaVersion', 'eventId', 'receiptId', 'eventDigest', 'issuedAt',
  'expiresAt', 'nonce', 'keyId', 'signature'
])
const RESPONSE_KEYS = Object.freeze([
  'schemaVersion', 'status', 'eventId', 'receiptId', 'eventDigest',
  'authoritativeRead', 'httpStatus', 'requestDigest', 'requestNonceHash',
  'readAt', 'expiresAt', 'receiverReleaseId', 'receiverJarSha256',
  'productCreditEligible', 'keyId', 'signature'
])
const STATUS_HTTP = new Map([
  ['COMMITTED_EXACT', 200],
  ['NOT_FOUND_AUTHORITATIVE', 404],
  ['IDENTITY_COLLISION', 409],
  ['READBACK_UNAVAILABLE', 503]
])

function exactKeys(value, keys) {
  return value && typeof value === 'object' && !Array.isArray(value) &&
    Object.keys(value).length === keys.length &&
    keys.every(key => Object.hasOwn(value, key))
}

function string(value) {
  return typeof value === 'string' ? value : ''
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function hmac(secret, value) {
  return createHmac('sha256', secret).update(value).digest('hex')
}

function safeHexEqual(left, right) {
  if (!HEX64.test(left) || !HEX64.test(right)) return false
  return timingSafeEqual(Buffer.from(left, 'hex'), Buffer.from(right, 'hex'))
}

export function canonicalRequest(request) {
  return `${REQUEST_DOMAIN}\n${JSON.stringify({
    method: METHOD,
    path: PATH,
    schemaVersion: string(request?.schemaVersion),
    eventId: string(request?.eventId),
    receiptId: string(request?.receiptId),
    eventDigest: string(request?.eventDigest),
    issuedAt: string(request?.issuedAt),
    expiresAt: string(request?.expiresAt),
    nonce: string(request?.nonce),
    keyId: string(request?.keyId)
  })}`
}

export function canonicalResponse(response) {
  return `${RESPONSE_DOMAIN}\n${JSON.stringify({
    schemaVersion: string(response?.schemaVersion),
    status: string(response?.status),
    httpStatus: Number(response?.httpStatus),
    eventId: string(response?.eventId),
    receiptId: string(response?.receiptId),
    eventDigest: string(response?.eventDigest),
    authoritativeRead: response?.authoritativeRead === true,
    requestDigest: string(response?.requestDigest),
    requestNonceHash: string(response?.requestNonceHash),
    readAt: string(response?.readAt),
    expiresAt: string(response?.expiresAt),
    receiverReleaseId: string(response?.receiverReleaseId),
    receiverJarSha256: string(response?.receiverJarSha256),
    productCreditEligible: response?.productCreditEligible === true,
    keyId: string(response?.keyId)
  })}`
}

export function signRequest(unsigned, { keyId, secret }) {
  const request = {
    ...unsigned,
    schemaVersion: REQUEST_SCHEMA,
    keyId,
    signature: ''
  }
  assertRequestShape(request)
  return Object.freeze({
    ...request,
    signature: hmac(secret, canonicalRequest(request))
  })
}

export function signResponse(unsigned, { keyId, secret }) {
  const response = {
    ...unsigned,
    schemaVersion: RESPONSE_SCHEMA,
    keyId,
    signature: ''
  }
  if (!exactKeys(response, RESPONSE_KEYS)) {
    throw new Error('readback_response_shape_invalid')
  }
  return Object.freeze({
    ...response,
    signature: hmac(secret, canonicalResponse(response))
  })
}

export function verifyResponse(response, {
  request,
  httpStatus,
  keyring,
  now = Date.now(),
  receiverReleaseId,
  receiverJarSha256
}) {
  assertRequestShape(request)
  if (!exactKeys(response, RESPONSE_KEYS) ||
      response.schemaVersion !== RESPONSE_SCHEMA ||
      STATUS_HTTP.get(response.status) !== httpStatus ||
      response.httpStatus !== httpStatus ||
      response.eventId !== request.eventId ||
      response.receiptId !== request.receiptId ||
      response.eventDigest !== request.eventDigest ||
      response.requestDigest !== sha256(canonicalRequest(request)) ||
      response.requestNonceHash !== sha256(request.nonce) ||
      response.receiverReleaseId !== receiverReleaseId ||
      response.receiverJarSha256 !== receiverJarSha256 ||
      response.productCreditEligible !== false ||
      !UTC_INSTANT.test(response.readAt) ||
      !UTC_INSTANT.test(response.expiresAt) ||
      !KEY_ID.test(response.keyId) ||
      !HEX64.test(response.signature)) {
    throw new Error('readback_response_contract_invalid')
  }
  const authoritative = httpStatus !== 503
  if (response.authoritativeRead !== authoritative) {
    throw new Error('readback_response_authority_invalid')
  }
  const readAt = Date.parse(response.readAt)
  const expiresAt = Date.parse(response.expiresAt)
  const observed = Number(typeof now === 'function' ? now() : now)
  if (!Number.isFinite(readAt) || !Number.isFinite(expiresAt) ||
      !Number.isFinite(observed) || expiresAt <= readAt ||
      expiresAt - readAt > 60_000 || observed > expiresAt ||
      observed < readAt - 30_000) {
    throw new Error('readback_response_time_invalid')
  }
  const secret = keyring?.[response.keyId]
  if (!secret || !safeHexEqual(
    response.signature,
    hmac(secret, canonicalResponse(response))
  )) {
    throw new Error('readback_response_signature_invalid')
  }
  return Object.freeze({
    status: response.status,
    httpStatus,
    authoritativeRead: response.authoritativeRead,
    productCreditEligible: false,
    receiverReleaseId,
    receiverJarSha256
  })
}

function assertRequestShape(request) {
  if (!exactKeys(request, REQUEST_KEYS) ||
      request.schemaVersion !== REQUEST_SCHEMA ||
      !HEX64.test(request.eventId) ||
      !HEX64.test(request.receiptId) ||
      !HEX64.test(request.eventDigest) ||
      !NONCE.test(request.nonce) ||
      !KEY_ID.test(request.keyId) ||
      (request.signature && !HEX64.test(request.signature))) {
    throw new Error('readback_request_shape_invalid')
  }
}
