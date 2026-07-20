import assert from 'node:assert/strict'
import fs from 'node:fs'
import {
  assertSafeEntitlementLifecycle,
  parseBoardContext,
  parseBoardContexts,
  parseBoardDashboardEnvelope,
  parseBoardEntitlementSnapshot,
  parseBoardRecentMeetings,
  parseStoredDraft,
  persistDraftWithReadback,
  reservationMatchesSubmitted
} from '../src/views/business/independentBoard/me/reservationSafety.js'

const safeContext = Object.freeze({
  tenantId: 7,
  memberId: 42,
  tenantName: '福帮手测试企业',
  memberRole: 'MEMBER'
})
const parsedContext = parseBoardContext(safeContext)
assert.deepEqual(parsedContext, safeContext)
assert.notEqual(parsedContext, safeContext, 'context must be rebuilt as a safe DTO')
assert.equal(Object.isFrozen(parsedContext), true)
assert.deepEqual(parseBoardContexts([safeContext]), [safeContext])
assert.throws(() => parseBoardContext({ ...safeContext, internalTenantKey: 'unsafe' }), /安全合同/)
assert.throws(() => parseBoardContexts([safeContext, safeContext]), /重复项/)

const safeRevokedEntitlement = Object.freeze({
  tenantId: 7,
  memberId: 42,
  userId: 99,
  grantedPlanCode: 'BOARD_VIP',
  effectivePlanCode: 'BOARD_FREE',
  activationState: 'REVOKED',
  dailyMeetingLimit: 1,
  agendaLimit: 5,
  seatLimit: 3,
  secretaryEnabled: false,
  connectorRequired: false,
  connectorVerified: false,
  usedCount: 0,
  reservedCount: 0,
  remainingCount: 1
})
const parsedEntitlement = parseBoardEntitlementSnapshot(safeRevokedEntitlement)
assert.deepEqual(parsedEntitlement, safeRevokedEntitlement)
assert.notEqual(parsedEntitlement, safeRevokedEntitlement,
  'entitlement must be rebuilt as a safe DTO')
assert.equal(Object.isFrozen(parsedEntitlement), true)
assert.throws(() => parseBoardEntitlementSnapshot({
  ...safeRevokedEntitlement,
  payloadDigest: 'unsafe'
}), /安全合同/)
assert.throws(() => parseBoardEntitlementSnapshot({
  ...safeRevokedEntitlement,
  effectivePlanCode: 'BOARD_VIP'
}), /未回退免费版/)

const safeRecentMeeting = Object.freeze({
  operationId: 'board-20260720-safe-recent',
  status: 'RESERVED',
  effectivePlanCode: 'BOARD_FREE',
  agendaCount: 3,
  seatCount: 2,
  remainingCount: 0,
  bucketDate: '2026-07-20',
  createdAt: '2026-07-20T10:00:00+08:00'
})
const parsedMeetings = parseBoardRecentMeetings([safeRecentMeeting])
assert.deepEqual(parsedMeetings, [safeRecentMeeting])
assert.notEqual(parsedMeetings[0], safeRecentMeeting,
  'recent meeting must be rebuilt as a safe DTO')
assert.equal(Object.isFrozen(parsedMeetings[0]), true)
assert.throws(() => parseBoardRecentMeetings([{
  ...safeRecentMeeting,
  requestDigest: 'unsafe'
}]), /安全合同/)

const dashboardEnvelope = {
  context: safeContext,
  entitlement: safeRevokedEntitlement,
  recentMeetings: [safeRecentMeeting],
  connectorState: 'NOT_CONNECTED',
  webhookState: 'COMING_SOON',
  watchState: 'COMING_SOON'
}
assert.deepEqual(parseBoardDashboardEnvelope(dashboardEnvelope), dashboardEnvelope)
assert.throws(() => parseBoardDashboardEnvelope({
  ...dashboardEnvelope,
  internalTraceId: 'unsafe'
}), /安全合同/)

assert.equal(assertSafeEntitlementLifecycle({
  activationState: 'REVOKED',
  effectivePlanCode: 'BOARD_FREE'
}).activationState, 'REVOKED')
assert.throws(() => assertSafeEntitlementLifecycle({
  activationState: 'REVOKED',
  effectivePlanCode: 'BOARD_VIP'
}), /未回退免费版/)
assert.throws(() => assertSafeEntitlementLifecycle({
  activationState: 'REMOVED',
  effectivePlanCode: 'BOARD_FREE'
}), /不受支持/)

const submitted = Object.freeze({
  operationId: 'board-20260720-safe-retry',
  agendaCount: 5,
  seatCount: 3
})
const reserved = {
  operationId: submitted.operationId,
  status: 'RESERVED',
  agendaCount: submitted.agendaCount,
  seatCount: submitted.seatCount,
  remainingCount: 2
}

assert.equal(reservationMatchesSubmitted(reserved, submitted), true)
for (const mutation of [
  { operationId: 'board-20260720-other-key' },
  { status: 'PENDING' },
  { status: 'COMPLETED' },
  { agendaCount: 4 },
  { seatCount: 4 },
  { remainingCount: null },
  { remainingCount: -1 }
]) {
  assert.equal(reservationMatchesSubmitted({ ...reserved, ...mutation }, submitted), false)
}

const draft = {
  tenantId: 7,
  userId: 42,
  operationId: submitted.operationId,
  agendaCount: submitted.agendaCount,
  seatCount: submitted.seatCount,
  outcomeState: 'UNKNOWN'
}
const memory = new Map()
const storage = {
  setItem(key, value) { memory.set(key, value) },
  getItem(key) { return memory.get(key) ?? null }
}
assert.deepEqual(persistDraftWithReadback(storage, 'draft-key', draft), draft)
assert.deepEqual(parseStoredDraft(memory.get('draft-key'), 7, 42), draft)
assert.equal(parseStoredDraft(memory.get('draft-key'), 8, 42), null)
assert.equal(parseStoredDraft('{broken', 7, 42), null)

assert.throws(() => persistDraftWithReadback({
  setItem() { throw new Error('denied') },
  getItem() { return null }
}, 'draft-key', draft))
assert.throws(() => persistDraftWithReadback({
  setItem() {},
  getItem() { return '{"different":true}' }
}, 'draft-key', draft), /DRAFT_STORAGE_READBACK_MISMATCH/)

const pageSource = fs.readFileSync(
  new URL('../src/views/business/independentBoard/me/index.vue', import.meta.url),
  'utf8'
)
const storageGateIndex = pageSource.indexOf('if (!persistCurrentReservationDraft())')
const sideEffectIndex = pageSource.indexOf('const response = await reserveIndependentBoardMeeting')
assert.ok(storageGateIndex >= 0 && storageGateIndex < sideEffectIndex)
assert.match(pageSource, /reservationMatchesSubmitted\(exactReadback\.meeting, submittedPayload\)/)
assert.match(pageSource, /reservationMatchesSubmitted\(dashboardReadback, submittedPayload\)/)
assert.match(pageSource, /reservationOutcomeState !== 'DRAFT'/)
assert.match(pageSource, /无法安全保存预约草稿/)
assert.match(pageSource, /state === 'REVOKED'/)
assert.match(pageSource, /权益已撤销/)
assert.match(pageSource, /parseBoardContext\(dashboard\.context\)/)
assert.match(pageSource, /parseBoardEntitlementSnapshot\(dashboard\.entitlement\)/)
assert.match(pageSource, /parseBoardRecentMeetings\(dashboard\.recentMeetings\)/)

console.log('Independent Board UI safety verification passed: reservation and revoked-entitlement gates are green.')
