import { readFile, stat } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { pathToFileURL } from 'node:url'

const TARGET_PRODUCT_ID = 'fbsir-eight-seat-board'
const TARGET_LISTED_VERSION = '26.7.20'
const MAX_INPUT_BYTES = 64 * 1024 * 1024
const MAX_SAMPLE_COUNT_PER_ROW = 1_000_000

const IDENTITY_FIELDS = [
  'productId',
  'starterProductId',
  'productSignatureProductId',
  'productSignatureMatchedProductIds',
  'serviceProductId',
  'expertEntryId',
  'starterExpertEntryId',
  'packageId',
  'expertId'
]

const VERSION_FIELDS = [
  'packageVersion',
  'listingVersion',
  'expertVersion'
]

const HOST_RECEIPT_CONTRACT = Object.freeze({
  serverVerificationState: 'server_verified',
  serverVerificationSource: 'fbss_host_forwarding_receipt_v1',
  evidenceTrust: 'server_verified',
  receiptStatus: 'verified',
  challengeJoinState: 'same_binding_joined'
})

const SERVICE_CLOSURE_CONTRACT = Object.freeze({
  verificationState: 'server_verified',
  verificationSource: 'fbss_service_closure_receipt_v1'
})

const TOOL_STAGE_BY_NAME = new Map([
  ['skill_whoami', 'whoami'],
  ['fbs_whoami', 'whoami'],
  ['whoami', 'whoami'],
  ['fbs_scene_pack_query', 'scene_pack'],
  ['skill_scene_pack', 'scene_pack'],
  ['scene_pack', 'scene_pack'],
  ['skill_consume', 'consume'],
  ['fbs_skill_consume', 'consume'],
  ['consume', 'consume']
])

const EVENT_STAGE_BY_NAME = new Map([
  ['whoami_completed', 'whoami'],
  ['identity_binding_completed', 'whoami'],
  ['scene_pack_completed', 'scene_pack'],
  ['scene_pack_resolved', 'scene_pack'],
  ['consume_completed', 'consume']
])

const SERVICE_CLOSURE_EVENT_TYPES = new Set([
  'continued_use_completed',
  'service_closure_completed'
])

const SOURCE_METADATA_ALLOWLIST = new Set([
  'unspecified',
  'stdin',
  'fixture',
  'api2_natural_evidence_ledger',
  'api2_fixed_window_legacy_snapshot',
  'runtime_state_backfill'
])

const HOST_VALUE_ALLOWLIST = new Set([
  'workbuddy',
  'workbuddy_ai',
  'codebuddy',
  'fbsir_hub',
  'apple_watch',
  'node'
])

const EXPLICIT_FAILURE_STATES = new Set([
  'failed',
  'failure',
  'error',
  'rejected',
  'denied',
  'cancelled',
  'canceled',
  'preview'
])

const TRUSTED_NATURAL_AUTHORITIES = new Set([
  'server_verified',
  'server_runtime_verified',
  'trusted_server_classifier',
  'host_receipt_verified'
])

const DIMENSION_VALUE_ALLOWLISTS = Object.freeze({
  channel: new Set([
    'unknown',
    'unknown_channel',
    'workbuddy_cn',
    'workbuddy_intl',
    'fbs-live-monitor',
    'fbs_live_monitor',
    'bookwriter_skill',
    'fbs-connector',
    'fbs_connector',
    'api2',
    'me_u3w',
    'admin_u3w',
    'apple_watch',
    'fbsir_hub'
  ]),
  terminal: new Set([
    'unknown',
    'unknown_terminal',
    'desktop',
    'node',
    'curl',
    'web',
    'mobile',
    'ios',
    'android',
    'workbuddy',
    'workbuddy_ai',
    'apple_watch',
    'fbsir_hub'
  ]),
  intent: new Set([
    'unknown',
    'unknown_intent',
    'general',
    'long_document_production',
    'company_strategy',
    'board_secretary_ir_workflow',
    'independent_board_review',
    'expert_consultation',
    'identity_binding',
    'scene_pack',
    'consume',
    'continued_use'
  ])
})

const PROBE_MARKER = /(^|[^a-z0-9])(probe|preflight|monitor|readonly|read_only|smoke|test|control|reference|codex)(?=$|[^a-z0-9])/i
const SYNTHETIC_MARKER = /(^|[^a-z0-9])(synthetic|simulation|simulated|codex_client_simulation)(?=$|[^a-z0-9])/i
const DIAGNOSTIC_MARKER = /(^|[^a-z0-9])(diagnostic|diagnose|evidence_only)(?=$|[^a-z0-9])/i

