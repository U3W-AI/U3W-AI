import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import {
  analyzeIndependentBoardTraffic,
  classifyIndependentBoardIdentity,
  classifyTrafficKind,
  extractTrafficRows,
  parseTrafficAttributionArgs
} from './independent-board-traffic-attribution.mjs'

const WINDOW = {
  start: '2026-07-20T14:44:00.000Z',
  end: '2026-07-21T14:44:00.000Z'
}

function bindingId(label) {
  return createHash('sha256').update(`independent-board-test:${label}`).digest('hex').slice(0, 16)
}

function trustedTargetRow(overrides = {}) {
  return {
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'server_verified',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true,
    hostForwardingReceiptStatus: 'verified',
    hostForwardingEvidenceTrust: 'server_verified',
    serverVerificationState: 'server_verified',
    serverVerificationSource: 'fbss_host_forwarding_receipt_v1',
    hostForwardingAckChallengeJoinState: 'same_binding_joined',
    hostForwardingAckChallengeRepositoryDurable: true,
    hostForwardingAckChallengeRepositoryProductionReady: true,
    channel: 'workbuddy_cn',
    terminal: 'desktop',
    hostType: 'workbuddy',
    hostPatchVersion: '5.2.6',
    ...overrides
  }
}

function alignedCandidateOptions(rows, overrides = {}) {
  const generatedAt = overrides.generatedAt || '2026-07-21T15:00:00.000Z'
  const serviceRelease = overrides.serviceRelease || 'fixture-release'
  const baseline = analyzeIndependentBoardTraffic(rows, { ...WINDOW, generatedAt })
  return {
    ...WINDOW,
    generatedAt,
    serviceRelease,
    embeddedReleaseId: serviceRelease,
    expectedSnapshotDigest: baseline.inputSnapshot.digest,
    enableCandidateAttribution: true,
    ...overrides
  }
}

function analyzeAlignedCandidate(rows, overrides = {}) {
  return analyzeIndependentBoardTraffic(rows, alignedCandidateOptions(rows, overrides))
}

test('uses a left-closed right-open fixed window', () => {
  const rows = [
    { observedAt: WINDOW.start, sampleCount: 2 },
    { observedAt: '2026-07-21T14:43:59.999Z', sampleCount: 3 },
    { observedAt: WINDOW.end, sampleCount: 5 }
  ]

  const report = analyzeIndependentBoardTraffic(rows, WINDOW)

  assert.equal(report.totals.rowCount, 2)
  assert.equal(report.totals.sampleCount, 5)
  assert.equal(report.window.interval, '[start,end)')
})

test('forms only a report-only candidate from an ordered trusted same-binding target chain', () => {
  const trusted = {
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'server_verified',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true,
    hostForwardingReceiptStatus: 'verified',
    hostForwardingEvidenceTrust: 'server_verified',
    serverVerificationState: 'server_verified',
    serverVerificationSource: 'fbss_host_forwarding_receipt_v1',
    hostForwardingAckChallengeJoinState: 'same_binding_joined',
    hostForwardingAckChallengeRepositoryDurable: true,
    hostForwardingAckChallengeRepositoryProductionReady: true,
    sameBindingClosable: true,
    chainFingerprint: bindingId('binding-hmac-1'),
    channel: 'workbuddy_cn',
    terminal: 'desktop',
    intentFamily: 'independent_board_review',
    hostType: 'workbuddy',
    hostPatchVersion: '5.2.6'
  }
  const rows = [
    { ...trusted, observedAt: '2026-07-21T10:00:00.000Z', toolName: 'skill_whoami' },
    { ...trusted, observedAt: '2026-07-21T10:01:00.000Z', toolName: 'fbs_scene_pack_query' },
    {
      ...trusted,
      observedAt: '2026-07-21T10:02:00.000Z',
      toolName: 'skill_consume'
    },
    {
      ...trusted,
      observedAt: '2026-07-21T10:03:00.000Z',
      eventType: 'continued_use_completed',
      serviceClosureReceiptVerified: true,
      serviceClosureVerificationState: 'server_verified',
      serviceClosureVerificationSource: 'fbss_service_closure_receipt_v1'
    }
  ]

  const report = analyzeAlignedCandidate(rows, {
    generatedAt: '2026-07-21T15:00:00.000Z',
    serviceRelease: 'fixture-release'
  })

  assert.equal(report.target.directSignal.sampleCount, 4)
  assert.equal(report.target.trustedCandidate.sampleCount, 4)
  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.serviceClosed.sampleCount, 1)
  assert.equal(report.target.distinctBindingCount, 1)
  assert.equal(report.target.naturalAttributedDistinctBindingCount, 1)
  assert.equal(report.target.authoritativeProductCredit.sampleCount, 0)
  assert.equal(report.authorityBoundary.mode, 'unsigned_input_report_only')
  assert.equal(report.generatedAt, '2026-07-21T15:00:00.000Z')
  assert.equal(report.asOf, '2026-07-21T10:03:00.000Z')
  assert.match(report.inputSnapshot.digest, /^[a-f0-9]{64}$/)
  assert.equal(report.inputSnapshot.serviceRelease, 'fixture-release')
  assert.deepEqual(report.dimensions.product, [
    { key: 'fbsir-eight-seat-board', rowCount: 4, sampleCount: 4 }
  ])
})

