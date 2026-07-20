const OPERATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/
const OUTCOME_STATES = new Set(['DRAFT', 'UNKNOWN', 'NOT_FOUND'])
const ENTITLEMENT_ACTIVATION_STATES = new Set([
  'FREE', 'ACTIVE', 'PENDING_CONNECTOR', 'INVALID_ENTITLEMENT', 'EXPIRED', 'REVOKED'
])
const BOARD_PLAN_CODES = new Set(['BOARD_FREE', 'BOARD_VIP'])
const MEETING_STATUSES = new Set([
  'PENDING', 'RESERVED', 'COMPLETED', 'REJECTED', 'RELEASED', 'FAILED', 'UNKNOWN'
])
const CONTEXT_KEYS = Object.freeze(['tenantId', 'memberId', 'tenantName', 'memberRole'])
const ENTITLEMENT_KEYS = Object.freeze([
  'tenantId', 'memberId', 'userId', 'grantedPlanCode', 'effectivePlanCode',
  'activationState', 'dailyMeetingLimit', 'agendaLimit', 'seatLimit',
  'secretaryEnabled', 'connectorRequired', 'connectorVerified',
  'usedCount', 'reservedCount', 'remainingCount'
])
const RECENT_MEETING_KEYS = Object.freeze([
  'operationId', 'status', 'effectivePlanCode', 'agendaCount',
  'seatCount', 'remainingCount', 'bucketDate', 'createdAt'
])
const DASHBOARD_KEYS = Object.freeze([
  'context', 'entitlement', 'recentMeetings', 'connectorState', 'webhookState', 'watchState'
])

export function parseBoardContexts(value) {
  if (!Array.isArray(value)) throw new Error('企业数据格式不正确，请刷新后重试。')
  const tenantIds = new Set()
  const records = value.map(item => {
    const context = parseBoardContext(item)
    if (tenantIds.has(context.tenantId)) {
      throw new Error('企业数据存在重复项，请联系管理员核验成员配置。')
    }
    tenantIds.add(context.tenantId)
    return context
  })
  return Object.freeze(records)
}

export function parseBoardContext(value) {
  assertExactKeys(value, CONTEXT_KEYS, '企业上下文')
  if (!isPositiveSafeInteger(value.tenantId)
      || !isPositiveSafeInteger(value.memberId)
      || !isNonBlankString(value.tenantName)
      || !isNonBlankString(value.memberRole)) {
    throw new Error('企业数据不完整，请联系管理员核验成员配置。')
  }
  return Object.freeze({
    tenantId: value.tenantId,
    memberId: value.memberId,
    tenantName: value.tenantName.trim(),
    memberRole: value.memberRole.trim()
  })
}

export function parseBoardEntitlementSnapshot(value) {
  assertExactKeys(value, ENTITLEMENT_KEYS, '权益快照')
  assertSafeEntitlementLifecycle(value)
  if (!isPositiveSafeInteger(value.tenantId)
      || !isPositiveSafeInteger(value.memberId)
      || !isPositiveSafeInteger(value.userId)
      || (value.grantedPlanCode !== null && !BOARD_PLAN_CODES.has(value.grantedPlanCode))
      || !BOARD_PLAN_CODES.has(value.effectivePlanCode)
      || !isPositiveSafeInteger(value.dailyMeetingLimit)
      || !isPositiveSafeInteger(value.agendaLimit) || value.agendaLimit > 30
      || !(value.seatLimit === null
        || (isPositiveSafeInteger(value.seatLimit) && value.seatLimit <= 100))
      || typeof value.secretaryEnabled !== 'boolean'
      || typeof value.connectorRequired !== 'boolean'
      || typeof value.connectorVerified !== 'boolean'
      || !isNonNegativeSafeInteger(value.usedCount)
      || !isNonNegativeSafeInteger(value.reservedCount)
      || !isNonNegativeSafeInteger(value.remainingCount)) {
    throw new Error('权益数据不完整或包含非法值。')
  }
  const expectedRemaining = Math.max(0,
    value.dailyMeetingLimit - value.usedCount - value.reservedCount)
  if (value.remainingCount !== expectedRemaining) {
    throw new Error('今日额度数据不一致，页面已停止预约。')
  }
  return Object.freeze({
    tenantId: value.tenantId,
    memberId: value.memberId,
    userId: value.userId,
    grantedPlanCode: value.grantedPlanCode,
    effectivePlanCode: value.effectivePlanCode,
    activationState: value.activationState,
    dailyMeetingLimit: value.dailyMeetingLimit,
    agendaLimit: value.agendaLimit,
    seatLimit: value.seatLimit,
    secretaryEnabled: value.secretaryEnabled,
    connectorRequired: value.connectorRequired,
    connectorVerified: value.connectorVerified,
    usedCount: value.usedCount,
    reservedCount: value.reservedCount,
    remainingCount: value.remainingCount
  })
}