function text(value) {
  return String(value ?? '').trim()
}

function lower(value) {
  return text(value).toLowerCase()
}

function truthy(value) {
  return value === true || ['true', '1', 'yes'].includes(lower(value))
}

function boundedSampleCount(row) {
  const parsed = Number(row?.sampleCount ?? 1)
  const valid = Number.isSafeInteger(parsed)
    && parsed >= 1
    && parsed <= MAX_SAMPLE_COUNT_PER_ROW
  return { value: valid ? parsed : 1, valid }
}

function timestampOf(row) {
  const value = row?.observedAt ?? row?.createdAt ?? row?.timestamp ?? row?.ts
  const parsed = Date.parse(text(value))
  return Number.isFinite(parsed) ? parsed : null
}

function valuesFor(row, fields) {
  const values = []
  const append = value => {
    if (Array.isArray(value)) {
      for (const item of value) append(item)
      return
    }
    if (['string', 'number', 'boolean'].includes(typeof value)) {
      const normalized = lower(value)
      if (normalized) values.push(normalized)
    } else if (value && typeof value === 'object') {
      values.push('__invalid_identity_shape__')
    }
  }
  for (const field of fields) append(row?.[field])
  return values
}

function allScalarText(value, output = []) {
  if (Array.isArray(value)) {
    for (const item of value) allScalarText(item, output)
  } else if (value && typeof value === 'object') {
    for (const item of Object.values(value)) allScalarText(item, output)
  } else if (['string', 'number', 'boolean'].includes(typeof value)) {
    output.push(lower(value))
  }
  return output
}

function trustedHostReceipt(row) {
  return row?.serverVerifiedHostForwardingAck === true
    && row?.hostForwardingReceiptObserved === true
    && row?.hostForwardingReceiptCreditEligible === true
    && lower(row?.hostForwardingReceiptStatus) === HOST_RECEIPT_CONTRACT.receiptStatus
    && lower(row?.hostForwardingEvidenceTrust) === HOST_RECEIPT_CONTRACT.evidenceTrust
    && lower(row?.serverVerificationState) === HOST_RECEIPT_CONTRACT.serverVerificationState
    && lower(row?.serverVerificationSource) === HOST_RECEIPT_CONTRACT.serverVerificationSource
    && lower(row?.hostForwardingAckChallengeJoinState) === HOST_RECEIPT_CONTRACT.challengeJoinState
    && row?.hostForwardingAckChallengeRepositoryDurable === true
    && row?.hostForwardingAckChallengeRepositoryProductionReady === true
}

function stableBindingKey(row) {
  const contracts = [
    ['chainFingerprint', /^(?:[a-f0-9]{16}|[a-f0-9]{32}|[a-f0-9]{64})$/i],
    ['serverBindingId', /^[A-Za-z0-9_-]{16,64}$/],
    ['anonymousUserCodeHash', /^[A-Za-z0-9_-]{16,128}$/],
    ['traceCorrelationId', /^(?:[a-f0-9]{16}|[a-f0-9]{32}|[a-f0-9]{64}|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})$/i],
    ['traceparentTraceId', /^(?:[a-f0-9]{16}|[a-f0-9]{32})$/i],
    ['traceId', /^(?:[a-f0-9]{16}|[a-f0-9]{32}|[a-f0-9]{64}|[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12})$/i]
  ]
  for (const [field, contract] of contracts) {
    const rawValue = row?.[field]
    if (typeof rawValue !== 'string') continue
    const value = rawValue.trim()
    if (!contract.test(value) || /^\d+$/.test(value)) continue
    if (new Set([...value.toLowerCase()]).size < 6) continue
    return value
  }
  return null
}

function trustedServiceClosureReceipt(row) {
  const eventType = lower(row?.eventType)
  const toolStage = toolStageOf(row)
  return row?.serviceClosureReceiptVerified === true
    && !hasExplicitFailure(row)
    && lower(row?.serviceClosureVerificationState) === SERVICE_CLOSURE_CONTRACT.verificationState
    && lower(row?.serviceClosureVerificationSource) === SERVICE_CLOSURE_CONTRACT.verificationSource
    && SERVICE_CLOSURE_EVENT_TYPES.has(eventType)
    && !['whoami', 'scene_pack'].includes(toolStage)
}

function isTrustedNaturalAuthority(value) {
  const authority = lower(value)
  return TRUSTED_NATURAL_AUTHORITIES.has(authority)
}