test('never treats a single sameBindingClosable row as a same-binding chain', () => {
  const rows = [{
    observedAt: '2026-07-21T10:00:00.000Z',
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'server_verified',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true,
    hostForwardingReceiptStatus: 'verified',
    hostForwardingEvidenceTrust: 'server_verified',
    serverVerificationState: 'server_verified',
    serverVerificationSource: 'fbss_host_forwarding_receipt_v1',
    hostForwardingAckChallengeJoinState: 'same_binding_joined',
    hostForwardingAckChallengeRepositoryDurable: true,
    hostForwardingAckChallengeRepositoryProductionReady: true,
    sameBindingClosable: true,
    chainFingerprint: bindingId('binding-hmac-single'),
    eventType: 'continued_use_completed'
  }]
  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.trustedCandidate.sampleCount, 1)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.incompleteSameBindingChain.sampleCount, 1)
})

test('joins the same canonical binding value across different source fields', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('shared-binding') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', serverBindingId: bindingId('shared-binding') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', anonymousUserCodeHash: bindingId('shared-binding') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.naturalAttributedDistinctBindingCount, 1)
})

test('never treats boolean-like continuity rescue state as a stable binding identifier', () => {
  for (const [field, invalidBinding] of [
    ...[false, true, 'false', 'true', 123].map(value => ['continuityRescuedFromServerBindingId', value]),
    ...[false, true, 'false', 'true', 123].map(value => ['chainFingerprint', value])
  ]) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', [field]: invalidBinding }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', [field]: invalidBinding }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', [field]: invalidBinding })
    ]

    const report = analyzeAlignedCandidate(rows)

    assert.equal(report.target.distinctBindingCount, 0)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
    assert.equal(report.attributionDebt.missingBinding.sampleCount, 3)
  }
})

test('uses the consume tool stage when the same row records a first-value outcome', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('consume-outcome-binding') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('consume-outcome-binding') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:02:00Z',
      toolName: 'skill_consume',
      eventType: 'first_value_completed',
      chainFingerprint: bindingId('consume-outcome-binding')
    })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.modes.first_value.sampleCount, 1)
})

test('blocks a binding that contains any off-target identity or private-board confusion event', () => {
  for (const conflictRow of [
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      productId: 'fbs-bookwriter'
    }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      prompt: 'private_board'
    }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      prompt: 'private  board'
    }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      productSignatureMatchedProductIds: ['fbsir-eight-seat-board', 'fbs-bookwriter']
    }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      serviceProductId: 'fbs-bookwriter'
    }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:30Z',
      toolName: 'heartbeat',
      chainFingerprint: bindingId('whole-binding-conflict'),
      productSignatureMatchedProductIds: {
        primary: 'fbsir-eight-seat-board',
        secondary: 'fbs-bookwriter'
      }
    })
  ]) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('whole-binding-conflict') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('whole-binding-conflict') }),
      conflictRow,
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('whole-binding-conflict') })
    ]

    const report = analyzeAlignedCandidate(rows)

    assert.equal(report.target.naturalAttributed.sampleCount, 0)
    assert.equal(report.attributionDebt.incompleteSameBindingChain.sampleCount, 1)
  }
})