export function parseBoardRecentMeetings(value) {
  if (!Array.isArray(value) || value.length > 10) {
    throw new Error('最近会议数据格式不正确。')
  }
  return Object.freeze(value.map((item, index) => {
    assertExactKeys(item, RECENT_MEETING_KEYS, `第${index + 1}条最近会议`)
    if (!isValidOperationId(item.operationId)
        || !MEETING_STATUSES.has(item.status)
        || !BOARD_PLAN_CODES.has(item.effectivePlanCode)
        || !isPositiveSafeInteger(item.agendaCount) || item.agendaCount > 30
        || !isPositiveSafeInteger(item.seatCount) || item.seatCount > 100
        || !isNonNegativeSafeInteger(item.remainingCount)
        || typeof item.bucketDate !== 'string'
        || !/^\d{4}-\d{2}-\d{2}$/.test(item.bucketDate)
        || !isNonBlankString(item.createdAt)
        || Number.isNaN(new Date(item.createdAt).getTime())) {
      throw new Error('最近会议记录包含非法值。')
    }
    return Object.freeze({
      operationId: item.operationId,
      status: item.status,
      effectivePlanCode: item.effectivePlanCode,
      agendaCount: item.agendaCount,
      seatCount: item.seatCount,
      remainingCount: item.remainingCount,
      bucketDate: item.bucketDate,
      createdAt: item.createdAt
    })
  }))
}

export function parseBoardDashboardEnvelope(value) {
  assertExactKeys(value, DASHBOARD_KEYS, '独董会看板')
  return Object.freeze({
    context: value.context,
    entitlement: value.entitlement,
    recentMeetings: value.recentMeetings,
    connectorState: value.connectorState,
    webhookState: value.webhookState,
    watchState: value.watchState
  })
}

export function assertSafeEntitlementLifecycle(value) {
  if (!isObject(value) || !ENTITLEMENT_ACTIVATION_STATES.has(value.activationState)) {
    throw new Error('权益激活状态不受支持。')
  }
  if (value.activationState === 'REVOKED' && value.effectivePlanCode !== 'BOARD_FREE') {
    throw new Error('已撤销权益未回退免费版，页面已停止预约。')
  }
  return value
}

export function reservationMatchesSubmitted(meeting, submitted) {
  return isObject(meeting)
    && isObject(submitted)
    && meeting.operationId === submitted.operationId
    && meeting.status === 'RESERVED'
    && meeting.agendaCount === submitted.agendaCount
    && meeting.seatCount === submitted.seatCount
    && isNonNegativeSafeInteger(meeting.remainingCount)
}

export function persistDraftWithReadback(storage, key, draft) {
  if (!storage || typeof storage.setItem !== 'function' || typeof storage.getItem !== 'function') {
    throw new Error('DRAFT_STORAGE_UNAVAILABLE')
  }
  if (typeof key !== 'string' || key.length === 0 || !isValidDraft(draft)) {
    throw new Error('DRAFT_STORAGE_INPUT_INVALID')
  }
  const serialized = JSON.stringify(draft)
  storage.setItem(key, serialized)
  const readback = storage.getItem(key)
  if (readback !== serialized) throw new Error('DRAFT_STORAGE_READBACK_MISMATCH')
  const parsed = JSON.parse(readback)
  if (!isValidDraft(parsed) || JSON.stringify(parsed) !== serialized) {
    throw new Error('DRAFT_STORAGE_READBACK_INVALID')
  }
  return parsed
}

export function parseStoredDraft(serialized, expectedTenantId, expectedUserId) {
  if (typeof serialized !== 'string' || serialized.length === 0) return null
  try {
    const value = JSON.parse(serialized)
    if (!isValidDraft(value)
        || value.tenantId !== expectedTenantId
        || value.userId !== expectedUserId) return null
    return value
  } catch {
    return null
  }
}

function isValidDraft(value) {
  return isObject(value)
    && isPositiveSafeInteger(value.tenantId)
    && isPositiveSafeInteger(value.userId)
    && typeof value.operationId === 'string'
    && OPERATION_ID_PATTERN.test(value.operationId)
    && isPositiveSafeInteger(value.agendaCount)
    && value.agendaCount <= 30
    && isPositiveSafeInteger(value.seatCount)
    && value.seatCount <= 100
    && OUTCOME_STATES.has(value.outcomeState)
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isNonBlankString(value) {
  return typeof value === 'string' && value.trim().length > 0
}

function isValidOperationId(value) {
  return typeof value === 'string' && OPERATION_ID_PATTERN.test(value)
}

function assertExactKeys(value, expectedKeys, label) {
  if (!isObject(value)) throw new Error(`${label}不是对象。`)
  const actual = Object.keys(value).sort()
  const expected = [...expectedKeys].sort()
  if (actual.length !== expected.length
      || actual.some((key, index) => key !== expected[index])) {
    throw new Error(`${label}字段集合不符合安全合同。`)
  }
}

function isPositiveSafeInteger(value) {
  return Number.isSafeInteger(value) && value > 0
}

function isNonNegativeSafeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0
}