export function classifyTrafficKind(row = {}) {
  const values = [
    row.trafficGroup,
    row.sourceTrafficGroup,
    row.businessTrafficGroup,
    row.trafficClass,
    row.sourceTrafficClass,
    row.sampleClass,
    row.sourceSampleClass,
    row.requestSource,
    row.channel,
    row.touchPoint,
    row.triggerSource,
    row.source,
    row.fbssTestRoute,
    row.testRunId
  ].map(text).filter(Boolean)
  const groups = values.map(lower)

  if (truthy(row.isSynthetic) || truthy(row.synthetic) || text(row.testRunId)
    || values.some(value => SYNTHETIC_MARKER.test(value))) return 'synthetic'
  if (groups.includes('probe') || values.some(value => PROBE_MARKER.test(value))) return 'probe'
  if (truthy(row.evidenceOnly) || Number(row.trafficSampleWeight) === 0
    || groups.includes('diagnostic') || values.some(value => DIAGNOSTIC_MARKER.test(value))) return 'diagnostic'

  const declaredNatural = [row.trafficGroup, row.sourceTrafficGroup, row.businessTrafficGroup]
    .map(lower)
    .filter(Boolean)
  if (declaredNatural.includes('natural')
    && declaredNatural.every(value => value === 'natural')
    && isTrustedNaturalAuthority(row.trafficClassificationAuthority)) return 'natural'
  return 'unknown'
}

export function classifyIndependentBoardIdentity(row = {}) {
  const identities = valuesFor(row, IDENTITY_FIELDS)
  const versions = valuesFor(row, VERSION_FIELDS)
  const directSignal = identities.includes(TARGET_PRODUCT_ID)
  const nonTargetIdentities = identities.filter(value => value !== TARGET_PRODUCT_ID)
  const hasBoardSecretaryConflict = identities.some(value => [
    'fbsir-board-secretary-assistant',
    'workbuddy_board_secretary_assistant'
  ].includes(value))
  const hasSuperPartnerConflict = identities.some(value => [
    'fbsir-super-partner-group',
    'workbuddy_super_partner_group'
  ].includes(value))
  const hasConnectorProxy = identities.some(value => [
    'fbs-connector',
    'fbs_connector',
    'fbs-connector-mcp'
  ].includes(value))
  const hasConflict = nonTargetIdentities.length > 0
  const scalarText = allScalarText(row)
  const privateBoardText = scalarText.some(value => (
    /(^|[^a-z0-9])private[-_\s]*board(?=$|[^a-z0-9])/i.test(value)
    || value.replace(/\s+/gu, '').includes('私董会')
  ))
  const versionMatched = versions.length > 0
    && versions.every(value => value === TARGET_LISTED_VERSION)
  const receiptTrusted = trustedHostReceipt(row)

  let state = 'general_reference'
  if (directSignal && (hasConflict || privateBoardText)) state = 'conflicted_identity'
  else if (directSignal && versions.length === 0) state = 'direct_identity_version_missing'
  else if (directSignal && !versionMatched) state = 'direct_identity_version_mismatch'
  else if (directSignal && !receiptTrusted) state = 'untrusted_direct_identity'
  else if (directSignal) state = 'trusted_candidate'
  else if (hasBoardSecretaryConflict) state = 'off_target_board_secretary'
  else if (hasSuperPartnerConflict) state = 'off_target_super_partner'
  else if (hasConnectorProxy) state = 'connector_proxy'
  else if (identities.length > 0) state = 'off_target_identity'
  else if (privateBoardText) state = 'private_board_confusion'

  return {
    state,
    directSignal,
    privateBoardText,
    versionMatched,
    versionObserved: versions.length > 0 ? [...new Set(versions)].sort().join('|') : null,
    trustedHostReceipt: receiptTrusted,
    stableBindingKey: stableBindingKey(row),
    sameBindingClosable: truthy(row.sameBindingClosable) && Boolean(stableBindingKey(row)),
    creditEligible: state === 'trusted_candidate'
  }
}

function hasExplicitFailure(row) {
  const eventType = lower(row?.eventType)
  const explicitFailure = row?.success === false
    || ['false', '0', 'no'].includes(lower(row?.success))
    || [row?.resultStatus, row?.toolStatus, row?.eventStatus]
      .map(lower)
      .some(value => EXPLICIT_FAILURE_STATES.has(value))
  const failedEventType = /(^|_)(?:failed|failure|error|rejected|denied|cancelled|canceled|preview)(?:_|$)/.test(eventType)
  const errorCode = text(row?.errorCode)
  return explicitFailure || failedEventType || Boolean(errorCode && errorCode !== '0')
}