test('requires exact successful stage names and rejects negated or failed lookalikes', () => {
  for (const rows of [
    [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'not_whoami', chainFingerprint: bindingId('lookalike-stage') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'scene_pack_failed', chainFingerprint: bindingId('lookalike-stage') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'never_consume', chainFingerprint: bindingId('lookalike-stage') })
    ],
    [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', eventType: 'whoami_failed', chainFingerprint: bindingId('failed-stage') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', eventType: 'scene_pack_failed', chainFingerprint: bindingId('failed-stage') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', eventType: 'consume_failed', chainFingerprint: bindingId('failed-stage') })
    ]
  ]) {
    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
  }
})

test('rejects an exact consume tool carrying an explicit failure result', () => {
  for (const failure of [
    { success: false },
    { resultStatus: 'failed' },
    { eventType: 'consume_failed' },
    { errorCode: 'UPSTREAM_FAILURE' }
  ]) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('explicit-failure') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('explicit-failure') }),
      trustedTargetRow({
        observedAt: '2026-07-21T10:02:00Z',
        toolName: 'skill_consume',
        chainFingerprint: bindingId('explicit-failure'),
        ...failure
      })
    ]

    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
  }
})

test('rejects cross-channel or cross-terminal stage stitching', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('dimension-drift') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:00Z',
      toolName: 'fbs_scene_pack_query',
      chainFingerprint: bindingId('dimension-drift'),
      channel: 'workbuddy_intl',
      terminal: 'mobile'
    }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('dimension-drift') })
  ]

  const report = analyzeAlignedCandidate(rows)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('rejects cross-host or cross-client-version stage stitching', () => {
  for (const sceneOverrides of [
    { hostType: 'codebuddy' },
    { hostPatchVersion: '4.10.4' }
  ]) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('host-version-drift') }),
      trustedTargetRow({
        observedAt: '2026-07-21T10:01:00Z',
        toolName: 'fbs_scene_pack_query',
        chainFingerprint: bindingId('host-version-drift'),
        ...sceneOverrides
      }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('host-version-drift') })
    ]

    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
  }
})

test('rejects conflicting client-version aliases within one stage', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('version-alias-conflict') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:01:00Z',
      toolName: 'fbs_scene_pack_query',
      chainFingerprint: bindingId('version-alias-conflict'),
      hostPatchVersion: '5.2.6',
      clientVersion: '4.10.4'
    }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('version-alias-conflict') })
  ]

  const report = analyzeAlignedCandidate(rows)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('rejects a chain whose stage sample count had to be repaired', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('invalid-weight') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('invalid-weight'), sampleCount: '1e308' }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('invalid-weight') })
  ]

  const report = analyzeAlignedCandidate(rows)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.inputQuality.invalidSampleCountRowCount, 1)
})

test('rejects an out-of-order whoami scene-pack consume sequence', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('out-of-order') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('out-of-order') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:03:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('out-of-order') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('rejects a sequence split across different bindings', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('binding-a') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('binding-b') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('binding-c') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('rejects a chain containing any mixed product identity', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('mixed-product') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('mixed-product'), expertEntryId: 'fbs-bookwriter' }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('mixed-product') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('rejects a target chain containing private-board confusion text', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('confusion-chain') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('confusion-chain'), prompt: '私董会' }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('confusion-chain') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.confusion.privateBoardText.sampleCount, 1)
})

test('rejects natural authority names that merely look server-verified', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('fake-authority'), trafficClassificationAuthority: 'server_attacker_verified' }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('fake-authority'), trafficClassificationAuthority: 'server_attacker_verified' }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('fake-authority'), trafficClassificationAuthority: 'server_attacker_verified' })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.trafficKinds.unknown.sampleCount, 3)
})

test('requires strictly increasing stage timestamps when no monotonic sequence is present', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('same-millisecond') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('same-millisecond') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('same-millisecond') })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})

test('requires a service-closure receipt event after the consume stage', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('closure-order') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('closure-order') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:02:00Z',
      toolName: 'skill_consume',
      chainFingerprint: bindingId('closure-order'),
      serviceClosureReceiptVerified: true,
      serviceClosureVerificationState: 'server_verified',
      serviceClosureVerificationSource: 'fbss_service_closure_receipt_v1'
    })
  ]

  const report = analyzeAlignedCandidate(rows)

  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.serviceClosed.sampleCount, 0)
})

