import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const candidateRoot = path.resolve(process.argv[2] || path.join(__dirname, '..', 'work', 'api2-cleanroom', 'p1-005-candidate-20260722', 'source'))
const normalizer = await import(pathToFileURL(path.join(candidateRoot, 'src', 'service-traction-product-signature-normalizer.js')))
const observability = await import(pathToFileURL(path.join(candidateRoot, 'src', 'service-traction-observability.js')))
const registration = await import(pathToFileURL(path.join(candidateRoot, 'src', 'fbss-independent-board-product-registration.js')))
assert.match(registration.INDEPENDENT_BOARD_REVIEW_ZIP_SHA256, /^[0-9a-f]{64}$/)
assert.equal(registration.INDEPENDENT_BOARD_REVIEW_ZIP_SHA256, 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd')

function hostAck() {
  return {
    serverVerificationState: 'verified',
    serverVerificationSource: 'server_dispatch_ack_hmac',
    serverObservedHostForwardingEvidenceTrust: 'server_dispatch_ack_verified',
    ackId: 'ack-1',
    challengeId: 'challenge-1',
    challengeJoinState: 'finalized',
    requestDigest: 'request-digest',
    coveredEventId: 'event-1'
  }
}

function row(overrides = {}) {
  return {
    trafficGroup: 'natural',
    sourceTrafficGroup: 'natural',
    sampleCount: 1,
    serverBindingId: 'srv_AbC123_xYz90',
    bindingIdentitySource: 'session_token_seeded',
    idempotencyKey: 'event-1',
    sameBindingClosable: true,
    serverVerifiedHostForwardingAck: hostAck(),
    ...overrides
  }
}

const targetRow = row({
  productId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  productVersion: registration.INDEPENDENT_BOARD_PRODUCT_VERSION,
  version: '1.29.0',
  runtimeProductId: 'api2-runtime-p1-004',
  expertEntryId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  packCode: 'fbs.independent-board.pending.v1',
  scenePackId: 'independent-board-pending',
  entrySurface: 'official_entry'
})
const targetSignature = normalizer.normalizeProductSignatureBoundary(targetRow)
const targetObservation = observability.normalizeObservationRow({ ...targetRow, ...targetSignature })
assert.equal(targetSignature.productSignatureProductId, 'fbsir_eight_seat_board')
assert.equal(targetSignature.productSignatureCanonicalProductId, registration.INDEPENDENT_BOARD_PRODUCT_ID)
assert.equal(targetSignature.productRegistrationStatus, 'PENDING_HOST_REGISTRATION')
assert.equal(targetSignature.productSignatureProductCreditCandidate, false)
assert.equal(targetSignature.productSignatureReportOnlyCandidate, true)
assert.equal(targetSignature.productRegistrationGateReason, 'exact_host_registration_required')
assert.equal(targetObservation.productCreditCandidate, false)
assert.equal(targetObservation.eligibleForProductCredit, false)
assert.equal(targetObservation.productNaturalDenominatorWeight, 0)
assert.equal(targetObservation.productRegistrationGateReason, 'exact_host_registration_required')
assert.equal(targetObservation.productCreditProvenanceReason, 'exact_host_registration_required')

const wrongVersion = normalizer.normalizeProductSignatureBoundary(row({
  productId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  productVersion: '26.7.19',
  expertEntryId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  packCode: 'fbs.independent-board.pending.v1',
  scenePackId: 'independent-board-pending',
  entrySurface: 'official_entry'
}))
assert.notEqual(wrongVersion.productSignatureProductId, 'fbsir_eight_seat_board')
assert.equal(registration.registrationGateForRow({ productSignatureProductId: 'fbsir_eight_seat_board', productVersion: '26.7.19' }).reason, 'exact_product_version_required')

const conflictingVersion = normalizer.normalizeProductSignatureBoundary(row({
  productId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  productVersion: '26.7.19',
  packageVersion: registration.INDEPENDENT_BOARD_PRODUCT_VERSION,
  expertEntryId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  entrySurface: 'official_entry'
}))
assert.notEqual(conflictingVersion.productSignatureProductId, 'fbsir_eight_seat_board')

const conflictingProductId = normalizer.normalizeProductSignatureBoundary(row({
  productId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  productSignatureProductId: 'workbuddy_board_secretary_assistant',
  productVersion: registration.INDEPENDENT_BOARD_PRODUCT_VERSION,
  expertEntryId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  entrySurface: 'official_entry'
}))
assert.notEqual(conflictingProductId.productSignatureProductId, 'fbsir_eight_seat_board')

const boardSecretary = normalizer.normalizeProductSignatureBoundary(row({
  productId: 'workbuddy_board_secretary_assistant',
  expertEntryId: 'board_secretary_assistant',
  packCode: 'fbss.board_secretary.compliance_red_team.v1',
  scenePackId: 'board-secretary',
  entrySurface: 'official_entry'
}))
assert.notEqual(boardSecretary.productSignatureProductId, 'fbsir_eight_seat_board')

const general = normalizer.normalizeProductSignatureBoundary(row({
  productId: '',
  expertEntryId: '',
  packCode: 'fbss.general.seven_day.v1',
  scenePackId: 'general',
  entryId: 'workbuddy.default',
  entrySurface: 'unknown_surface',
  intentFamily: 'general',
  profileSegment: 'new_user'
}))
assert.notEqual(general.productSignatureProductId, 'fbsir_eight_seat_board')

const forgedRegistered = registration.verifyRegisteredExactHostRegistration({
  schema: registration.INDEPENDENT_BOARD_REGISTRATION_SCHEMA,
  registrationStatus: 'REGISTERED_EXACT',
  productId: registration.INDEPENDENT_BOARD_PRODUCT_ID,
  productVersion: registration.INDEPENDENT_BOARD_PRODUCT_VERSION,
  reviewZipSha256: registration.INDEPENDENT_BOARD_REVIEW_ZIP_SHA256,
  packageId: 'pkg-1',
  expertEntryId: 'entry-1',
  hostType: 'WORKBUDDY',
  connectorType: 'fbs-connector',
  entrySurface: 'official_entry',
  signatureVerified: true,
  candidateEnabled: false,
  publicRouteEnabled: false
})
assert.equal(forgedRegistered.verified, false)
assert.equal(forgedRegistered.authoritativeCreditEnabled, false)

process.stdout.write(`${JSON.stringify({
  status: 'pass',
  target: { productId: targetSignature.productSignatureProductId, canonicalProductId: targetSignature.productSignatureCanonicalProductId, candidate: targetObservation.productCreditCandidate, eligible: targetObservation.eligibleForProductCredit, reason: targetObservation.productRegistrationGateReason },
  wrongVersionRejected: true,
  conflictingVersionRejected: true,
  conflictingProductIdRejected: true,
  boardSecretaryIsolated: true,
  generalSurfaceIsolated: true,
  forgedRegistrationRejected: true
}, null, 2)}\n`)