function toolStageOf(row) {
  const eventType = lower(row?.eventType)
  const toolName = lower(row?.toolName)
  if (hasExplicitFailure(row)) return 'other'
  return TOOL_STAGE_BY_NAME.get(toolName)
    || EVENT_STAGE_BY_NAME.get(eventType)
    || 'other'
}

function outcomeModeOf(row) {
  const eventType = lower(row?.eventType)
  if (SERVICE_CLOSURE_EVENT_TYPES.has(eventType)) return 'continued_use'
  if (['first_value_completed', 'first_value_observed'].includes(eventType)) return 'first_value'
  return 'other'
}

function modeOf(row) {
  const outcomeMode = outcomeModeOf(row)
  return outcomeMode === 'other' ? toolStageOf(row) : outcomeMode
}

function productDimension(identity, productCreditEnabled) {
  if (identity.creditEligible && productCreditEnabled) return TARGET_PRODUCT_ID
  if (identity.creditEligible) return 'independent_board_trusted_candidate_credit_disabled'
  if (identity.directSignal) return `independent_board_${identity.state}`
  return identity.state
}

function firstValue(row, fields, fallback = 'unknown') {
  for (const field of fields) {
    const value = text(row?.[field])
    if (value) return value
  }
  return fallback
}

function categoricalValue(row, fields, dimension) {
  const value = lower(firstValue(row, fields))
  if (value === 'unknown') return value
  if ([...value].length > 128 || Buffer.byteLength(value, 'utf8') > 256) return 'invalid_or_unbounded'
  if (!/^[\p{L}\p{N}._-]+$/u.test(value) || !/\p{L}/u.test(value)) return 'invalid_or_unbounded'
  return DIMENSION_VALUE_ALLOWLISTS[dimension]?.has(value)
    ? value
    : 'other_or_redacted'
}

function hostValue(row) {
  const value = lower(firstValue(row, ['hostType', 'hostPlatform', 'hostExecutionPlane']))
  if (value === 'unknown') return value
  return HOST_VALUE_ALLOWLIST.has(value) ? value : 'other_or_redacted'
}

function clientVersionValue(row) {
  const fields = [
    'hostPatchVersion',
    'hostVersion',
    'clientVersion',
    'requestHeaderClientVersion',
    'requestUserAgentClientVersion'
  ]
  const values = []
  for (const field of fields) {
    const rawValue = row?.[field]
    if (rawValue === undefined || rawValue === null || rawValue === '') continue
    if (typeof rawValue !== 'string') return 'other_or_redacted'
    values.push(lower(rawValue))
  }
  if (values.length === 0) return 'unknown'
  if (values.some(value => !/^v?\d{1,4}(?:\.\d{1,4}){1,3}(?:[-+][0-9a-z.-]{1,32})?$/.test(value))) {
    return 'other_or_redacted'
  }
  const distinct = [...new Set(values)]
  return distinct.length === 1 ? distinct[0] : 'conflicted'
}

function versionDimension(identity) {
  const value = text(identity.versionObserved)
  if (!value) return 'unknown'
  if ([...value].length > 64 || Buffer.byteLength(value, 'utf8') > 128) return 'invalid_or_unbounded'
  if (!/^[\p{L}\p{N}._+-]+$/u.test(value)) return 'invalid_or_unbounded'
  if (value === TARGET_LISTED_VERSION) return value
  if (/^v?\d{1,4}(?:\.\d{1,4}){1,3}(?:[-+][0-9a-z.-]{1,32})?$/i.test(value)) {
    return 'other_semantic_version'
  }
  return 'other_or_redacted'
}

function sourceMetadataValue(value) {
  const normalized = lower(value) || 'unspecified'
  return SOURCE_METADATA_ALLOWLIST.has(normalized) ? normalized : 'other_or_redacted'
}

function releaseMetadataValue(value) {
  const normalized = lower(value)
  if (!normalized || [...normalized].length > 160 || Buffer.byteLength(normalized, 'utf8') > 192) return null
  const deploymentRelease = /^\d{8,14}(?:-[a-z0-9][a-z0-9.-]{0,127})+$/
  const testRelease = /^(?:fixture|runtime|embedded)-release(?:-[a-z0-9.-]+)?$/
  return deploymentRelease.test(normalized) || testRelease.test(normalized)
    ? normalized
    : null
}

function addCounter(counter, key, sampleCount) {
  const current = counter.get(key) || { key, rowCount: 0, sampleCount: 0 }
  current.rowCount += 1
  current.sampleCount += sampleCount
  counter.set(key, current)
}