test('does not accept receipt booleans encoded as permissive strings', () => {
  for (const stringBoolean of ['yes', '1', 'true']) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('string-receipt'), serverVerifiedHostForwardingAck: stringBoolean }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('string-receipt'), serverVerifiedHostForwardingAck: stringBoolean }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('string-receipt'), serverVerifiedHostForwardingAck: stringBoolean })
    ]

    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.trustedCandidate.sampleCount, 0)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
  }
})

test('does not treat closure fields attached to a later whoami as a service closure event', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('wrong-closure-event') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('wrong-closure-event') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('wrong-closure-event') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:03:00Z',
      toolName: 'skill_whoami',
      chainFingerprint: bindingId('wrong-closure-event'),
      serviceClosureReceiptVerified: true,
      serviceClosureVerificationState: 'server_verified',
      serviceClosureVerificationSource: 'fbss_service_closure_receipt_v1'
    })
  ]

  const report = analyzeAlignedCandidate(rows)
  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.serviceClosed.sampleCount, 0)
})

test('requires the service closure event to preserve the attributed host route', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('closure-route') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('closure-route') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('closure-route') }),
    trustedTargetRow({
      observedAt: '2026-07-21T10:03:00Z',
      eventType: 'continued_use_completed',
      chainFingerprint: bindingId('closure-route'),
      channel: 'workbuddy_intl',
      terminal: 'mobile',
      serviceClosureReceiptVerified: true,
      serviceClosureVerificationState: 'server_verified',
      serviceClosureVerificationSource: 'fbss_service_closure_receipt_v1'
    })
  ]

  const report = analyzeAlignedCandidate(rows)
  assert.equal(report.target.naturalAttributed.sampleCount, 1)
  assert.equal(report.target.serviceClosed.sampleCount, 0)
})

test('rejects an explicitly failed or version-conflicted service closure event', () => {
  for (const closureConflict of [
    { success: false },
    { errorCode: 'CLOSURE_FAILURE' },
    { clientVersion: '4.10.4' }
  ]) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('closure-failure') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('closure-failure') }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('closure-failure') }),
      trustedTargetRow({
        observedAt: '2026-07-21T10:03:00Z',
        eventType: 'continued_use_completed',
        chainFingerprint: bindingId('closure-failure'),
        serviceClosureReceiptVerified: true,
        serviceClosureVerificationState: 'server_verified',
        serviceClosureVerificationSource: 'fbss_service_closure_receipt_v1',
        ...closureConflict
      })
    ]

    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.naturalAttributed.sampleCount, 1)
    assert.equal(report.target.serviceClosed.sampleCount, 0)
  }
})

test('rejects low-entropy repeated binding identifiers', () => {
  for (const invalidBinding of ['aaaaaaaa', '12345678', 'abcdabce']) {
    const rows = [
      trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: invalidBinding }),
      trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: invalidBinding }),
      trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: invalidBinding })
    ]

    const report = analyzeAlignedCandidate(rows)
    assert.equal(report.target.distinctBindingCount, 0)
    assert.equal(report.target.naturalAttributed.sampleCount, 0)
  }
})

test('quarantines a client-declared natural target without trusted host receipt', () => {
  const row = {
    observedAt: '2026-07-21T10:00:00.000Z',
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'untrusted_client_claim_quarantined',
    sameBindingClosable: true,
    bindingKey: 'binding-hmac-2'
  }

  const report = analyzeIndependentBoardTraffic([row], WINDOW)

  assert.equal(report.trafficKinds.unknown.sampleCount, 1)
  assert.equal(report.target.directSignal.sampleCount, 1)
  assert.equal(report.target.trustedCandidate.sampleCount, 0)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.untrustedDirectIdentity.sampleCount, 1)
})

