/**
 * P1-005A exact Independent Board registration contract.
 *
 * This module is intentionally report-only and default-off. It does not infer
 * packageId/expertEntryId from the frozen review zip or from a shared surface.
 * A later signed REGISTERED_EXACT host receipt may replace the pending state;
 * this first wave never enables authoritative product credit.
 */

export const INDEPENDENT_BOARD_PRODUCT_ID = 'fbsir-eight-seat-board'
export const INDEPENDENT_BOARD_NORMALIZED_PRODUCT_ID = 'fbsir_eight_seat_board'
export const INDEPENDENT_BOARD_PRODUCT_VERSION = '26.7.20'
export const INDEPENDENT_BOARD_REVIEW_ZIP_SHA256 = 'd2380072556c0dcf429604ae33713668c509f74a0303f7bca2baceebf32c78cd'
export const INDEPENDENT_BOARD_REGISTRATION_SCHEMA = 'fbs.independentBoard.productRegistration.v1'
export const INDEPENDENT_BOARD_REGISTRATION_STATUS = 'PENDING_HOST_REGISTRATION'

const VERSION_FIELDS = [
  'productVersion',
  'runtimeProductVersion',
  'listedProductVersion',
  'packageVersion',
  'expertPackageVersion',
  'version'
]

function text(value = '') {
  return String(value ?? '').trim()
}

function exactProductId(row = {}) {
  return [row.productId, row.productSignatureProductId, row.listedProductId, row.runtimeProductId]
    .map(text)
    .some(value => value === INDEPENDENT_BOARD_PRODUCT_ID)
}

function exactProductVersion(row = {}) {
  return VERSION_FIELDS
    .map(field => text(row[field]))
    .filter(Boolean)
    .some(value => value === INDEPENDENT_BOARD_PRODUCT_VERSION)
}

export function isIndependentBoardProductId(value = '') {
  return [INDEPENDENT_BOARD_PRODUCT_ID, INDEPENDENT_BOARD_NORMALIZED_PRODUCT_ID].includes(text(value))
}

export function isExactIndependentBoardIdentity(row = {}) {
  return exactProductId(row) && exactProductVersion(row)
}

export function independentBoardProductRegistration() {
  return {
    schema: INDEPENDENT_BOARD_REGISTRATION_SCHEMA,
    productId: INDEPENDENT_BOARD_PRODUCT_ID,
    productVersion: INDEPENDENT_BOARD_PRODUCT_VERSION,
    reviewZipSha256: INDEPENDENT_BOARD_REVIEW_ZIP_SHA256,
    registrationStatus: INDEPENDENT_BOARD_REGISTRATION_STATUS,
    hostType: 'WORKBUDDY',
    connectorType: 'fbs-connector',
    entrySurface: 'official_entry',
    packageId: null,
    expertEntryId: null,
    attributionMode: 'report_only',
    candidateEnabled: false,
    publicRouteEnabled: false,
    authoritativeCreditEnabled: false
  }
}

export function registrationGateForRow(row = {}) {
  const productId = text(row.productSignatureProductId || row.productId)
  if (!isIndependentBoardProductId(productId)) {
    return {
      isIndependentBoard: false,
      registrationStatus: 'NOT_TARGET',
      registrationAuthority: 'not_applicable',
      attributionMode: 'existing_contract',
      authoritativeCreditEnabled: true,
      reason: ''
    }
  }
  const exact = exactProductVersion(row)
  return {
    isIndependentBoard: true,
    registrationStatus: INDEPENDENT_BOARD_REGISTRATION_STATUS,
    registrationAuthority: 'pending_host_registration',
    attributionMode: 'report_only',
    authoritativeCreditEnabled: false,
    reason: exact ? 'exact_host_registration_required' : 'exact_product_version_required'
  }
}

export function verifyRegisteredExactHostRegistration(registration = {}) {
  return {
    verified: false,
    authoritativeCreditEnabled: false,
    reason: 'signed_registration_verifier_not_wired_in_p1005a'
  }
}