function counterValues(counter) {
  return [...counter.values()].sort((left, right) => left.key.localeCompare(right.key))
}

function emptyMetric() {
  return { rowCount: 0, sampleCount: 0 }
}

function addMetric(metric, sampleCount) {
  metric.rowCount += 1
  metric.sampleCount += sampleCount
}

function normalizedWindow(options) {
  const startMs = Date.parse(text(options?.start))
  const endMs = Date.parse(text(options?.end))
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs >= endMs) {
    throw new TypeError('A valid fixed start/end window with start < end is required')
  }
  return { startMs, endMs }
}

function canonicalValue(value) {
  if (Array.isArray(value)) return value.map(canonicalValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort()
      .filter(key => value[key] !== undefined)
      .map(key => [key, canonicalValue(value[key])]))
  }
  return value
}

function snapshotDigest(rows, startMs, endMs) {
  const digest = createHash('sha256')
  digest.update('fbsir.independent-board.traffic-attribution.input/v1\n')
  digest.update(`${new Date(startMs).toISOString()}\n${new Date(endMs).toISOString()}\n`)
  for (const row of rows) digest.update(`${JSON.stringify(canonicalValue(row))}\n`)
  return digest.digest('hex')
}

function completeOrderedTargetChain(entries) {
  const bindingHasIdentityConflict = entries.some(entry => (
    entry.identity.privateBoardText
    || !['general_reference', 'trusted_candidate'].includes(entry.identity.state)
  ))
  if (bindingHasIdentityConflict) return null

  const relevant = entries
    .filter(entry => entry.toolStage !== 'other' || entry.outcomeMode !== 'other')
    .sort((left, right) => left.observedMs - right.observedMs)
  if (relevant.length === 0 || relevant.some(entry => (
    !entry.identity.creditEligible || entry.trafficKind !== 'natural' || !entry.sampleCountValid
  ))) return null


  const stageEntries = relevant.filter(entry => entry.toolStage !== 'other')
  const unsafeRouteBuckets = new Set(['unknown', 'unknown_channel', 'unknown_terminal', 'other_or_redacted', 'invalid_or_unbounded', 'conflicted'])
  const routeKeys = ['channelKey', 'terminalKey', 'hostKey', 'clientVersionKey']
  if (stageEntries.some(entry => routeKeys.some(key => unsafeRouteBuckets.has(entry[key])))) return null
  if (routeKeys.some(key => new Set(stageEntries.map(entry => entry[key])).size !== 1)) return null

  for (const whoami of relevant.filter(entry => entry.toolStage === 'whoami')) {
    const scenePack = relevant.find(entry => (
      entry.toolStage === 'scene_pack' && entry.observedMs > whoami.observedMs
    ))
    if (!scenePack) continue
    const consume = relevant.find(entry => (
      entry.toolStage === 'consume' && entry.observedMs > scenePack.observedMs
    ))
    if (consume) {
      return {
        whoami,
        scenePack,
        consume,
        relevant,
        route: Object.fromEntries(routeKeys.map(key => [key, consume[key]]))
      }
    }
  }
  return null
}