test('holds an otherwise complete target chain behind the default-off candidate gate', () => {
  const trusted = {
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'server_verified',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true,
    hostForwardingReceiptStatus: 'verified',
    hostForwardingEvidenceTrust: 'server_verified',
    serverVerificationState: 'server_verified',
    serverVerificationSource: 'fbss_host_forwarding_receipt_v1',
    hostForwardingAckChallengeJoinState: 'same_binding_joined',
    hostForwardingAckChallengeRepositoryDurable: true,
    hostForwardingAckChallengeRepositoryProductionReady: true,
    sameBindingClosable: true,
    chainFingerprint: bindingId('binding-hmac-default-off'),
    channel: 'workbuddy_cn',
    terminal: 'desktop',
    hostType: 'workbuddy',
    hostPatchVersion: '5.2.6'
  }
  const rows = [
    { ...trusted, observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami' },
    { ...trusted, observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query' },
    { ...trusted, observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume' }
  ]

  const report = analyzeIndependentBoardTraffic(rows, WINDOW)

  assert.equal(report.candidateAttributionGate.enabled, false)
  assert.equal(report.target.trustedCandidate.sampleCount, 3)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.candidateHeldByGate.sampleCount, 1)
})

test('never promotes board-secretary, super-partner, connector or text-only private-board proxies', () => {
  const rows = [
    { observedAt: '2026-07-21T10:00:00Z', productId: 'fbsir-board-secretary-assistant' },
    { observedAt: '2026-07-21T10:01:00Z', expertEntryId: 'fbsir-super-partner-group' },
    { observedAt: '2026-07-21T10:02:00Z', productId: 'fbs-connector' },
    { observedAt: '2026-07-21T10:03:00Z', prompt: 'private board / 私董会' }
  ]

  const report = analyzeIndependentBoardTraffic(rows, WINDOW)

  assert.equal(report.target.directSignal.sampleCount, 0)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.confusion.privateBoardText.sampleCount, 1)
  assert.deepEqual(report.dimensions.product.map(item => item.key), [
    'connector_proxy',
    'off_target_board_secretary',
    'off_target_super_partner',
    'private_board_confusion'
  ])
})

test('requires the frozen listed version and rejects conflicting exact identities', () => {
  const wrongVersion = classifyIndependentBoardIdentity({
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.21',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true
  })
  const conflict = classifyIndependentBoardIdentity({
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    expertEntryId: 'fbs-bookwriter',
    serverVerifiedHostForwardingAck: true,
    hostForwardingReceiptObserved: true,
    hostForwardingReceiptCreditEligible: true
  })

  assert.equal(wrongVersion.state, 'direct_identity_version_mismatch')
  assert.equal(wrongVersion.creditEligible, false)
  assert.equal(conflict.state, 'conflicted_identity')
  assert.equal(conflict.creditEligible, false)
})

test('rejects mixed authoritative package versions and canonicalizes binding without field prefixes', () => {
  const identity = classifyIndependentBoardIdentity({
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    expertVersion: '26.7.21',
    versionKey: '26.7.20',
    chainFingerprint: bindingId('canonical-binding'),
    serverBindingId: bindingId('lower-priority-binding')
  })
  const fallbackIdentity = classifyIndependentBoardIdentity({
    productId: 'fbsir-eight-seat-board',
    packageVersion: '26.7.20',
    serverBindingId: bindingId('canonical-binding')
  })

  assert.equal(identity.state, 'direct_identity_version_mismatch')
  assert.equal(identity.stableBindingKey, bindingId('canonical-binding'))
  assert.equal(fallbackIdentity.stableBindingKey, bindingId('canonical-binding'))
})

test('separates probes and synthetic rows from server-authoritative natural rows', () => {
  assert.equal(classifyTrafficKind({ requestSource: 'fbs-live-monitor' }), 'probe')
  assert.equal(classifyTrafficKind({ testRunId: 'codex-synthetic-1' }), 'synthetic')
  assert.equal(classifyTrafficKind({
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'server_runtime_verified'
  }), 'natural')
  assert.equal(classifyTrafficKind({
    trafficGroup: 'natural',
    trafficClassificationAuthority: 'client_declared'
  }), 'unknown')
})

test('does not echo unbounded free text into categorical dimensions', () => {
  const report = analyzeIndependentBoardTraffic([{
    observedAt: '2026-07-21T10:00:00Z',
    intentFamily: 'alice@example.com',
    channel: 'workbuddy_cn',
    terminal: '13800138000',
    packageVersion: 'person@example.com'
  }], WINDOW)

  assert.deepEqual(report.dimensions.intent, [
    { key: 'invalid_or_unbounded', rowCount: 1, sampleCount: 1 }
  ])
  assert.deepEqual(report.dimensions.terminal, [
    { key: 'invalid_or_unbounded', rowCount: 1, sampleCount: 1 }
  ])
  assert.deepEqual(report.dimensions.version, [
    { key: 'invalid_or_unbounded', rowCount: 1, sampleCount: 1 }
  ])
})

test('redacts identifier-shaped values that are not in finite dimension contracts', () => {
  const report = analyzeIndependentBoardTraffic([{
    observedAt: '2026-07-21T10:00:00Z',
    intentFamily: 'alice_smith',
    channel: 'customer2026',
    terminal: 'device_alice',
    packageVersion: 'person2026'
  }], WINDOW)

  assert.deepEqual(report.dimensions.intent, [
    { key: 'other_or_redacted', rowCount: 1, sampleCount: 1 }
  ])
  assert.deepEqual(report.dimensions.channel, [
    { key: 'other_or_redacted', rowCount: 1, sampleCount: 1 }
  ])
  assert.deepEqual(report.dimensions.terminal, [
    { key: 'other_or_redacted', rowCount: 1, sampleCount: 1 }
  ])
  assert.deepEqual(report.dimensions.version, [
    { key: 'other_or_redacted', rowCount: 1, sampleCount: 1 }
  ])
})

test('does not echo free-form source or release metadata into the report', () => {
  const report = analyzeIndependentBoardTraffic([{
    observedAt: '2026-07-21T10:00:00Z'
  }], {
    ...WINDOW,
    source: 'alice@example.com',
    serviceRelease: 'alice@example.com',
    embeddedReleaseId: 'alice@example.com'
  })

  assert.equal(report.inputSnapshot.source, 'other_or_redacted')
  assert.equal(report.inputSnapshot.serviceRelease, 'other_or_redacted')
  assert.equal(report.releaseIdentity.runtimeRelease, 'other_or_redacted')
  assert.equal(report.releaseIdentity.embeddedReleaseId, 'other_or_redacted')
  assert.equal(report.releaseIdentity.alignment, 'unverified')
})

test('replaces unsafe or unbounded sample counts with one and records the debt', () => {
  const report = analyzeIndependentBoardTraffic([{
    observedAt: '2026-07-21T10:00:00Z',
    sampleCount: '1e308'
  }], WINDOW)

  assert.equal(report.totals.sampleCount, 1)
  assert.equal(report.inputQuality.invalidSampleCountRowCount, 1)
  assert.equal(JSON.parse(JSON.stringify(report)).totals.sampleCount, 1)
})

test('marks snapshot and release identity drift explicitly', () => {
  const report = analyzeIndependentBoardTraffic([{
    observedAt: '2026-07-21T10:00:00Z'
  }], {
    ...WINDOW,
    expectedSnapshotDigest: '0'.repeat(64),
    serviceRelease: 'runtime-release-a',
    embeddedReleaseId: 'embedded-release-b'
  })

  assert.equal(report.inputSnapshot.drift, true)
  assert.equal(report.inputSnapshot.alignment, 'drift')
  assert.equal(report.releaseIdentity.drift, true)
  assert.equal(report.releaseIdentity.alignment, 'drift')
  assert.equal(report.releaseIdentity.runtimeRelease, 'runtime-release-a')
  assert.equal(report.releaseIdentity.embeddedReleaseId, 'embedded-release-b')
})

test('holds a complete candidate chain when snapshot and release identities drift', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('drift-evidence') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('drift-evidence') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('drift-evidence') })
  ]

  const report = analyzeIndependentBoardTraffic(rows, {
    ...WINDOW,
    enableCandidateAttribution: true,
    generatedAt: '2026-07-21T15:00:00Z',
    expectedSnapshotDigest: '0'.repeat(64),
    serviceRelease: 'runtime-release-a',
    embeddedReleaseId: 'embedded-release-b'
  })

  assert.equal(report.inputSnapshot.alignment, 'drift')
  assert.equal(report.releaseIdentity.alignment, 'drift')
  assert.equal(report.candidateAttributionGate.effective, false)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.candidateHeldByEvidenceAlignment.sampleCount, 1)
})