export function analyzeIndependentBoardTraffic(rows = [], options = {}) {
  const { startMs, endMs } = normalizedWindow(options)
  const candidateAttributionRequested = options.enableCandidateAttribution === true
  const generatedAtMs = options.generatedAt === undefined
    ? Date.now()
    : Date.parse(text(options.generatedAt))
  if (!Number.isFinite(generatedAtMs)) throw new TypeError('generatedAt must be an ISO-8601 timestamp')
  const sourceRows = Array.isArray(rows) ? rows : []
  const digest = snapshotDigest(sourceRows, startMs, endMs)
  const expectedSnapshotDigest = lower(options.expectedSnapshotDigest)
  if (expectedSnapshotDigest && !/^[a-f0-9]{64}$/.test(expectedSnapshotDigest)) {
    throw new TypeError('expectedSnapshotDigest must be a lowercase SHA-256 hex digest')
  }
  const snapshotAlignment = !expectedSnapshotDigest
    ? 'unverified'
    : expectedSnapshotDigest === digest ? 'aligned' : 'drift'
  const runtimeReleaseInput = text(options.serviceRelease)
  const embeddedReleaseInput = text(options.embeddedReleaseId)
  const runtimeRelease = releaseMetadataValue(runtimeReleaseInput)
  const embeddedReleaseId = releaseMetadataValue(embeddedReleaseInput)
  const runtimeReleaseOutput = runtimeReleaseInput
    ? runtimeRelease || 'other_or_redacted'
    : null
  const embeddedReleaseOutput = embeddedReleaseInput
    ? embeddedReleaseId || 'other_or_redacted'
    : null
  const releaseAlignment = !runtimeRelease || !embeddedReleaseId
    ? 'unverified'
    : runtimeRelease === embeddedReleaseId ? 'aligned' : 'drift'
  const temporalAlignment = generatedAtMs >= endMs ? 'aligned' : 'drift'
  const evidenceAlignmentEligible = snapshotAlignment === 'aligned'
    && releaseAlignment === 'aligned'
    && temporalAlignment === 'aligned'
  const candidateAttributionEnabled = candidateAttributionRequested && evidenceAlignmentEligible
  const dimensions = Object.fromEntries(['product', 'version', 'channel', 'terminal', 'intent', 'mode']
    .map(key => [key, new Map()]))
  const trafficKinds = Object.fromEntries(['natural', 'probe', 'diagnostic', 'synthetic', 'unknown']
    .map(key => [key, emptyMetric()]))
  const target = {
    directSignal: emptyMetric(),
    trustedCandidate: emptyMetric(),
    naturalAttributed: emptyMetric(),
    serviceClosed: emptyMetric(),
    authoritativeProductCredit: emptyMetric(),
    distinctBindingCount: 0,
    naturalAttributedDistinctBindingCount: 0,
    serviceClosedDistinctBindingCount: 0,
    modes: Object.fromEntries(['whoami', 'scene_pack', 'consume', 'first_value', 'continued_use', 'other']
      .map(key => [key, emptyMetric()])),
    toolStages: Object.fromEntries(['whoami', 'scene_pack', 'consume', 'other']
      .map(key => [key, emptyMetric()])),
    outcomes: Object.fromEntries(['first_value', 'continued_use', 'other']
      .map(key => [key, emptyMetric()]))
  }
  const attributionDebt = Object.fromEntries([
    'untrustedDirectIdentity',
    'versionMissing',
    'versionMismatch',
    'missingProductIdentity',
    'missingTrafficAuthority',
    'missingBinding',
    'missingHostReceipt',
    'candidateHeldByGate',
    'candidateHeldByEvidenceAlignment',
    'incompleteSameBindingChain'
  ].map(key => [key, emptyMetric()]))
  const confusion = { privateBoardText: emptyMetric() }
  const totals = emptyMetric()
  const targetBindings = new Set()
  const bindingGroups = new Map()
  const analyzedRows = []
  let latestObservedMs = null
  let invalidTimestampRowCount = 0
  let outsideWindowRowCount = 0
  let invalidSampleCountRowCount = 0

  for (const row of sourceRows) {
    const observedMs = timestampOf(row)
    if (observedMs === null) {
      invalidTimestampRowCount += 1
      continue
    }
    if (observedMs < startMs || observedMs >= endMs) {
      outsideWindowRowCount += 1
      continue
    }

    const boundedSample = boundedSampleCount(row)
    const sampleCount = boundedSample.value
    if (!boundedSample.valid) invalidSampleCountRowCount += 1
    const identity = classifyIndependentBoardIdentity(row)
    const trafficKind = classifyTrafficKind(row)
    const toolStage = toolStageOf(row)
    const outcomeMode = outcomeModeOf(row)
    const mode = modeOf(row)
    const version = versionDimension(identity)
    const channelKey = categoricalValue(row, ['channel', 'channelTrack', 'requestSource'], 'channel')
    const terminalKey = categoricalValue(row, ['terminal', 'clientFamily', 'hostType'], 'terminal')
    const intentKey = categoricalValue(row, ['intentFamily', 'intentClass'], 'intent')
    const hostKey = hostValue(row)
    const clientVersionKey = clientVersionValue(row)
    const binding = identity.stableBindingKey
    const analyzed = {
      row,
      observedMs,
      sampleCount,
      identity,
      trafficKind,
      mode,
      toolStage,
      outcomeMode,
      sampleCountValid: boundedSample.valid,
      channelKey,
      terminalKey,
      intentKey,
      hostKey,
      clientVersionKey,
      binding
    }
    analyzedRows.push(analyzed)
    if (latestObservedMs === null || observedMs > latestObservedMs) latestObservedMs = observedMs
    if (binding) {
      const group = bindingGroups.get(binding) || []
      group.push(analyzed)
      bindingGroups.set(binding, group)
    }
    addMetric(totals, sampleCount)
    addMetric(trafficKinds[trafficKind], sampleCount)

    addCounter(dimensions.product, productDimension(identity, candidateAttributionEnabled), sampleCount)
    addCounter(dimensions.version, version, sampleCount)
    addCounter(dimensions.channel, channelKey, sampleCount)
    addCounter(dimensions.terminal, terminalKey, sampleCount)
    addCounter(dimensions.intent, intentKey, sampleCount)
    addCounter(dimensions.mode, mode, sampleCount)

    if (identity.directSignal) {
      addMetric(target.directSignal, sampleCount)
      addMetric(target.modes[mode], sampleCount)
      addMetric(target.toolStages[toolStage], sampleCount)
      addMetric(target.outcomes[outcomeMode], sampleCount)
      if (binding) targetBindings.add(binding)
      if (!identity.trustedHostReceipt) addMetric(attributionDebt.missingHostReceipt, sampleCount)
    }
    if (identity.creditEligible) addMetric(target.trustedCandidate, sampleCount)

    if (identity.directSignal && !identity.creditEligible) {
      addMetric(attributionDebt.untrustedDirectIdentity, sampleCount)
    }
    if (identity.directSignal && identity.state === 'direct_identity_version_missing') {
      addMetric(attributionDebt.versionMissing, sampleCount)
    }
    if (identity.state === 'direct_identity_version_mismatch') {
      addMetric(attributionDebt.versionMismatch, sampleCount)
    }
    if (!identity.directSignal && identity.state === 'general_reference') {
      addMetric(attributionDebt.missingProductIdentity, sampleCount)
    }
    if (!text(row.trafficClassificationAuthority)) {
      addMetric(attributionDebt.missingTrafficAuthority, sampleCount)
    }
    if (!binding) addMetric(attributionDebt.missingBinding, sampleCount)
    if (identity.privateBoardText) addMetric(confusion.privateBoardText, sampleCount)
  }

  const naturalAttributedBindings = new Set()
  const serviceClosedBindings = new Set()
  for (const binding of targetBindings) {
    const group = bindingGroups.get(binding) || []
    const chain = completeOrderedTargetChain(group)
    if (!chain) {
      const representative = [...group].sort((left, right) => right.observedMs - left.observedMs)[0]
      addMetric(attributionDebt.incompleteSameBindingChain, representative?.sampleCount || 1)
      continue
    }
    if (!candidateAttributionRequested) {
      addMetric(attributionDebt.candidateHeldByGate, chain.consume.sampleCount)
      continue
    }
    if (!evidenceAlignmentEligible) {
      addMetric(attributionDebt.candidateHeldByEvidenceAlignment, chain.consume.sampleCount)
      continue
    }
    addMetric(target.naturalAttributed, chain.consume.sampleCount)
    naturalAttributedBindings.add(binding)
    const closure = chain.relevant.find(entry => (
      entry.observedMs > chain.consume.observedMs
      && trustedServiceClosureReceipt(entry.row)
      && Object.entries(chain.route).every(([key, value]) => entry[key] === value)
    ))
    if (closure) {
      addMetric(target.serviceClosed, closure.sampleCount)
      serviceClosedBindings.add(binding)
    }
  }

  target.distinctBindingCount = targetBindings.size
  target.naturalAttributedDistinctBindingCount = naturalAttributedBindings.size
  target.serviceClosedDistinctBindingCount = serviceClosedBindings.size
  return {
    schema: 'fbsir.independent-board.traffic-attribution/v1',
    generatedAt: new Date(generatedAtMs).toISOString(),
    asOf: latestObservedMs === null ? null : new Date(latestObservedMs).toISOString(),
    reportMode: 'read_only_candidate',
    authorityBoundary: {
      mode: 'unsigned_input_report_only',
      authoritativeProductCredit: false,
      rule: 'Only a separately verified service-side signed receipt may promote a candidate to product credit.'
    },
    productContract: {
      productId: TARGET_PRODUCT_ID,
      listedVersion: TARGET_LISTED_VERSION,
      writePolicy: 'frozen_package_no_writeback',
      hostReceiptContract: HOST_RECEIPT_CONTRACT,
      serviceClosureContract: SERVICE_CLOSURE_CONTRACT
    },
    window: {
      start: new Date(startMs).toISOString(),
      end: new Date(endMs).toISOString(),
      interval: '[start,end)'
    },
    candidateAttributionGate: {
      requested: candidateAttributionRequested,
      enabled: candidateAttributionEnabled,
      effective: candidateAttributionEnabled,
      evidenceAlignmentEligible,
      defaultEnabled: false,
      activation: 'explicit_report_option_plus_aligned_snapshot_release_and_time'
    },
    inputSnapshot: {
      algorithm: 'sha256',
      digest,
      expectedDigest: expectedSnapshotDigest || null,
      alignment: snapshotAlignment,
      drift: snapshotAlignment === 'drift',
      inputRowCount: sourceRows.length,
      windowRowCount: analyzedRows.length,
      source: sourceMetadataValue(options.source),
      serviceRelease: runtimeReleaseOutput
    },
    releaseIdentity: {
      runtimeRelease: runtimeReleaseOutput,
      embeddedReleaseId: embeddedReleaseOutput,
      alignment: releaseAlignment,
      drift: releaseAlignment === 'drift'
    },
    temporalAlignment: {
      status: temporalAlignment,
      generatedAtNotBeforeWindowEnd: generatedAtMs >= endMs,
      reference: 'window.end'
    },
    totals,
    trafficKinds,
    target,
    confusion,
    attributionDebt,
    dimensions: Object.fromEntries(Object.entries(dimensions)
      .map(([key, counter]) => [key, counterValues(counter)])),
    inputQuality: {
      invalidTimestampRowCount,
      outsideWindowRowCount,
      invalidSampleCountRowCount,
      maxSampleCountPerRow: MAX_SAMPLE_COUNT_PER_ROW
    }
  }
}

export const INDEPENDENT_BOARD_TRAFFIC_CONTRACT = Object.freeze({
  productId: TARGET_PRODUCT_ID,
  listedVersion: TARGET_LISTED_VERSION,
  interval: '[start,end)',
  safeDefault: 'unsigned_input_report_only_and_candidate_attribution_disabled'
})

export function extractTrafficRows(input) {
  if (Array.isArray(input)) return input
  const candidates = [
    input?.naturalEvidenceLedger?.cohortHints,
    input?.payload?.naturalEvidenceLedger?.cohortHints,
    input?.data?.naturalEvidenceLedger?.cohortHints,
    input?.ledger?.cohortHints,
    input?.cohortHints,
    input?.rows,
    input?.items
  ]
  return candidates.find(Array.isArray) || []
}

export function parseTrafficAttributionArgs(argv = []) {
  const args = {
    input: null,
    start: null,
    end: null,
    pretty: false,
    enableCandidateAttribution: false
  }
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    const next = () => {
      const value = argv[++index]
      if (value === undefined) throw new TypeError(`Missing value for ${token}`)
      return value
    }
    if (token === '--input') args.input = next()
    else if (token === '--start') args.start = next()
    else if (token === '--end') args.end = next()
    else if (token === '--pretty') args.pretty = true
    else if (token === '--enable-candidate-attribution') args.enableCandidateAttribution = true
    else if (token === '--generated-at') args.generatedAt = next()
    else if (token === '--service-release') args.serviceRelease = next()
    else if (token === '--embedded-release-id') args.embeddedReleaseId = next()
    else if (token === '--source') args.source = next()
    else if (token === '--expected-snapshot-digest') args.expectedSnapshotDigest = next()
    else throw new TypeError(`Unknown argument: ${token}`)
  }
  if (!args.input || !args.start || !args.end) {
    throw new TypeError('--input, --start and --end are required')
  }
  normalizedWindow(args)
  return args
}