test('holds a complete candidate chain when snapshot or release identity is unverified', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('unverified-evidence') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('unverified-evidence') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('unverified-evidence') })
  ]

  const report = analyzeIndependentBoardTraffic(rows, {
    ...WINDOW,
    enableCandidateAttribution: true,
    generatedAt: '2026-07-21T15:00:00Z'
  })

  assert.equal(report.inputSnapshot.alignment, 'unverified')
  assert.equal(report.releaseIdentity.alignment, 'unverified')
  assert.equal(report.candidateAttributionGate.effective, false)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.candidateHeldByEvidenceAlignment.sampleCount, 1)
})

test('holds a complete candidate chain when generatedAt is earlier than asOf', () => {
  const rows = [
    trustedTargetRow({ observedAt: '2026-07-21T10:00:00Z', toolName: 'skill_whoami', chainFingerprint: bindingId('temporal-drift') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:01:00Z', toolName: 'fbs_scene_pack_query', chainFingerprint: bindingId('temporal-drift') }),
    trustedTargetRow({ observedAt: '2026-07-21T10:02:00Z', toolName: 'skill_consume', chainFingerprint: bindingId('temporal-drift') })
  ]
  const baseline = analyzeIndependentBoardTraffic(rows, WINDOW)

  const report = analyzeIndependentBoardTraffic(rows, {
    ...WINDOW,
    enableCandidateAttribution: true,
    generatedAt: '2026-07-21T09:59:00Z',
    expectedSnapshotDigest: baseline.inputSnapshot.digest,
    serviceRelease: 'fixture-release',
    embeddedReleaseId: 'fixture-release'
  })

  assert.equal(report.temporalAlignment.status, 'drift')
  assert.equal(report.candidateAttributionGate.effective, false)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
  assert.equal(report.attributionDebt.candidateHeldByEvidenceAlignment.sampleCount, 1)
})

test('extracts the standard API2 natural-evidence ledger without double counting aliases', () => {
  const canonicalRows = [{ observedAt: WINDOW.start, productId: 'canonical' }]
  const rows = extractTrafficRows({
    naturalEvidenceLedger: { cohortHints: canonicalRows },
    rows: [{ observedAt: WINDOW.start, productId: 'alias' }]
  })

  assert.deepEqual(rows, canonicalRows)
})

test('CLI accepts only an explicit input and fixed start/end window', () => {
  assert.deepEqual(parseTrafficAttributionArgs([
    '--input', '-',
    '--start', WINDOW.start,
    '--end', WINDOW.end,
    '--pretty',
    '--enable-candidate-attribution',
    '--generated-at', '2026-07-21T15:00:00Z',
    '--service-release', 'runtime-release',
    '--embedded-release-id', 'embedded-release',
    '--source', 'fixture',
    '--expected-snapshot-digest', 'a'.repeat(64)
  ]), {
    input: '-',
    start: WINDOW.start,
    end: WINDOW.end,
    pretty: true,
    enableCandidateAttribution: true,
    generatedAt: '2026-07-21T15:00:00Z',
    serviceRelease: 'runtime-release',
    embeddedReleaseId: 'embedded-release',
    source: 'fixture',
    expectedSnapshotDigest: 'a'.repeat(64)
  })
  assert.throws(
    () => parseTrafficAttributionArgs(['--window-hours', '24']),
    /Unknown argument/
  )
})

test('CLI analyzes a standard ledger from stdin without creating an input file', () => {
  const script = fileURLToPath(new URL('./independent-board-traffic-attribution.mjs', import.meta.url))
  const input = JSON.stringify({
    naturalEvidenceLedger: {
      cohortHints: [{
        observedAt: '2026-07-21T10:00:00Z',
        productId: 'fbsir-eight-seat-board',
        packageVersion: '26.7.20',
        trafficGroup: 'natural',
        trafficClassificationAuthority: 'server_verified',
        serverVerifiedHostForwardingAck: true,
        hostForwardingReceiptObserved: true,
        hostForwardingReceiptCreditEligible: true,
        sameBindingClosable: true,
        bindingKey: 'fixture-binding'
      }]
    }
  })

  const result = spawnSync(process.execPath, [
    script,
    '--input', '-',
    '--start', WINDOW.start,
    '--end', WINDOW.end,
    '--enable-candidate-attribution'
  ], { input, encoding: 'utf8' })

  assert.equal(result.status, 0, result.stderr)
  const report = JSON.parse(result.stdout)
  assert.equal(report.totals.rowCount, 1)
  assert.equal(report.target.naturalAttributed.sampleCount, 0)
})