async function readStandardInput() {
  process.stdin.setEncoding('utf8')
  let source = ''
  let bytes = 0
  for await (const chunk of process.stdin) {
    bytes += Buffer.byteLength(chunk, 'utf8')
    if (bytes > MAX_INPUT_BYTES) throw new RangeError('Input exceeds the 64 MiB safety limit')
    source += chunk
  }
  return source
}

async function readInput(input) {
  if (input === '-') return readStandardInput()
  const metadata = await stat(input)
  if (!metadata.isFile()) throw new TypeError('Input must be a regular file or - for stdin')
  if (metadata.size > MAX_INPUT_BYTES) throw new RangeError('Input exceeds the 64 MiB safety limit')
  return readFile(input, 'utf8')
}

async function main() {
  const args = parseTrafficAttributionArgs(process.argv.slice(2))
  const source = await readInput(args.input)
  const rows = extractTrafficRows(JSON.parse(source))
  const report = analyzeIndependentBoardTraffic(rows, args)
  process.stdout.write(`${JSON.stringify(report, null, args.pretty ? 2 : 0)}\n`)
}

const isDirectRun = Boolean(process.argv[1])
  && pathToFileURL(process.argv[1]).href === import.meta.url

if (isDirectRun) {
  main().catch(error => {
    console.error(error instanceof Error ? error.message : error)
    process.exitCode = 1
  })
}
